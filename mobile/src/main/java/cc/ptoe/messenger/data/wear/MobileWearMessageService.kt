package cc.ptoe.messenger.data.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import cc.ptoe.messenger.MessengerApplication
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MobileWearMessageService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncProtocol.CHAT_REQUEST_PATH -> {
                serviceScope.launch {
                    val app = application as MessengerApplication
                    val payload = MobileWearChatHandler(app).handleChatRequest(messageEvent.data)
                    Wearable.getMessageClient(this@MobileWearMessageService)
                        .sendMessage(
                            messageEvent.sourceNodeId,
                            WearSyncProtocol.CHAT_RESPONSE_PATH,
                            payload
                        )
                        .awaitTask()
                    app.wearSyncManager.pushNow()
                }
            }
            WearSyncProtocol.NEW_CHAT_REQUEST_PATH -> {
                serviceScope.launch {
                    val app = application as MessengerApplication
                    val payload = MobileWearChatHandler(app).handleNewConversation(messageEvent.data)
                    Wearable.getMessageClient(this@MobileWearMessageService)
                        .sendMessage(
                            messageEvent.sourceNodeId,
                            WearSyncProtocol.NEW_CHAT_RESPONSE_PATH,
                            payload
                        )
                        .awaitTask()
                    app.wearSyncManager.pushNow()
                }
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

private suspend fun <T> Task<T>.awaitTask(): T {
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
