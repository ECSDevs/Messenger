package cc.ptoe.messenger.data.wear

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.ServiceInfo
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Foreground service that exposes the Messenger data layer to a paired Wear OS
 * watch over a standard Bluetooth RFCOMM socket.
 *
 * This transport intentionally does NOT depend on Google Play Services for
 * Wear OS — DataLayer / MessageClient are broken or missing on Samsung
 * China-region Galaxy Watches. RFCOMM is a stock Android API, so it works on
 * any Wear OS device that has Bluetooth.
 *
 * Line-delimited JSON is spoken on top of the socket:
 *   - {"type":"sync"}              -> {"type":"sync_response", ...}
 *   - {"type":"chat",  ...}        -> {"type":"chat_response", ...}
 *   - {"type":"new_conversation", ...} -> {"type":"new_conversation_response", ...}
 *
 * The chat and new-conversation paths reuse [MobileWearChatHandler] so the
 * business logic stays identical to the DataLayer implementation.
 */
class MobileBluetoothServer : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var isRunning = false

    private val app: MessengerApplication
        get() = applicationContext as MessengerApplication

    override fun onCreate() {
        super.onCreate()
        runCatching { startInForeground() }
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(cc.ptoe.messenger.R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(cc.ptoe.messenger.R.string.app_name))
            .setContentText("Wear sync running")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        if (isRunning) return
        isRunning = true
        serviceScope.launch {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                Log.w(TAG, "Bluetooth adapter not available on this device")
                return@launch
            }
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME,
                    WearSyncProtocol.SERVICE_UUID
                )
                Log.d(TAG, "Bluetooth server listening on ${WearSyncProtocol.SERVICE_UUID}")
                while (isRunning) {
                    val clientSocket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        if (isRunning) Log.w(TAG, "Accept failed", e)
                        null
                    } ?: break
                    serviceScope.launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    private suspend fun handleClient(socket: BluetoothSocket) {
        Log.d(TAG, "Client connected: ${socket.remoteDevice.address}")
        try {
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)
            while (isRunning && socket.isConnected) {
                val line = try {
                    input.readLine()
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "Read failed", e)
                    null
                } ?: break
                val request = try {
                    JSONObject(line)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid request: $line", e)
                    continue
                }
                val response = handleRequest(request)
                try {
                    output.writeBytes(response.toString() + "\n")
                    output.flush()
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "Write failed", e)
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client error", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun handleRequest(request: JSONObject): JSONObject {
        return when (request.optString("type")) {
            "sync" -> handleSyncRequest()
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
        private const val TAG = "MobileBluetoothServer"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wear_bluetooth_sync"
        private const val SERVICE_NAME = "MessengerWearSync"
        private const val MAX_CONVERSATIONS = 30
        private const val MAX_MESSAGES_PER_CONVERSATION = 40
    }
}
