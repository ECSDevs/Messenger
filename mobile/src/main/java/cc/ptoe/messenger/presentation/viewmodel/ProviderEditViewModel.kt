package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ProviderEditUiState(
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val nameError: String? = null,
    val baseUrlError: String? = null,
    val apiKeyError: String? = null,
    val isEditing: Boolean = false,
    val isSaved: Boolean = false
)

class ProviderEditViewModel(
    private val providerRepository: ProviderRepository,
    private val providerId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderEditUiState())
    val uiState: StateFlow<ProviderEditUiState> = _uiState.asStateFlow()

    init {
        if (providerId != null) {
            loadProvider(providerId)
        }
    }

    private fun loadProvider(id: String) {
        viewModelScope.launch {
            providerRepository.getById(id).collect { provider ->
                if (provider != null) {
                    _uiState.value = _uiState.value.copy(
                        name = provider.name,
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
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

    fun onBaseUrlChange(baseUrl: String) {
        _uiState.value = _uiState.value.copy(
            baseUrl = baseUrl,
            baseUrlError = null
        )
    }

    fun onApiKeyChange(apiKey: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = apiKey,
            apiKeyError = null
        )
    }

    fun save(): Boolean {
        val currentState = _uiState.value
        var hasError = false

        val nameError = if (currentState.name.isBlank()) {
            "名称不能为空"
        } else null

        val baseUrlError = when {
            currentState.baseUrl.isBlank() -> "API 地址不能为空"
            !isValidUrl(currentState.baseUrl) -> "请输入有效的 URL"
            else -> null
        }

        val apiKeyError = if (currentState.apiKey.isBlank()) {
            "API Key 不能为空"
        } else null

        if (nameError != null || baseUrlError != null || apiKeyError != null) {
            hasError = true
        }

        _uiState.value = currentState.copy(
            nameError = nameError,
            baseUrlError = baseUrlError,
            apiKeyError = apiKeyError
        )

        if (hasError) {
            return false
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (providerId != null) {
                val existing = providerRepository.getById(providerId).first()
                if (existing != null) {
                    val updatedProvider = existing.copy(
                        name = currentState.name.trim(),
                        baseUrl = currentState.baseUrl.trim(),
                        apiKey = currentState.apiKey.trim(),
                        updatedAt = now
                    )
                    providerRepository.update(updatedProvider)
                    _uiState.value = _uiState.value.copy(isSaved = true)
                }
            } else {
                val newProvider = Provider(
                    id = UUID.randomUUID().toString(),
                    name = currentState.name.trim(),
                    baseUrl = currentState.baseUrl.trim(),
                    apiKey = currentState.apiKey.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                providerRepository.insert(newProvider)
                _uiState.value = _uiState.value.copy(isSaved = true)
            }
        }

        return true
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            android.net.Uri.parse(url)
            url.startsWith("http://") || url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun provideFactory(
            providerRepository: ProviderRepository,
            providerId: String? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProviderEditViewModel(providerRepository, providerId) as T
            }
        }
    }
}
