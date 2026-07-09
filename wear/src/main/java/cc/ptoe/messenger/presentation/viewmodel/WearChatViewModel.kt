package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.data.WearAgent
import cc.ptoe.messenger.data.WearChatMessage
import cc.ptoe.messenger.data.WearChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WearChatUiState(
    val agents: List<WearAgent> = emptyList(),
    val selectedAgent: WearAgent? = null,
    val messages: List<WearChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val isSyncing: Boolean = false,
    val bannerMessage: String? = null
)

class WearChatViewModel(
    private val repository: WearChatRepository
) : ViewModel() {

    private val isSending = MutableStateFlow(false)
    private val isSyncing = MutableStateFlow(false)
    private val bannerMessage = MutableStateFlow<String?>(null)

    private val chatContent = combine(
        repository.agents,
        repository.selectedAgent,
        repository.selectedMessages
    ) { agents, selectedAgent, messages ->
        Triple(agents, selectedAgent, messages)
    }

    private val transientState = combine(
        isSending,
        isSyncing,
        bannerMessage
    ) { sending, syncing, banner ->
        Triple(sending, syncing, banner)
    }

    val uiState: StateFlow<WearChatUiState> = combine(
        chatContent,
        transientState
    ) { content, transient ->
        WearChatUiState(
            agents = content.first,
            selectedAgent = content.second,
            messages = content.third,
            isSending = transient.first,
            isSyncing = transient.second,
            bannerMessage = transient.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WearChatUiState()
    )

    init {
        refreshAgents()
    }

    fun refreshAgents() {
        viewModelScope.launch {
            isSyncing.value = true
            val result = repository.requestAgentSync()
            isSyncing.value = false
            result.exceptionOrNull()?.message?.let { showBanner(it) }
        }
    }

    fun selectAgent(agentId: String) {
        viewModelScope.launch {
            repository.selectAgent(agentId)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isSending.value) return

        viewModelScope.launch {
            isSending.value = true
            val result = repository.sendMessage(text.trim())
            isSending.value = false
            result.exceptionOrNull()?.message?.let { showBanner(it) }
        }
    }

    fun dismissBanner() {
        bannerMessage.value = null
    }

    private fun showBanner(message: String) {
        bannerMessage.value = message
    }

    companion object {
        fun provideFactory(
            repository: WearChatRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WearChatViewModel(repository) as T
            }
        }
    }
}
