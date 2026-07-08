package cc.ptoe.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_AGENT_INITIALIZED = booleanPreferencesKey("default_agent_initialized")
        val CURRENT_AGENT_ID = stringPreferencesKey("current_agent_id")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
    }

    val defaultAgentInitialized: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_AGENT_INITIALIZED] ?: false
        }

    suspend fun setDefaultAgentInitialized(initialized: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_AGENT_INITIALIZED] = initialized
        }
    }

    val currentAgentId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CURRENT_AGENT_ID]
        }

    suspend fun setCurrentAgentId(agentId: String?) {
        context.dataStore.edit { preferences ->
            if (agentId != null) {
                preferences[PreferencesKeys.CURRENT_AGENT_ID] = agentId
            } else {
                preferences.remove(PreferencesKeys.CURRENT_AGENT_ID)
            }
        }
    }

    val userAvatar: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_AVATAR]
        }

    suspend fun setUserAvatar(avatar: String?) {
        context.dataStore.edit { preferences ->
            if (avatar != null) {
                preferences[PreferencesKeys.USER_AVATAR] = avatar
            } else {
                preferences.remove(PreferencesKeys.USER_AVATAR)
            }
        }
    }
}
