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
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlin.reflect.KClass
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.domain.repository.ApiRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import cc.ptoe.messenger.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import cc.ptoe.messenger.generated.resources.Res
import cc.ptoe.messenger.generated.resources.error_load_failed
import cc.ptoe.messenger.generated.resources.error_sync_failed
import org.jetbrains.compose.resources.getString
import cc.ptoe.messenger.data.util.randomUuid

enum class SyncStatus {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

data class ProviderDetailUiState(
    val provider: Provider? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val syncError: String? = null,
    val syncedModels: List<ChatModel> = emptyList()
)

class ProviderDetailViewModel(
    private val providerRepository: ProviderRepository,
    private val modelRepository: ModelRepository,
    private val apiRepository: ApiRepository,
    private val providerId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderDetailUiState())
    val uiState: StateFlow<ProviderDetailUiState> = _uiState.asStateFlow()

    val models = modelRepository.getByProviderId(providerId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadProvider()
    }

    private fun loadProvider() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                providerRepository.getById(providerId).collect { provider ->
                    _uiState.value = _uiState.value.copy(
                        provider = provider,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: getString(Res.string.error_load_failed)
                )
            }
        }
    }

    fun syncModels() {
        val provider = _uiState.value.provider ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                syncStatus = SyncStatus.LOADING,
                syncError = null,
                syncedModels = emptyList()
            )
            try {
                val fetchedModels = apiRepository.fetchModels(provider)
                _uiState.value = _uiState.value.copy(
                    syncStatus = SyncStatus.SUCCESS,
                    syncedModels = fetchedModels
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    syncStatus = SyncStatus.ERROR,
                    syncError = e.message ?: getString(Res.string.error_sync_failed)
                )
            }
        }
    }

    fun resetSyncState() {
        _uiState.value = _uiState.value.copy(
            syncStatus = SyncStatus.IDLE,
            syncError = null,
            syncedModels = emptyList()
        )
    }

    suspend fun saveSelectedModels(selectedModelIds: List<String>) {
        val provider = _uiState.value.provider ?: return
        val existingModels = modelRepository.getByProviderId(providerId).first()
        val existingModelIds = existingModels.map { it.modelId }.toSet()
        val newModels = _uiState.value.syncedModels
            .filter { it.modelId in selectedModelIds && it.modelId !in existingModelIds }
            .map { model ->
                model.copy(
                    id = randomUuid(),
                    providerId = provider.id,
                    isEnabled = true,
                    createdAt = System.currentTimeMillis()
                )
            }
        if (newModels.isNotEmpty()) {
            modelRepository.insertAll(newModels)
        }
        resetSyncState()
    }

    fun toggleModelEnabled(modelId: String, enabled: Boolean) {
        viewModelScope.launch {
            modelRepository.setEnabled(modelId, enabled)
        }
    }

    fun addModelManually(modelId: String, displayName: String): Boolean {
        val provider = _uiState.value.provider ?: return false
        if (modelId.isBlank()) return false
        viewModelScope.launch {
            val existingModels = modelRepository.getByProviderId(providerId).first()
            val exists = existingModels.any { it.modelId == modelId }
            if (!exists) {
                val newModel = ChatModel(
                    id = randomUuid(),
                    providerId = provider.id,
                    modelId = modelId,
                    displayName = displayName.ifBlank { modelId },
                    isEnabled = true,
                    createdAt = System.currentTimeMillis()
                )
                modelRepository.insert(newModel)
            }
        }
        return true
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.delete(modelId)
        }
    }

    companion object {
        fun provideFactory(
            providerRepository: ProviderRepository,
            modelRepository: ModelRepository,
            apiRepository: ApiRepository,
            providerId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                return ProviderDetailViewModel(
                    providerRepository,
                    modelRepository,
                    apiRepository,
                    providerId
                ) as T
            }
        }
    }
}
