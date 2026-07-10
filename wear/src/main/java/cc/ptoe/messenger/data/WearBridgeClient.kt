package cc.ptoe.messenger.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WearBridgeClient(
    context: Context,
    private val scope: CoroutineScope
) : MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {

    private val appContext = context.applicationContext
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val dataClient = Wearable.getDataClient(appContext)
    private val avatarDir = File(appContext.filesDir, "synced_avatars").also { it.mkdirs() }
    private var isListening = false

    private val _syncUpdates = MutableSharedFlow<WearSyncSnapshot>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val syncUpdates: SharedFlow<WearSyncSnapshot> = _syncUpdates.asSharedFlow()

    private val _chatResponses = MutableSharedFlow<WearChatResponse>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val chatResponses: SharedFlow<WearChatResponse> = _chatResponses.asSharedFlow()

    private val _newChatResponses = MutableSharedFlow<WearNewChatResponse>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val newChatResponses: SharedFlow<WearNewChatResponse> = _newChatResponses.asSharedFlow()

    fun start() {
        if (isListening) return
        // Programmatic listeners are intentionally NOT registered here.
        // Once a WearableListenerService is declared in the manifest the system
        // delivers DataLayer events to the service instead of to listeners added
        // via the data/message client. The service (WearableDataListenerService)
        // forwards into this client via onDataChanged/onMessageReceived.
        isListening = true
    }

    fun loadExistingState() {
        scope.launch {
            runCatching {
                val buffer = dataClient.dataItems.await()
                try {
                    val iterator = buffer.iterator()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        if (item.uri.path == WearSyncProtocol.STATE_PATH) {
                            emitSnapshot(DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                } finally {
                    buffer.release()
                }
            }.onFailure {
                Log.w(TAG, "Failed to load existing DataLayer state", it)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncProtocol.CHAT_RESPONSE_PATH -> {
                _chatResponses.tryEmit(WearSyncProtocol.decodeChatResponse(messageEvent.data))
            }
            WearSyncProtocol.NEW_CHAT_RESPONSE_PATH -> {
                _newChatResponses.tryEmit(WearSyncProtocol.decodeNewChatResponse(messageEvent.data))
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            val iterator = dataEvents.iterator()
            while (iterator.hasNext()) {
                val event = iterator.next()
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != WearSyncProtocol.STATE_PATH) continue
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                scope.launch {
                    emitSnapshot(dataMap)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    suspend fun requestChat(
        requestId: String,
        conversationId: String,
        text: String
    ): Result<Unit> {
        return sendMessage(
            path = WearSyncProtocol.CHAT_REQUEST_PATH,
            payload = WearSyncProtocol.encodeChatRequest(requestId, conversationId, text)
        )
    }

    suspend fun requestNewConversation(
        requestId: String,
        agentId: String?
    ): Result<Unit> {
        return sendMessage(
            path = WearSyncProtocol.NEW_CHAT_REQUEST_PATH,
            payload = WearSyncProtocol.encodeNewChatRequest(requestId, agentId)
        )
    }

    private suspend fun emitSnapshot(dataMap: DataMap) {
        runCatching {
            val agentsJson = dataMap.getString(WearSyncProtocol.KEY_AGENTS).orEmpty()
            val conversationsJson = dataMap.getString(WearSyncProtocol.KEY_CONVERSATIONS).orEmpty()
            val messagesJson = dataMap.getString(WearSyncProtocol.KEY_MESSAGES).orEmpty()
            val updatedAt = dataMap.getLong(WearSyncProtocol.KEY_UPDATED_AT, 0L)

            val agentsArray = JSONArray(agentsJson.ifBlank { "[]" })
            val avatarPaths = mutableMapOf<String, String?>()
            repeat(agentsArray.length()) { index ->
                val agentId = agentsArray.getJSONObject(index).optString("id")
                if (agentId.isBlank()) return@repeat
                val asset = dataMap.getAsset(WearSyncProtocol.agentAvatarKey(agentId))
                avatarPaths[agentId] = asset?.let { saveAsset(it, "agent_$agentId.jpg") }
            }

            val userAvatarAsset = dataMap.getAsset(WearSyncProtocol.KEY_USER_AVATAR)
            val userAvatarPath = userAvatarAsset?.let { saveAsset(it, "user.jpg") }

            val agents = WearChatJsonCodec.decodeAgentsFromSync(agentsJson, avatarPaths)
            val conversations = WearChatJsonCodec.decodeConversationsFromSync(conversationsJson)
            val messages = WearChatJsonCodec.decodeMessagesFromSync(messagesJson)

            _syncUpdates.emit(
                WearSyncSnapshot(
                    agents = agents,
                    conversations = conversations,
                    messages = messages,
                    userAvatarPath = userAvatarPath,
                    updatedAt = updatedAt
                )
            )
        }.onFailure {
            Log.w(TAG, "Failed to parse DataLayer snapshot", it)
        }
    }

    private suspend fun saveAsset(
        asset: com.google.android.gms.wearable.Asset,
        fileName: String
    ): String? {
        return runCatching {
            val fd = dataClient.getFdForAsset(asset).await()
            val input = fd.inputStream ?: return null
            val target = File(avatarDir, fileName)
            input.use { stream ->
                FileOutputStream(target).use { output ->
                    stream.copyTo(output)
                }
            }
            target.absolutePath
        }.getOrNull()
    }

    private suspend fun sendMessage(path: String, payload: ByteArray): Result<Unit> {
        return runCatching {
            val node = resolvePhoneNode()
            messageClient.sendMessage(node.id, path, payload).await()
            Unit
        }
    }

    private suspend fun resolvePhoneNode(): Node {
        val nodes = nodeClient.connectedNodes.await()
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            ?: throw IllegalStateException("Phone is not connected.")
    }

    companion object {
        private const val TAG = "WearBridgeClient"
    }
}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}
