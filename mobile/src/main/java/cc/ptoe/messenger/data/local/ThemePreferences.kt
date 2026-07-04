package cc.ptoe.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cc.ptoe.messenger.presentation.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ThemePreferences(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val modeString = preferences[PreferencesKeys.THEME_MODE] ?: "system"
            ThemeMode.valueOf(modeString.uppercase())
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name.lowercase()
        }
    }
}
