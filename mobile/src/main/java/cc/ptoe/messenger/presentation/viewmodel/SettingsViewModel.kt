package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.data.local.AppPreferences
import cc.ptoe.messenger.data.local.ThemePreferences
import cc.ptoe.messenger.data.cloud.CloudManifest
import cc.ptoe.messenger.data.cloud.CloudSyncRepository
import cc.ptoe.messenger.data.cloud.CloudUser
import cc.ptoe.messenger.presentation.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themePreferences: ThemePreferences,
    private val appPreferences: AppPreferences,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    val themeMode = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    val userAvatar = appPreferences.userAvatar
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val cloudUser = cloudSyncRepository.user
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val cloudServerUrl = cloudSyncRepository.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://messenger.ptoe.cc")

    fun setCloudServerUrl(url: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { cloudSyncRepository.setServerUrl(url) }) }
    }

    fun login(email: String, password: String, onResult: (Result<CloudUser>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { cloudSyncRepository.login(email, password) }) }
    }

    fun register(email: String, password: String, onResult: (Result<CloudUser>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { cloudSyncRepository.register(email, password) }) }
    }

    fun logout(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { cloudSyncRepository.logout() }) }
    }

    fun upload(onResult: (Result<CloudManifest>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { cloudSyncRepository.upload() }) }
    }

    fun restore(onResult: (Result<CloudManifest>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { cloudSyncRepository.downloadAndRestore() }) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setUserAvatar(avatar: String?) {
        viewModelScope.launch {
            appPreferences.setUserAvatar(avatar)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            MessengerApplication.instance.clearAllDataAndReinit()
        }
    }

    companion object {
        fun provideFactory(
            themePreferences: ThemePreferences,
            appPreferences: AppPreferences,
            cloudSyncRepository: CloudSyncRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(themePreferences, appPreferences, cloudSyncRepository) as T
            }
        }
    }
}
