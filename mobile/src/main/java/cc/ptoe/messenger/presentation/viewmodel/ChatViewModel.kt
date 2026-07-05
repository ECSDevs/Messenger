package cc.ptoe.messenger.presentation.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.data.remote.sse.ChatStreamEvent
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.ApiRepository
import cc.ptoe.messenger.domain.repository.ConversationRepository
import cc.ptoe.messenger.domain.repository.MessageRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import cc.ptoe.messenger.domain.repository.ProviderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val agentRepository: AgentRepository,
    private val apiRepository: ApiRepository,
    private val modelRepository: ModelRepository,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _conversationId = MutableStateFlow<String?>(null)

    val conversation: StateFlow<Conversation?> = _conversationId
        .flatMapLatest { id ->
            if (id != null) {
                conversationRepository.getById(id)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val agent: StateFlow<Agent?> = conversation
        .flatMapLatest { conv ->
            if (conv != null) {
                agentRepository.getById(conv.agentId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val messages: StateFlow<List<Message>> = _conversationId
        .flatMapLatest { id ->
            if (id != null) {
                messageRepository.getByConversationId(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _needsModelSetup = MutableStateFlow(false)
    val needsModelSetup: StateFlow<Boolean> = _needsModelSetup.asStateFlow()

    private var currentGenerationJob: Job? = null

    /**
     * 三层参数合并：对话 override > Agent（含跟随默认Agent） > 默认Agent
     * 1. 先根据 Agent 的 followDefault* 标记合并默认 Agent 的值
     * 2. 再用对话的 override 值覆盖对应的字段（systemPrompt 不允许 override）
     */
    private suspend fun resolveEffectiveAgent(
        agent: Agent,
        conversation: Conversation? = null
    ): Agent {
        val agentWithDefault = if (agent.isDefault) {
            agent
        } else {
            val default = agentRepository.getAll().first().firstOrNull { it.isDefault }
                ?: return agent
            agent.copy(
                systemPrompt = if (agent.followDefaultSystemPrompt) default.systemPrompt else agent.systemPrompt,
                defaultModelId = if (agent.followDefaultModel) default.defaultModelId else agent.defaultModelId,
                temperature = if (agent.followDefaultTemperature) default.temperature else agent.temperature,
                topP = if (agent.followDefaultTopP) default.topP else agent.topP,
                maxTokens = if (agent.followDefaultMaxTokens) default.maxTokens else agent.maxTokens
            )
        }

        if (conversation == null) return agentWithDefault

        return agentWithDefault.copy(
            defaultModelId = conversation.overrideModelId ?: agentWithDefault.defaultModelId,
            temperature = conversation.overrideTemperature ?: agentWithDefault.temperature,
            topP = conversation.overrideTopP ?: agentWithDefault.topP,
            maxTokens = conversation.overrideMaxTokens ?: agentWithDefault.maxTokens
        )
    }

    fun loadConversation(conversationId: String) {
        _conversationId.value = conversationId
    }

    fun sendMessage(text: String) {
        val convId = _conversationId.value ?: return
        if (text.isBlank()) return
        if (_isGenerating.value) return

        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val rawAgent = agent.value ?: run {
                setError("Agent not found")
                return@launch
            }
            val currentAgent = resolveEffectiveAgent(rawAgent, conv)

            if (currentAgent.defaultModelId == null) {
                _needsModelSetup.value = true
                return@launch
            }

            val userMessageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val userMessage = Message(
                id = userMessageId,
                conversationId = convId,
                role = MessageRole.USER,
                content = text,
                timestamp = now,
                status = MessageStatus.SENDING
            )
            messageRepository.insert(userMessage)

            updateConversationLastMessage(convId, text, now)

            if (isNewConversation(conv.title)) {
                updateConversationTitle(convId, generateTitle(text))
            }

            messageRepository.update(userMessage.copy(status = MessageStatus.SENT))

            generateResponse(convId, currentAgent)
        }
    }

    private suspend fun generateResponse(
        conversationId: String,
        agent: Agent
    ) {
        val conv = conversation.value ?: return
        val result = getActiveModelAndProvider(conv, agent)
        if (result == null) {
            setError("No available model. Please configure a provider and model first.")
            return
        }

        val (provider, model) = result

        val aiMessageId = UUID.randomUUID().toString()
        val aiMessage = Message(
            id = aiMessageId,
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING
        )
        messageRepository.insert(aiMessage)

        _isGenerating.value = true

        val historyMessages = messages.value
            .filter { it.status == MessageStatus.SENT }
            .takeLast(20)

        currentGenerationJob = viewModelScope.launch {
            try {
                var currentContent = ""
                var hasFinished = false
                apiRepository.streamChatCompletion(
                    provider = provider,
                    modelId = model.modelId,
                    messages = historyMessages,
                    systemPrompt = agent.systemPrompt,
                    temperature = agent.temperature,
                    topP = agent.topP,
                    maxTokens = agent.maxTokens
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Content -> {
                            currentContent += event.text
                            messageRepository.update(
                                aiMessage.copy(content = currentContent)
                            )
                            updateConversationLastMessage(conversationId, currentContent, System.currentTimeMillis())
                        }
                        is ChatStreamEvent.Done -> {
                            hasFinished = true
                            messageRepository.update(
                                aiMessage.copy(
                                    content = currentContent,
                                    status = MessageStatus.SENT
                                )
                            )
                            updateConversationLastMessage(conversationId, currentContent, System.currentTimeMillis())
                            _isGenerating.value = false
                        }
                        is ChatStreamEvent.Error -> {
                            hasFinished = true
                            messageRepository.update(
                                aiMessage.copy(
                                    content = currentContent,
                                    status = MessageStatus.ERROR,
                                    errorMessage = event.message
                                )
                            )
                            setError(event.message)
                            _isGenerating.value = false
                        }
                    }
                }
                if (!hasFinished) {
                    messageRepository.update(
                        aiMessage.copy(
                            content = currentContent,
                            status = MessageStatus.ERROR,
                            errorMessage = "API 未返回有效响应"
                        )
                    )
                    setError("API 未返回有效响应，请检查 API 配置和参数")
                    _isGenerating.value = false
                }
            } catch (e: Exception) {
                val currentContent = messages.value.find { it.id == aiMessageId }?.content ?: ""
                messageRepository.update(
                    aiMessage.copy(
                        content = currentContent,
                        status = MessageStatus.ERROR,
                        errorMessage = e.message ?: "Unknown error"
                    )
                )
                setError(e.message ?: "Unknown error")
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        _isGenerating.value = false

        viewModelScope.launch {
            val lastAiMessage = messages.value.lastOrNull { it.role == MessageRole.ASSISTANT }
            if (lastAiMessage != null && lastAiMessage.status == MessageStatus.SENDING) {
                messageRepository.update(
                    lastAiMessage.copy(status = MessageStatus.SENT)
                )
            }
        }
    }

    fun retrySend(messageId: String) {
        viewModelScope.launch {
            val message = messages.value.find { it.id == messageId } ?: return@launch
            if (message.status != MessageStatus.ERROR) return@launch
            if (_isGenerating.value) return@launch

            val rawAgent = agent.value ?: return@launch
            val conv = conversation.value ?: return@launch
            val currentAgent = resolveEffectiveAgent(rawAgent, conv)

            if (currentAgent.defaultModelId == null) {
                _needsModelSetup.value = true
                return@launch
            }

            messageRepository.update(
                message.copy(
                    status = MessageStatus.SENDING,
                    errorMessage = null,
                    content = ""
                )
            )
            val result = getActiveModelAndProvider(conv, currentAgent)
            if (result == null) {
                setError("No available model. Please configure a provider and model first.")
                messageRepository.update(
                    message.copy(
                        status = MessageStatus.ERROR,
                        errorMessage = "No available model"
                    )
                )
                return@launch
            }

            val (provider, model) = result

            val historyMessages = messages.value
                .filter { it.status == MessageStatus.SENT && it.id != messageId }
                .takeLast(20)

            _isGenerating.value = true

            currentGenerationJob = launch {
                try {
                    var currentContent = ""
                    var hasFinished = false
                    apiRepository.streamChatCompletion(
                        provider = provider,
                        modelId = model.modelId,
                        messages = historyMessages,
                        systemPrompt = currentAgent.systemPrompt,
                        temperature = currentAgent.temperature,
                        topP = currentAgent.topP,
                        maxTokens = currentAgent.maxTokens
                    ).collect { event ->
                        when (event) {
                            is ChatStreamEvent.Content -> {
                                currentContent += event.text
                                messageRepository.update(
                                    message.copy(content = currentContent)
                                )
                                updateConversationLastMessage(
                                    message.conversationId,
                                    currentContent,
                                    System.currentTimeMillis()
                                )
                            }
                            is ChatStreamEvent.Done -> {
                                hasFinished = true
                                messageRepository.update(
                                    message.copy(
                                        content = currentContent,
                                        status = MessageStatus.SENT,
                                        errorMessage = null
                                    )
                                )
                                updateConversationLastMessage(
                                    message.conversationId,
                                    currentContent,
                                    System.currentTimeMillis()
                                )
                                _isGenerating.value = false
                            }
                            is ChatStreamEvent.Error -> {
                                hasFinished = true
                                messageRepository.update(
                                    message.copy(
                                        content = currentContent,
                                        status = MessageStatus.ERROR,
                                        errorMessage = event.message
                                    )
                                )
                                setError(event.message)
                                _isGenerating.value = false
                            }
                        }
                    }
                    if (!hasFinished) {
                        messageRepository.update(
                            message.copy(
                                content = currentContent,
                                status = MessageStatus.ERROR,
                                errorMessage = "API 未返回有效响应"
                            )
                        )
                        setError("API 未返回有效响应，请检查 API 配置和参数")
                        _isGenerating.value = false
                    }
                } catch (e: Exception) {
                    val currentContent = messages.value.find { it.id == messageId }?.content ?: ""
                    messageRepository.update(
                        message.copy(
                            content = currentContent,
                            status = MessageStatus.ERROR,
                            errorMessage = e.message ?: "Unknown error"
                        )
                    )
                    setError(e.message ?: "Unknown error")
                    _isGenerating.value = false
                }
            }
        }
    }

    fun regenerateMessage(messageId: String) {
        val convId = _conversationId.value ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            val message = messages.value.find { it.id == messageId } ?: return@launch
            if (message.role != MessageRole.ASSISTANT) return@launch

            val rawAgent = agent.value ?: return@launch
            val conv = conversation.value ?: return@launch
            val currentAgent = resolveEffectiveAgent(rawAgent, conv)

            if (currentAgent.defaultModelId == null) {
                _needsModelSetup.value = true
                return@launch
            }

            messageRepository.delete(messageId)

            generateResponse(convId, currentAgent)
        }
    }

    fun copyMessage(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("message", text)
        clipboard.setPrimaryClip(clip)
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.delete(messageId)

            val convId = _conversationId.value ?: return@launch
            val remainingMessages = messageRepository.getByConversationId(convId).first()
            val lastMessage = remainingMessages.lastOrNull()
            updateConversationLastMessage(
                convId,
                lastMessage?.content ?: "",
                lastMessage?.timestamp ?: System.currentTimeMillis()
            )
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun dismissModelSetupPrompt() {
        _needsModelSetup.value = false
    }

    private fun setError(message: String) {
        _errorMessage.value = message
    }

    private suspend fun getActiveModelAndProvider(
        conversation: Conversation,
        agent: Agent
    ): Pair<Provider, ChatModel>? {
        val allModels = modelRepository.getAll().first()
        val enabledModels = allModels.filter { it.isEnabled }

        if (enabledModels.isEmpty()) return null

        val defaultModel = agent.defaultModelId?.let { modelId ->
            enabledModels.find { it.id == modelId }
        }

        val model = defaultModel ?: run {
            if (conversation.providerId.isNotBlank()) {
                enabledModels.find { it.providerId == conversation.providerId }
            } else {
                null
            }
        } ?: enabledModels.first()

        val provider = providerRepository.getById(model.providerId).first() ?: return null

        return provider to model
    }

    private suspend fun updateConversationLastMessage(
        conversationId: String,
        lastMessage: String,
        timestamp: Long
    ) {
        val conv = conversationRepository.getById(conversationId).first() ?: return
        conversationRepository.update(
            conv.copy(
                lastMessage = lastMessage,
                updatedAt = timestamp
            )
        )
    }

    private suspend fun updateConversationTitle(conversationId: String, title: String) {
        val conv = conversationRepository.getById(conversationId).first() ?: return
        conversationRepository.update(conv.copy(title = title))
    }

    private fun isNewConversation(title: String): Boolean {
        return title.isBlank() || title == "新对话" || title == "New Chat"
    }

    private fun generateTitle(firstMessage: String): String {
        val trimmed = firstMessage.trim()
        return if (trimmed.length <= 30) {
            trimmed
        } else {
            trimmed.take(30) + "..."
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentGenerationJob?.cancel()
        currentGenerationJob = null
    }

    companion object {
        fun provideFactory(
            messageRepository: MessageRepository,
            conversationRepository: ConversationRepository,
            agentRepository: AgentRepository,
            apiRepository: ApiRepository,
            modelRepository: ModelRepository,
            providerRepository: ProviderRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    messageRepository,
                    conversationRepository,
                    agentRepository,
                    apiRepository,
                    modelRepository,
                    providerRepository
                ) as T
            }
        }
    }
}
