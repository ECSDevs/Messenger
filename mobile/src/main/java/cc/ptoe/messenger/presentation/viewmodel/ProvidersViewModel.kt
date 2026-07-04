package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.domain.repository.ModelRepository
import cc.ptoe.messenger.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderWithModelCount(
    val provider: Provider,
    val modelCount: Int
)

class ProvidersViewModel(
    private val providerRepository: ProviderRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    val providersWithModelCount = combine(
        providerRepository.getAll(),
        modelRepository.getAll()
    ) { providers, models ->
        providers.map { provider ->
            val modelCount = models.count { it.providerId == provider.id }
            ProviderWithModelCount(
                provider = provider,
                modelCount = modelCount
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            providerRepository.delete(providerId)
        }
    }

    companion object {
        fun provideFactory(
            providerRepository: ProviderRepository,
            modelRepository: ModelRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProvidersViewModel(providerRepository, modelRepository) as T
            }
        }
    }
}
