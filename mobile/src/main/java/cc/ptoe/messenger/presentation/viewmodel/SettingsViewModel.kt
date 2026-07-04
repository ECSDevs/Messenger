package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.data.local.ThemePreferences
import cc.ptoe.messenger.presentation.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            MessengerApplication.instance.clearAllDataAndReinit()
        }
    }

    companion object {
        fun provideFactory(
            themePreferences: ThemePreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(themePreferences) as T
            }
        }
    }
}
