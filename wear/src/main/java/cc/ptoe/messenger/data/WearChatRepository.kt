package cc.ptoe.messenger.data

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

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
                preferences.applySnapshot(
                    agents = snapshot.agents,
                    conversations = snapshot.conversations,
                    messages = snapshot.messages,
                    userAvatarPath = snapshot.userAvatarPath,
                    selectedConversationId = nextSelection
                )
            }
        }
    }

    suspend fun selectConversation(conversationId: String) {
        preferences.setSelectedConversationId(conversationId)
    }

    /**
     * Entry point for [WearableDataListenerService] to forward DataLayer events
     * emitted by the phone. The buffer is consumed and released by [WearBridgeClient].
     */
    fun handleDataEvents(dataEvents: DataEventBuffer) {
        bridgeClient.onDataChanged(dataEvents)
    }

    /**
     * Entry point for [WearableDataListenerService] to forward messages emitted
     * by the phone (chat responses, new-chat responses).
     */
    fun handleMessage(messageEvent: MessageEvent) {
        bridgeClient.onMessageReceived(messageEvent)
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
            val error = sendResult.exceptionOrNull()?.message ?: "Phone is not connected."
            replacePendingWithError(conversation.id, requestId, error)
            return Result.failure(IllegalStateException(error))
        }

        val response = try {
            withTimeout(60000) {
                bridgeClient.chatResponses.first { it.requestId == requestId }
            }
        } catch (_: Exception) {
            val error = "Timed out waiting for your phone."
            replacePendingWithError(conversation.id, requestId, error)
            return Result.failure(IllegalStateException(error))
        }

        return if (!response.error.isNullOrBlank()) {
            replacePendingWithError(conversation.id, requestId, response.error)
            Result.failure(IllegalStateException(response.error))
        } else {
            val content = response.content.orEmpty()
            val current = preferences.messageHistory.first()[conversation.id].orEmpty()
            val updated = current.map { message ->
                when (message.id) {
                    "local-user-$requestId" -> message.copy(
                        id = response.userMessageId ?: message.id
                    )
                    "local-assistant-$requestId" -> message.copy(
                        id = response.assistantMessageId ?: message.id,
                        content = content,
                        isPending = false,
                        isError = false
                    )
                    else -> message
                }
            }
            saveMessages(conversation.id, updated)
            updateConversationPreview(conversation.id, content, System.currentTimeMillis())
            Result.success(Unit)
        }
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
