package cc.ptoe.messenger.data

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
    val selectedAgentId: Flow<String?> = preferences.selectedAgentId
    val selectedAgent: Flow<WearAgent?> = combine(
        preferences.agents,
        preferences.selectedAgentId
    ) { agents, selectedId ->
        agents.firstOrNull { it.id == selectedId }
    }
    val selectedMessages: Flow<List<WearChatMessage>> = combine(
        preferences.messageHistory,
        preferences.selectedAgentId
    ) { history, selectedId ->
        selectedId?.let { history[it].orEmpty() } ?: emptyList()
    }

    init {
        bridgeClient.start()

        scope.launch {
            clearInterruptedMessages()
        }

        scope.launch {
            bridgeClient.agentUpdates.collect { syncedAgents ->
                val agentIds = syncedAgents.map { it.id }.toSet()
                val currentHistory = preferences.messageHistory.first()
                val trimmedHistory = currentHistory.filterKeys(agentIds::contains)
                preferences.setAgents(syncedAgents)
                preferences.setMessageHistory(trimmedHistory)

                val currentSelection = preferences.selectedAgentId.first()
                val nextSelection = when {
                    syncedAgents.isEmpty() -> null
                    syncedAgents.any { it.id == currentSelection } -> currentSelection
                    else -> syncedAgents.first().id
                }
                preferences.setSelectedAgentId(nextSelection)
            }
        }
    }

    suspend fun requestAgentSync(): Result<Unit> = bridgeClient.requestAgents()

    suspend fun selectAgent(agentId: String) {
        preferences.setSelectedAgentId(agentId)
    }

    suspend fun sendMessage(text: String): Result<Unit> {
        val agent = selectedAgent.first()
            ?: return Result.failure(IllegalStateException("Sync your agents from the phone first."))
        if (!agent.isReady) {
            return Result.failure(
                IllegalStateException("This agent needs a model on your phone before it can chat.")
            )
        }

        val existingMessages = selectedMessages.first()
        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val userMessage = WearChatMessage(
            id = UUID.randomUUID().toString(),
            role = WearMessageRole.USER,
            content = text,
            timestamp = now
        )
        val assistantPlaceholder = WearChatMessage(
            id = requestId,
            role = WearMessageRole.ASSISTANT,
            content = "",
            timestamp = now + 1,
            isPending = true
        )

        saveMessages(agent.id, existingMessages + userMessage + assistantPlaceholder)

        val history = (existingMessages + userMessage)
            .filterNot { it.isError || it.isPending }
            .map { message ->
                WearOutgoingMessage(
                    role = message.role,
                    content = message.content
                )
            }

        val sendResult = bridgeClient.requestChat(
            requestId = requestId,
            agentId = agent.id,
            history = history
        )

        if (sendResult.isFailure) {
            val error = sendResult.exceptionOrNull()?.message ?: "Phone is not connected."
            updateAssistantResult(agent.id, requestId, error, isError = true)
            return Result.failure(IllegalStateException(error))
        }

        val response = try {
            withTimeout(60000) {
                bridgeClient.chatResponses.first { it.requestId == requestId }
            }
        } catch (_: Exception) {
            val error = "Timed out waiting for your phone."
            updateAssistantResult(agent.id, requestId, error, isError = true)
            return Result.failure(IllegalStateException(error))
        }

        return if (!response.error.isNullOrBlank()) {
            updateAssistantResult(agent.id, requestId, response.error, isError = true)
            Result.failure(IllegalStateException(response.error))
        } else {
            updateAssistantResult(
                agentId = agent.id,
                requestId = requestId,
                content = response.content.orEmpty(),
                isError = false
            )
            Result.success(Unit)
        }
    }

    private suspend fun clearInterruptedMessages() {
        val history = preferences.messageHistory.first()
        val normalized = history.mapValues { (_, messages) ->
            messages.map { message ->
                if (message.isPending) {
                    message.copy(
                        content = "Previous reply was interrupted.",
                        isPending = false,
                        isError = true
                    )
                } else {
                    message
                }
            }
        }
        if (normalized != history) {
            preferences.setMessageHistory(normalized)
        }
    }

    private suspend fun updateAssistantResult(
        agentId: String,
        requestId: String,
        content: String,
        isError: Boolean
    ) {
        val currentHistory = preferences.messageHistory.first()
        val updatedMessages = currentHistory[agentId].orEmpty().map { message ->
            if (message.id == requestId) {
                message.copy(
                    content = content,
                    isPending = false,
                    isError = isError
                )
            } else {
                message
            }
        }
        saveMessages(agentId, updatedMessages)
    }

    private suspend fun saveMessages(agentId: String, messages: List<WearChatMessage>) {
        val updatedHistory = preferences.messageHistory.first().toMutableMap()
        updatedHistory[agentId] = messages
        preferences.setMessageHistory(updatedHistory)
    }
}
