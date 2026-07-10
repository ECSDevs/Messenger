package cc.ptoe.messenger.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WearChatRepository(
    private val preferences: WearChatPreferences,
    private val bridgeClient: WearBridgeClient,
    scope: CoroutineScope
) {

    val agents: Flow<List<WearAgent>> = preferences.agents
    val conversations: Flow<List<WearConversation>> = preferences.conversations
    val selectedConversationId: Flow<String?> = preferences.selectedConversationId
    val messageHistory: Flow<Map<String, List<WearChatMessage>>> = preferences.messageHistory
    val userAvatarPath: Flow<String?> = preferences.userAvatarPath

    val connectionState: StateFlow<WearConnectionState> = bridgeClient.connectionState

    fun requestReconnect() = bridgeClient.requestReconnect()

    val selectedConversation: Flow<WearConversation?> = combine(
        preferences.conversations,
        preferences.selectedConversationId
    ) { conversations, selectedId ->
        conversations.firstOrNull { it.id == selectedId }
    }

    val selectedAgent: Flow<WearAgent?> = combine(
        preferences.agents,
        selectedConversation
    ) { agents, conversation ->
        conversation?.let { conv -> agents.firstOrNull { it.id == conv.agentId } }
    }

    val selectedMessages: Flow<List<WearChatMessage>> = combine(
        preferences.messageHistory,
        preferences.selectedConversationId
    ) { history, selectedId ->
        selectedId?.let { history[it].orEmpty() } ?: emptyList()
    }

    /**
     * Conversations that currently have a streaming chat in flight, keyed by
     * conversationId -> requestId. Used by the sync collector to avoid
     * clobbering the local optimistic messages with a stale phone snapshot
     * while the phone is still generating the reply. See [sendMessage].
     */
    private val inFlightChats = ConcurrentHashMap<String, String>()

    private val streamTimeoutMs: Long = 180_000L

    init {
        bridgeClient.start()
        bridgeClient.loadExistingState()

        scope.launch {
            bridgeClient.syncUpdates.collect { snapshot ->
                val currentSelection = preferences.selectedConversationId.first()
                val conversationIds = snapshot.conversations.map { it.id }.toSet()
                val nextSelection = when {
                    snapshot.conversations.isEmpty() -> null
                    currentSelection != null && currentSelection in conversationIds -> currentSelection
                    else -> snapshot.conversations.firstOrNull()?.id
                }
                // While a chat is streaming for a conversation, the phone's
                // sync snapshot lags behind the local optimistic messages
                // (local user message + streaming assistant placeholder).
                // Wholesale-replacing messages here would make the just-sent
                // message vanish until the phone's next sync catches up — the
                // "send -> message disappears" bug. So for in-flight
                // conversations we keep the local messages AND conversations
                // (the conversation's lastMessage/updatedAt may also be ahead
                // locally) and only let the sync overwrite the rest.
                val mergedMessages = snapshot.messages.toMutableMap()
                var mergedConversations = snapshot.conversations
                if (inFlightChats.isNotEmpty()) {
                    val localHistory = preferences.messageHistory.first()
                    val localConversations = preferences.conversations.first()
                    for (convId in inFlightChats.keys) {
                        // Preserve local messages (includes optimistic user msg + placeholder)
                        localHistory[convId]?.let { localMsgs ->
                            mergedMessages[convId] = localMsgs
                        }
                        // Preserve local conversation (includes updated lastMessage/updatedAt)
                        val localConv = localConversations.firstOrNull { it.id == convId }
                        if (localConv != null) {
                            mergedConversations = mergedConversations.map { sc ->
                                if (sc.id == convId) localConv else sc
                            }
                        }
                    }
                }
                preferences.applySnapshot(
                    agents = snapshot.agents,
                    conversations = mergedConversations,
                    messages = mergedMessages,
                    userAvatarPath = snapshot.userAvatarPath,
                    selectedConversationId = nextSelection
                )
            }
        }
    }

    suspend fun selectConversation(conversationId: String) {
        preferences.setSelectedConversationId(conversationId)
    }

    suspend fun createConversation(agentId: String? = null): Result<String> {
        val requestId = UUID.randomUUID().toString()
        val sendResult = bridgeClient.requestNewConversation(requestId, agentId)
        if (sendResult.isFailure) {
            return Result.failure(
                sendResult.exceptionOrNull()
                    ?: IllegalStateException("Phone is not connected.")
            )
        }

        val response = try {
            withTimeout(30000) {
                bridgeClient.newChatResponses.first { it.requestId == requestId }
            }
        } catch (_: Exception) {
            return Result.failure(IllegalStateException("Timed out waiting for your phone."))
        }

        return if (!response.error.isNullOrBlank()) {
            Result.failure(IllegalStateException(response.error))
        } else {
            val conversationId = response.conversationId
                ?: return Result.failure(IllegalStateException("Phone did not return a conversation."))
            preferences.setSelectedConversationId(conversationId)
            Result.success(conversationId)
        }
    }

    suspend fun sendMessage(text: String): Result<Unit> {
        val conversation = selectedConversation.first()
            ?: return Result.failure(IllegalStateException("Open a chat first."))
        val agent = selectedAgent.first()
            ?: return Result.failure(IllegalStateException("Agent not synced yet."))
        if (!agent.isReady) {
            return Result.failure(
                IllegalStateException("This agent needs a model on your phone before it can chat.")
            )
        }

        val existingMessages = selectedMessages.first()
        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val userMessage = WearChatMessage(
            id = "local-user-$requestId",
            conversationId = conversation.id,
            role = WearMessageRole.USER,
            content = text,
            timestamp = now
        )
        val assistantPlaceholder = WearChatMessage(
            id = "local-assistant-$requestId",
            conversationId = conversation.id,
            role = WearMessageRole.ASSISTANT,
            content = "",
            timestamp = now + 1,
            isPending = true
        )

        // Mark in-flight BEFORE writing to DataStore so the sync collector
        // (which reads inFlightChats + local messageHistory together) can
        // never observe a state where messages are written but the guard is
        // missing — that window is exactly what let sync clobber the just-sent
        // message in the previous version.
        inFlightChats[conversation.id] = requestId

        // Insert the optimistic user message + pending assistant placeholder
        // before sending so the user sees their message immediately.
        saveMessages(
            conversation.id,
            existingMessages + userMessage + assistantPlaceholder
        )
        updateConversationPreview(conversation.id, text, now)

        val sendResult = bridgeClient.requestChat(
            requestId = requestId,
            conversationId = conversation.id,
            text = text
        )

        if (sendResult.isFailure) {
            inFlightChats.remove(conversation.id)
            val error = sendResult.exceptionOrNull()?.message ?: "Phone is not connected."
            replacePendingWithError(conversation.id, requestId, error)
            return Result.failure(IllegalStateException(error))
        }

        // Collect streaming frames for this request: accumulate deltas into
        // the placeholder content (so the bubble streams on the watch), until
        // a terminal Done / Error frame arrives. onEach updates the bubble on
        // every delta; `first` returns the terminal frame and cancels collection.
        val currentContent = StringBuilder()
        val terminal = try {
            withTimeout(streamTimeoutMs) {
                bridgeClient.chatFrames
                    .filter { it.requestId == requestId }
                    .onEach { frame ->
                        if (frame is WearChatFrame.Delta) {
                            currentContent.append(frame.delta)
                            updatePlaceholderContent(
                                conversation.id,
                                requestId,
                                currentContent.toString()
                            )
                        }
                    }
                    .first { it is WearChatFrame.Done || it is WearChatFrame.Error }
            }
        } catch (_: Exception) {
            inFlightChats.remove(conversation.id)
            val error = "Timed out waiting for your phone."
            replacePendingWithError(conversation.id, requestId, error)
            return Result.failure(IllegalStateException(error))
        }

        inFlightChats.remove(conversation.id)

        return when (terminal) {
            is WearChatFrame.Error -> {
                replacePendingWithError(conversation.id, requestId, terminal.message)
                Result.failure(IllegalStateException(terminal.message))
            }
            is WearChatFrame.Done -> {
                val content = terminal.content.ifEmpty { currentContent.toString() }
                finalizePlaceholder(
                    conversationId = conversation.id,
                    requestId = requestId,
                    content = content,
                    userMessageId = terminal.userMessageId,
                    assistantMessageId = terminal.assistantMessageId
                )
                updateConversationPreview(
                    conversation.id,
                    content.ifBlank { text },
                    System.currentTimeMillis()
                )
                Result.success(Unit)
            }
            is WearChatFrame.Delta -> error("unreachable: delta is not terminal")
        }
    }

    /**
     * Updates only the assistant placeholder's content while streaming,
     * leaving [WearChatMessage.isPending] true so the bubble keeps its
     * "streaming" styling but now renders real text instead of "Thinking...".
     */
    private suspend fun updatePlaceholderContent(
        conversationId: String,
        requestId: String,
        content: String
    ) {
        val current = preferences.messageHistory.first()[conversationId].orEmpty()
        val updated = current.map { message ->
            if (message.id == "local-assistant-$requestId") {
                message.copy(content = content)
            } else {
                message
            }
        }
        saveMessages(conversationId, updated)
        updateConversationPreview(conversationId, content, System.currentTimeMillis())
    }

    private suspend fun finalizePlaceholder(
        conversationId: String,
        requestId: String,
        content: String,
        userMessageId: String?,
        assistantMessageId: String?
    ) {
        val current = preferences.messageHistory.first()[conversationId].orEmpty()
        val updated = current.map { message ->
            when (message.id) {
                "local-user-$requestId" -> message.copy(
                    id = userMessageId ?: message.id
                )
                "local-assistant-$requestId" -> message.copy(
                    id = assistantMessageId ?: message.id,
                    content = content,
                    isPending = false,
                    isError = false
                )
                else -> message
            }
        }
        saveMessages(conversationId, updated)
    }

    private suspend fun replacePendingWithError(
        conversationId: String,
        requestId: String,
        error: String
    ) {
        val current = preferences.messageHistory.first()[conversationId].orEmpty()
        val updated = current.map { message ->
            if (message.id == "local-assistant-$requestId") {
                message.copy(
                    content = error,
                    isPending = false,
                    isError = true
                )
            } else {
                message
            }
        }
        saveMessages(conversationId, updated)
        updateConversationPreview(conversationId, error, System.currentTimeMillis())
    }

    private suspend fun saveMessages(conversationId: String, messages: List<WearChatMessage>) {
        val updatedHistory = preferences.messageHistory.first().toMutableMap()
        updatedHistory[conversationId] = messages
        preferences.setMessageHistory(updatedHistory)
    }

    private suspend fun updateConversationPreview(
        conversationId: String,
        preview: String,
        updatedAt: Long
    ) {
        val conversations = preferences.conversations.first().map { conversation ->
            if (conversation.id == conversationId) {
                conversation.copy(
                    lastMessage = preview,
                    updatedAt = updatedAt
                )
            } else {
                conversation
            }
        }.sortedByDescending { it.updatedAt }
        preferences.setConversations(conversations)
    }
}
