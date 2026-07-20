package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.ConversationRepository
import cc.ptoe.messenger.domain.repository.CurrentAgentRepository
import cc.ptoe.messenger.domain.repository.MessageRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModel(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val currentAgentRepository: CurrentAgentRepository,
    private val agentRepository: AgentRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    val currentAgent: StateFlow<Agent?> = currentAgentRepository.currentAgent
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val allAgents: StateFlow<List<Agent>> = agentRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _showAllAgents = MutableStateFlow(true)
    val showAllAgents: StateFlow<Boolean> = _showAllAgents.asStateFlow()

    val conversations: StateFlow<List<Conversation>> = combine(
        currentAgentRepository.currentAgent,
        _showAllAgents
    ) { agent, showAll -> agent to showAll }
        .flatMapLatest { (agent, showAll) ->
            when {
                showAll -> conversationRepository.getAll()
                agent != null -> conversationRepository.getByAgentId(agent.id)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createNewConversation(
        title: String = "新对话",
        agentId: String? = null,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val agent = if (agentId != null) {
                agentRepository.getById(agentId).first()
            } else {
                currentAgentRepository.currentAgent.first()
            } ?: return@launch
            currentAgentRepository.setCurrentAgentId(agent.id)
            _showAllAgents.value = false
            val now = System.currentTimeMillis()
            val providerId = determineProviderId(agent)
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                title = title,
                providerId = providerId,
                agentId = agent.id,
                createdAt = now,
                updatedAt = now,
                lastMessage = null
            )
            conversationRepository.insert(conversation)
            onCreated(conversation.id)
        }
    }

    private suspend fun determineProviderId(agent: Agent): String {
        val allModels = modelRepository.getAll().first()
        val enabledModels = allModels.filter { it.isEnabled }

        if (enabledModels.isEmpty()) return ""

        val defaultModel = agent.defaultModelId?.let { modelId ->
            enabledModels.find { it.id == modelId }
        }

        return (defaultModel ?: enabledModels.firstOrNull())?.providerId ?: ""
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.getById(conversationId).first()
                ?: return@launch
            val now = System.currentTimeMillis()
            conversationRepository.update(
                conversation.copy(
                    title = newTitle,
                    updatedAt = now
                )
            )
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.delete(conversationId)
        }
    }

    fun cloneConversation(
        conversationId: String,
        onCloned: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val source = conversationRepository.getById(conversationId).first()
                ?: return@launch
            val clonedConversationId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val clonedConversation = source.copy(
                id = clonedConversationId,
                title = "${source.title}（副本）",
                createdAt = now,
                updatedAt = now
            )

            conversationRepository.insert(clonedConversation)
            val clonedMessages = messageRepository.getByConversationId(source.id).first().map { message ->
                message.copy(
                    id = UUID.randomUUID().toString(),
                    conversationId = clonedConversationId,
                    status = if (message.status == MessageStatus.SENDING) {
                        MessageStatus.SENT
                    } else {
                        message.status
                    }
                )
            }
            messageRepository.insertAll(clonedMessages)
            onCloned(clonedConversationId)
        }
    }

    fun switchAgent(agentId: String) {
        viewModelScope.launch {
            currentAgentRepository.setCurrentAgentId(agentId)
        }
        _showAllAgents.value = false
    }

    fun showAllAgents() {
        _showAllAgents.value = true
    }

    companion object {
        fun provideFactory(
            conversationRepository: ConversationRepository,
            messageRepository: MessageRepository,
            currentAgentRepository: CurrentAgentRepository,
            agentRepository: AgentRepository,
            modelRepository: ModelRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConversationsViewModel(
                    conversationRepository,
                    messageRepository,
                    currentAgentRepository,
                    agentRepository,
                    modelRepository
                ) as T
            }
        }
    }
}
