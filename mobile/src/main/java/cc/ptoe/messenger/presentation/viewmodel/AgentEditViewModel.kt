package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.data.cloud.CloudMarketAgentUpdate
import cc.ptoe.messenger.data.cloud.CloudSyncRepository
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.R
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import cc.ptoe.messenger.domain.repository.ProviderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class AgentEditUiState(
    val name: String = "",
    val avatar: String? = null,
    val systemPrompt: String = "",
    val defaultModelId: String? = null,
    val selectedProviderId: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: String? = null,
    val thinkingEnabled: Boolean = false,
    val isDefault: Boolean = false,
    val followDefaultSystemPrompt: Boolean = false,
    val followDefaultModel: Boolean = false,
    val followDefaultTemperature: Boolean = false,
    val followDefaultTopP: Boolean = false,
    val followDefaultMaxTokens: Boolean = false,
    val followDefaultThinking: Boolean = false,
    val defaultAgent: Agent? = null,
    val nameError: String? = null,
    val isEditing: Boolean = false,
    val isSaved: Boolean = false,
    val marketAgentId: String? = null,
    val marketAgentRole: String? = null,
    val marketActionInProgress: Boolean = false
)

class AgentEditViewModel(
    private val agentRepository: AgentRepository,
    private val modelRepository: ModelRepository,
    private val providerRepository: ProviderRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val agentId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentEditUiState())
    val uiState: StateFlow<AgentEditUiState> = _uiState.asStateFlow()

    val providers: StateFlow<List<Provider>> = providerRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val modelsForSelectedProvider: StateFlow<List<ChatModel>> = _uiState
        .map { it.selectedProviderId }
        .distinctUntilChanged()
        .flatMapLatest { providerId ->
            if (providerId == null) {
                flowOf(emptyList())
            } else {
                modelRepository.getEnabledByProviderId(providerId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // 始终加载默认 Agent，用于非默认 Agent 的"跟随"展示
        viewModelScope.launch {
            agentRepository.getAll().collect { agents ->
                val default = agents.firstOrNull { it.isDefault }
                _uiState.value = _uiState.value.copy(defaultAgent = default)
            }
        }
        if (agentId != null) {
            loadAgent(agentId)
        }
    }

    private fun loadAgent(id: String) {
        viewModelScope.launch {
            agentRepository.getById(id).collect { agent ->
                if (agent != null) {
                    val providerId = agent.defaultModelId?.let { modelId ->
                        modelRepository.getById(modelId).first()?.providerId
                    }
                    _uiState.value = _uiState.value.copy(
                        name = agent.name,
                        avatar = agent.avatar,
                        systemPrompt = agent.systemPrompt,
                        defaultModelId = agent.defaultModelId,
                        selectedProviderId = providerId,
                        temperature = agent.temperature,
                        topP = agent.topP,
                        maxTokens = agent.maxTokens?.toString(),
                        thinkingEnabled = agent.thinkingEnabled,
                        isDefault = agent.isDefault,
                        followDefaultSystemPrompt = agent.followDefaultSystemPrompt,
                        followDefaultModel = agent.followDefaultModel,
                        followDefaultTemperature = agent.followDefaultTemperature,
                        followDefaultTopP = agent.followDefaultTopP,
                        followDefaultMaxTokens = agent.followDefaultMaxTokens,
                        followDefaultThinking = agent.followDefaultThinking,
                        marketAgentId = agent.marketAgentId,
                        marketAgentRole = agent.marketAgentRole,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null
        )
    }

    fun onAvatarChange(avatar: String?) {
        _uiState.value = _uiState.value.copy(avatar = avatar)
    }

    fun onSystemPromptChange(systemPrompt: String) {
        _uiState.value = _uiState.value.copy(systemPrompt = systemPrompt)
    }

    fun onProviderChange(providerId: String?) {
        // 切换 Provider 时清空已选模型，避免出现 provider 与 model 不匹配的情况
        _uiState.value = _uiState.value.copy(
            selectedProviderId = providerId,
            defaultModelId = null
        )
    }

    fun onDefaultModelChange(modelId: String?) {
        _uiState.value = _uiState.value.copy(defaultModelId = modelId)
    }

    fun onTemperatureChange(temperature: Float) {
        _uiState.value = _uiState.value.copy(temperature = temperature)
    }

    fun onTopPChange(topP: Float) {
        _uiState.value = _uiState.value.copy(topP = topP)
    }

    fun onMaxTokensChange(maxTokens: String?) {
        _uiState.value = _uiState.value.copy(maxTokens = maxTokens)
    }

    fun onFollowSystemPromptChange(follow: Boolean) {
        _uiState.value = _uiState.value.copy(followDefaultSystemPrompt = follow)
    }

    fun onFollowModelChange(follow: Boolean) {
        _uiState.value = _uiState.value.copy(followDefaultModel = follow)
    }

    fun onFollowTemperatureChange(follow: Boolean) {
        _uiState.value = _uiState.value.copy(followDefaultTemperature = follow)
    }

    fun onFollowTopPChange(follow: Boolean) {
        _uiState.value = _uiState.value.copy(followDefaultTopP = follow)
    }

    fun onFollowMaxTokensChange(follow: Boolean) {
        _uiState.value = _uiState.value.copy(followDefaultMaxTokens = follow)
    }

    fun onThinkingChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(thinkingEnabled = enabled)
    }

    fun onFollowThinkingChange(follow: Boolean) {
        _uiState.value = _uiState.value.copy(followDefaultThinking = follow)
    }

    fun save(): Boolean {
        val currentState = _uiState.value
        var hasError = false

        val nameError = if (currentState.name.isBlank()) {
            MessengerApplication.instance.getString(R.string.error_name_required)
        } else null

        if (nameError != null) {
            hasError = true
        }

        _uiState.value = currentState.copy(
            nameError = nameError
        )

        if (hasError) {
            return false
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val maxTokensInt = currentState.maxTokens?.toIntOrNull()

            if (agentId != null) {
                val existing = agentRepository.getById(agentId).first()
                if (existing != null) {
                    val updatedAgent = existing.copy(
                        name = currentState.name.trim(),
                        avatar = currentState.avatar,
                        systemPrompt = currentState.systemPrompt.trim(),
                        defaultModelId = currentState.defaultModelId,
                        temperature = currentState.temperature,
                        topP = currentState.topP,
                        maxTokens = maxTokensInt,
                        thinkingEnabled = currentState.thinkingEnabled,
                        followDefaultSystemPrompt = currentState.followDefaultSystemPrompt,
                        followDefaultModel = currentState.followDefaultModel,
                        followDefaultTemperature = currentState.followDefaultTemperature,
                        followDefaultTopP = currentState.followDefaultTopP,
                        followDefaultMaxTokens = currentState.followDefaultMaxTokens,
                        followDefaultThinking = currentState.followDefaultThinking,
                        updatedAt = now
                    )
                    agentRepository.update(updatedAgent)
                    _uiState.value = _uiState.value.copy(isSaved = true)
                }
            } else {
                val newAgent = Agent(
                    id = UUID.randomUUID().toString(),
                    name = currentState.name.trim(),
                    avatar = currentState.avatar,
                    systemPrompt = currentState.systemPrompt.trim(),
                    defaultModelId = currentState.defaultModelId,
                    temperature = currentState.temperature,
                    topP = currentState.topP,
                    maxTokens = maxTokensInt,
                    thinkingEnabled = currentState.thinkingEnabled,
                    isDefault = false,
                    followDefaultSystemPrompt = currentState.followDefaultSystemPrompt,
                    followDefaultModel = currentState.followDefaultModel,
                    followDefaultTemperature = currentState.followDefaultTemperature,
                    followDefaultTopP = currentState.followDefaultTopP,
                    followDefaultMaxTokens = currentState.followDefaultMaxTokens,
                    followDefaultThinking = currentState.followDefaultThinking,
                    createdAt = now,
                    updatedAt = now
                )
                agentRepository.insert(newAgent)
                _uiState.value = _uiState.value.copy(isSaved = true)
            }
        }

        return true
    }

    fun publishMarketAgent(onResult: (Result<Unit>) -> Unit) = runMarketAction(onResult) {
        cloudSyncRepository.publishMarketAgent(requireNotNull(agentId))
    }

    fun pushMarketAgentUpdate(onResult: (Result<Unit>) -> Unit) = runMarketAction(onResult) {
        cloudSyncRepository.pushMarketAgentUpdate(requireNotNull(agentId))
    }

    fun removeMarketAgent(onResult: (Result<Unit>) -> Unit) = runMarketAction(onResult) {
        cloudSyncRepository.removeMarketAgent(requireNotNull(agentId))
    }

    fun checkMarketAgentUpdate(onResult: (Result<CloudMarketAgentUpdate>) -> Unit) {
        val id = agentId ?: return onResult(Result.failure(IllegalStateException("Save the Agent before checking updates.")))
        _uiState.value = _uiState.value.copy(marketActionInProgress = true)
        viewModelScope.launch {
            val result = runCatching { cloudSyncRepository.checkMarketAgentUpdate(id) }
            _uiState.value = _uiState.value.copy(marketActionInProgress = false)
            onResult(result)
        }
    }

    fun applyMarketAgentUpdate(onResult: (Result<Unit>) -> Unit) = runMarketAction(onResult) {
        val update = cloudSyncRepository.checkMarketAgentUpdate(requireNotNull(agentId))
        check(update.hasUpdate) { "This Agent is already up to date." }
        cloudSyncRepository.applyMarketAgentUpdate(requireNotNull(agentId), update.agent)
    }

    private fun runMarketAction(onResult: (Result<Unit>) -> Unit, action: suspend () -> Unit) {
        if (agentId == null) {
            onResult(Result.failure(IllegalStateException("Save the Agent before using the market.")))
            return
        }
        _uiState.value = _uiState.value.copy(marketActionInProgress = true)
        viewModelScope.launch {
            val result = runCatching {
                saveCurrentAgentForMarket()
                action()
            }
            _uiState.value = _uiState.value.copy(marketActionInProgress = false)
            onResult(result)
        }
    }

    private suspend fun saveCurrentAgentForMarket() {
        val id = requireNotNull(agentId)
        val currentState = _uiState.value
        check(currentState.name.isNotBlank()) { "名称不能为空" }
        val existing = checkNotNull(agentRepository.getById(id).first()) { "Agent 不存在" }
        agentRepository.update(
            existing.copy(
                name = currentState.name.trim(),
                avatar = currentState.avatar,
                systemPrompt = currentState.systemPrompt.trim(),
                defaultModelId = currentState.defaultModelId,
                temperature = currentState.temperature,
                topP = currentState.topP,
                maxTokens = currentState.maxTokens?.toIntOrNull(),
                thinkingEnabled = currentState.thinkingEnabled,
                followDefaultSystemPrompt = currentState.followDefaultSystemPrompt,
                followDefaultModel = currentState.followDefaultModel,
                followDefaultTemperature = currentState.followDefaultTemperature,
                followDefaultTopP = currentState.followDefaultTopP,
                followDefaultMaxTokens = currentState.followDefaultMaxTokens,
                followDefaultThinking = currentState.followDefaultThinking,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        fun provideFactory(
            agentRepository: AgentRepository,
            modelRepository: ModelRepository,
            providerRepository: ProviderRepository,
            cloudSyncRepository: CloudSyncRepository,
            agentId: String? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AgentEditViewModel(
                    agentRepository,
                    modelRepository,
                    providerRepository,
                    cloudSyncRepository,
                    agentId
                ) as T
            }
        }
    }
}
