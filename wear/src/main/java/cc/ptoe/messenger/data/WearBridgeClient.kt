package cc.ptoe.messenger.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Watch-side bridge to the phone. Replaces the previous DataLayer-based
 * implementation with a WebSocket transport (see [WearNetworkBridge]) plus
 * a polling loop, so the watch keeps in sync even on Samsung China-region
 * watches where Google Play Services for Wear OS is missing.
 *
 * The transport uses the watch's existing tether / WiFi network: the phone
 * advertises a `_messenger._tcp` NSD service, the watch finds it via mDNS,
 * opens a WebSocket, and both sides speak the same line-delimited JSON
 * protocol as before.
 *
 * Public surface (the [syncUpdates] / [chatResponses] / [newChatResponses]
 * flows and the [requestChat] / [requestNewConversation] methods) is identical
 * to the previous version, so [WearChatRepository] is unchanged.
 */
class WearBridgeClient(
    context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val networkBridge = WearNetworkBridge(context, scope)

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

    /**
     * Snapshot of the network transport's current state, surfaced to the UI
     * so the chat list can show a "waiting for phone" banner.
     */
    val connectionState: StateFlow<WearConnectionState> = networkBridge.connectionState

    /** Bumped each time the user asks for an explicit reconnect from the UI. */
    private val _reconnectRequests = MutableStateFlow(0)
    val reconnectRequests: StateFlow<Int> = _reconnectRequests.asStateFlow()

    private var syncJob: Job? = null
    private var lastUpdatedAt: Long = 0L

    fun start() {
        if (syncJob != null) return
        syncJob = scope.launch {
            // Race the polling loop against user-initiated reconnect requests
            // so a "Reconnect" tap forces an immediate retry.
            val reconnectTrigger = _reconnectRequests
            while (true) {
                val targetTick = reconnectTrigger.value
                if (networkBridge.connect()) {
                    // Always do an initial fetch so the UI doesn't sit on the
                    // empty default state while waiting for the next tick.
                    requestSync()
                    while (networkBridge.isConnected.value) {
                        // Bail out of the inner loop early if the user asked
                        // to reconnect — we want to close the socket and try
                        // again straight away.
                        if (reconnectTrigger.value != targetTick) break
                        delay(POLL_INTERVAL_MS)
                        if (reconnectTrigger.value != targetTick) break
                        requestSync()
                    }
                } else {
                    Log.d(TAG, "Phone not reachable via network, will retry")
                    // Wait either for the next reconnect tick or the normal
                    // backoff, whichever comes first.
                    val deadline = System.currentTimeMillis() + RECONNECT_DELAY_MS
                    while (System.currentTimeMillis() < deadline &&
                        reconnectTrigger.value == targetTick
                    ) {
                        delay(250L)
                    }
                }
                if (reconnectTrigger.value != targetTick) {
                    Log.d(TAG, "Reconnect requested — closing socket and retrying")
                    runCatching { networkBridge.close() }
                }
            }
        }
    }

    /** Drops the active socket so the next [connect] starts fresh. */
    fun close() {
        runCatching { networkBridge.close() }
    }

    fun loadExistingState() {
        // The polling loop in start() already performs the initial fetch, so
        // this is a no-op kept for API compatibility with [WearChatRepository].
        start()
    }

    /**
     * Drops the current connection and asks the polling loop to retry
     * immediately on its next iteration. Use this from the UI when the user
     * taps a "Reconnect" button.
     */
    fun requestReconnect() {
        _reconnectRequests.value = _reconnectRequests.value + 1
    }

    private suspend fun requestSync() {
        val response = networkBridge.requestSync() ?: return
        if (response.optString("type") != "sync_response") return
        val updatedAt = response.optLong("updatedAt", 0L)
        // Always emit on the first poll so the UI has data, then dedupe
        // subsequent polls by the updatedAt timestamp.
        if (lastUpdatedAt != 0L && updatedAt <= lastUpdatedAt) return
        lastUpdatedAt = updatedAt

        runCatching {
            val agentsJson = response.optJSONArray("agents")?.toString() ?: "[]"
            val conversationsJson = response.optJSONArray("conversations")?.toString() ?: "[]"
            val messagesJson = response.optJSONObject("messages")?.toString() ?: "{}"

            val agents = WearChatJsonCodec.decodeAgentsFromSync(agentsJson, emptyMap())
            val conversations = WearChatJsonCodec.decodeConversationsFromSync(conversationsJson)
            val messages = WearChatJsonCodec.decodeMessagesFromSync(messagesJson)

            _syncUpdates.emit(
                WearSyncSnapshot(
                    agents = agents,
                    conversations = conversations,
                    messages = messages,
                    userAvatarPath = null,
                    updatedAt = updatedAt
                )
            )
        }.onFailure {
            Log.w(TAG, "Failed to parse sync response", it)
        }
    }

    suspend fun requestChat(
        requestId: String,
        conversationId: String,
        text: String
    ): Result<Unit> {
        val response = networkBridge.requestChat(requestId, conversationId, text)
            ?: return Result.failure(IllegalStateException("Phone is not connected."))
        val error = response.optString("error").takeIf { it.isNotBlank() }
        if (error != null) {
            _chatResponses.tryEmit(
                WearChatResponse(
                    requestId = requestId,
                    content = null,
                    error = error,
                    userMessageId = null,
                    assistantMessageId = null
                )
            )
            return Result.failure(IllegalStateException(error))
        }
        val content = response.optString("content")
        _chatResponses.tryEmit(
            WearChatResponse(
                requestId = requestId,
                content = content.takeIf { it.isNotBlank() },
                error = null,
                userMessageId = response.optString("userMessageId").takeIf { it.isNotBlank() },
                assistantMessageId = response.optString("assistantMessageId").takeIf { it.isNotBlank() }
            )
        )
        return Result.success(Unit)
    }

    suspend fun requestNewConversation(
        requestId: String,
        agentId: String?
    ): Result<Unit> {
        val response = networkBridge.requestNewConversation(requestId, agentId)
            ?: return Result.failure(IllegalStateException("Phone is not connected."))
        val error = response.optString("error").takeIf { it.isNotBlank() }
        if (error != null) {
            _newChatResponses.tryEmit(
                WearNewChatResponse(
                    requestId = requestId,
                    conversationId = null,
                    agentId = null,
                    error = error
                )
            )
            return Result.failure(IllegalStateException(error))
        }
        val conversationId = response.optString("conversationId").takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Phone did not return a conversation."))
        _newChatResponses.tryEmit(
            WearNewChatResponse(
                requestId = requestId,
                conversationId = conversationId,
                agentId = response.optString("agentId").takeIf { it.isNotBlank() },
                error = null
            )
        )
        return Result.success(Unit)
    }

    companion object {
        private const val TAG = "WearBridgeClient"
        private const val POLL_INTERVAL_MS = 3000L
        private const val RECONNECT_DELAY_MS = 5000L
    }
}
