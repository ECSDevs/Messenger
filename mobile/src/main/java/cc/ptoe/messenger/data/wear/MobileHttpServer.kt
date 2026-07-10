/*
 * Copyright 2026 ECSDevs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.ptoe.messenger.data.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress

/**
 * Foreground service that exposes the Messenger data layer to a paired Wear OS
 * watch over a stock WebSocket transport.
 *
 * This is a Bluetooth-free transport. The previous Bluetooth RFCOMM socket
 * was abandoned because Samsung China-region Galaxy Watches have broken
 * GMS for Wear OS *and* because we kept hitting Samsung's Bluetooth pairing
 * quirks. Wear OS watches tether their network to the phone (Bluetooth PAN),
 * so the watch and phone are always on the same L2 network — we can just run
 * a regular WebSocket server on the phone and let the watch find it via mDNS
 * (Android's NsdManager).
 *
 * Protocol: one JSON object per WebSocket text frame, requestId is always
 * echoed in the response so the client can correlate.
 *
 *   <- {"type":"sync", "requestId":"..."}
 *   -> {"type":"sync_response", "requestId":"...", "agents":[...], ...}
 *   <- {"type":"chat",  "requestId":"...", "conversationId":"...", "text":"..."}
 *   -> {"type":"chat_response", "requestId":"...", "content":"...", ...}
 *   <- {"type":"new_conversation", "requestId":"...", "agentId":"..."}
 *   -> {"type":"new_conversation_response", "requestId":"...", "conversationId":"..."}
 *
 * The chat and new_conversation paths reuse [MobileWearChatHandler] so the
 * business logic stays identical to the previous transport.
 */
