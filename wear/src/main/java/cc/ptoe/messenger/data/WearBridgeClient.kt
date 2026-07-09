package cc.ptoe.messenger.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WearBridgeClient(context: Context) : MessageClient.OnMessageReceivedListener {

    private val appContext = context.applicationContext
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private var isListening = false

    private val _agentUpdates = MutableSharedFlow<List<WearAgent>>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val agentUpdates: SharedFlow<List<WearAgent>> = _agentUpdates.asSharedFlow()

    private val _chatResponses = MutableSharedFlow<WearChatResponse>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val chatResponses: SharedFlow<WearChatResponse> = _chatResponses.asSharedFlow()

    fun start() {
        if (isListening) return
        messageClient.addListener(this)
        isListening = true
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncProtocol.AGENTS_RESPONSE_PATH -> {
                _agentUpdates.tryEmit(WearSyncProtocol.decodeAgents(messageEvent.data))
            }
            WearSyncProtocol.CHAT_RESPONSE_PATH -> {
                _chatResponses.tryEmit(WearSyncProtocol.decodeChatResponse(messageEvent.data))
            }
        }
    }

    suspend fun requestAgents(): Result<Unit> {
        return sendMessage(
            path = WearSyncProtocol.AGENTS_REQUEST_PATH,
            payload = ByteArray(0)
        )
    }

    suspend fun requestChat(
        requestId: String,
        agentId: String,
        history: List<WearOutgoingMessage>
    ): Result<Unit> {
        return sendMessage(
            path = WearSyncProtocol.CHAT_REQUEST_PATH,
            payload = WearSyncProtocol.encodeChatRequest(requestId, agentId, history)
        )
    }

    private suspend fun sendMessage(path: String, payload: ByteArray): Result<Unit> {
        return runCatching {
            val node = resolvePhoneNode()
            messageClient.sendMessage(node.id, path, payload).await()
            return@runCatching Unit
        }
    }

    private suspend fun resolvePhoneNode(): Node {
        val nodes = nodeClient.connectedNodes.await()
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        ?: throw IllegalStateException("Phone is not connected.")
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
