/*
 * Copyright 2026 ECSDevs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.R
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
            MessengerApplication.instance.getString(R.string.error_name_required)
        } else null

        val baseUrlError = when {
            currentState.baseUrl.isBlank() -> MessengerApplication.instance.getString(R.string.error_api_url_required)
            !isValidUrl(currentState.baseUrl) -> MessengerApplication.instance.getString(R.string.error_invalid_url)
            else -> null
        }

        val apiKeyError = if (currentState.apiKey.isBlank()) {
            MessengerApplication.instance.getString(R.string.error_api_key_required)
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