class MobileHttpServer : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var wsServer: WebSocketServer? = null
    @Volatile private var nsdManager: NsdManager? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var isRunning = false

    private val app: MessengerApplication
        get() = applicationContext as MessengerApplication

    override fun onCreate() {
        super.onCreate()
        runCatching { startInForeground() }
        acquireMulticastLock()
        startServer()
        registerNsd()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        runCatching { wsServer?.stop(1_000) }
        wsServer = null
        unregisterNsd()
        runCatching { multicastLock?.release() }
        multicastLock = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(cc.ptoe.messenger.R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(cc.ptoe.messenger.R.string.app_name))
            .setContentText("Wear sync running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireMulticastLock() {
        // mDNS uses multicast. Without this lock, doze / power-save often
        // drops the multicast packets and NSD discovery silently fails.
        runCatching {
            val wifi = getSystemService(WifiManager::class.java) ?: return@runCatching
            val lock = wifi.createMulticastLock(MULTICAST_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
        }.onFailure { Log.w(TAG, "Failed to acquire multicast lock", it) }
    }

    private fun startServer() {
        if (isRunning) return
        isRunning = true
        serviceScope.launch {
            val server = object : WebSocketServer(InetSocketAddress(PORT)) {
                override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                    Log.d(TAG, "Client connected: ${conn.remoteSocketAddress}")
                }

                override fun onMessage(conn: WebSocket, message: String) {
                    val request = try {
                        JSONObject(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid message: $message", e)
                        try {
                            conn.send(
                                JSONObject().apply {
                                    put("type", "error")
                                    put("error", "Invalid JSON: ${e.message}")
                                }.toString()
                            )
                        } catch (_: Exception) {}
                        return
                    }
                    serviceScope.launch {
                        val response = try {
                            handleRequest(request)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to handle message: $message", e)
                            JSONObject().apply {
                                put("type", "error")
                                put("error", e.message ?: "Unknown error")
                            }
                        }
                        try {
                            conn.send(response.toString())
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send response", e)
                        }
                    }
                }

                override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                    Log.d(TAG, "Client disconnected: code=$code reason=$reason")
                }

                override fun onError(conn: WebSocket?, ex: Exception) {
                    Log.w(TAG, "Server error", ex)
                }

                override fun onStart() {
                    Log.d(TAG, "WebSocket server listening on $PORT")
                }
            }
            try {
                server.start()
                wsServer = server
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WebSocket server", e)
            }
        }
    }

    private fun registerNsd() {
        val nsd = getSystemService(NsdManager::class.java) ?: run {
            Log.w(TAG, "NsdManager not available")
            return
        }
        nsdManager = nsd
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service registered: ${serviceInfo.serviceName} ${serviceInfo.serviceType}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: error=$errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "NSD service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregistration failed: error=$errorCode")
            }
        }
        registrationListener = listener
        runCatching {
            nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { Log.w(TAG, "NSD registerService threw", it) }
    }

    private fun unregisterNsd() {
        val nsd = nsdManager ?: return
        val listener = registrationListener ?: return
        runCatching { nsd.unregisterService(listener) }
        registrationListener = null
    }

    private suspend fun handleRequest(request: JSONObject): JSONObject {
        val requestId = request.optString("requestId")
        return when (request.optString("type")) {
            "sync" -> {
                val response = handleSyncRequest()
                if (requestId.isNotBlank()) response.put("requestId", requestId)
                response
            }
            "chat" -> {
                val payload = request.toString().encodeToByteArray()
                val responseBytes = MobileWearChatHandler(app).handleChatRequest(payload)
                val responseJson = JSONObject(responseBytes.decodeToString())
                responseJson.put("type", "chat_response")
                responseJson
            }
            "new_conversation" -> {
                val payload = request.toString().encodeToByteArray()
                val responseBytes = MobileWearChatHandler(app).handleNewConversation(payload)
                val responseJson = JSONObject(responseBytes.decodeToString())
                responseJson.put("type", "new_conversation_response")
                responseJson
            }
            else -> JSONObject().apply {
                put("type", "error")
                put("error", "Unknown request type: ${request.optString("type")}")
                if (requestId.isNotBlank()) put("requestId", requestId)
            }
        }
    }

    private suspend fun handleSyncRequest(): JSONObject {
        val agents = app.agentRepository.getAll().first()
            .sortedWith(compareByDescending<Agent> { it.isDefault }.thenBy { it.name.lowercase() })
        val conversations = app.conversationRepository.getAll().first()
            .sortedByDescending { it.updatedAt }
            .take(MAX_CONVERSATIONS)

        val agentsJson = JSONArray().apply {
            agents.forEach { agent ->
                put(JSONObject().apply {
                    put("id", agent.id)
                    put("name", agent.name)
                    put("isDefault", agent.isDefault)
                    put("isReady", isAgentReady(agent))
                })
            }
        }

        val conversationsJson = JSONArray().apply {
            conversations.forEach { conv ->
                put(JSONObject().apply {
                    put("id", conv.id)
                    put("title", conv.title)
                    put("agentId", conv.agentId)
                    put("lastMessage", conv.lastMessage)
                    put("updatedAt", conv.updatedAt)
                    put("createdAt", conv.createdAt)
                })
            }
        }

        val messagesJson = JSONObject()
        conversations.forEach { conv ->
            val messages = app.messageRepository.getByConversationId(conv.id).first()
                .filter { it.role != MessageRole.SYSTEM }
                .takeLast(MAX_MESSAGES_PER_CONVERSATION)
            messagesJson.put(conv.id, JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("id", msg.id)
                        put("conversationId", msg.conversationId)
                        put("role", when (msg.role) {
                            MessageRole.ASSISTANT -> "assistant"
                            else -> "user"
                        })
                        put("content", msg.content)
                        put("timestamp", msg.timestamp)
                        put("isError", msg.status == MessageStatus.ERROR)
                        put("isPending", msg.status == MessageStatus.SENDING)
                    })
                }
            })
        }

        return JSONObject().apply {
            put("type", "sync_response")
            put("agents", agentsJson)
            put("conversations", conversationsJson)
            put("messages", messagesJson)
            put("updatedAt", System.currentTimeMillis())
        }
    }

    private suspend fun isAgentReady(agent: Agent): Boolean {
        val enabledModels = app.modelRepository.getAll().first().filter { it.isEnabled }
        if (enabledModels.isEmpty()) return false
        val model = agent.defaultModelId?.let { modelId ->
            enabledModels.firstOrNull { it.id == modelId }
        } ?: enabledModels.firstOrNull() ?: return false
        return app.providerRepository.getById(model.providerId).first() != null
    }

    companion object {
        private const val TAG = "MobileHttpServer"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wear_http_sync"
        private const val SERVICE_NAME = "MessengerWearSync"
        // NSD service type — DNS-SD. The trailing dot is required.
        private const val SERVICE_TYPE = "_messenger._tcp."
        private const val PORT = 18765
        private const val MULTICAST_LOCK_TAG = "MessengerWearSync"
        private const val MAX_CONVERSATIONS = 30
        private const val MAX_MESSAGES_PER_CONVERSATION = 40
    }
}
