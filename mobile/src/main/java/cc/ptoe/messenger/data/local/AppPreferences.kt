package cc.ptoe.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppPreferences(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_AGENT_INITIALIZED = booleanPreferencesKey("default_agent_initialized")
        val CURRENT_AGENT_ID = stringPreferencesKey("current_agent_id")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val CLOUD_SERVER_URL = stringPreferencesKey("cloud_server_url")
        val CLOUD_SESSION = stringPreferencesKey("cloud_session")
        val CLOUD_SESSION_HOST = stringPreferencesKey("cloud_session_host")
        val CLOUD_USER = stringPreferencesKey("cloud_user")
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

    val cloudServerUrl: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CLOUD_SERVER_URL] }

    suspend fun setCloudServerUrl(url: String?) {
        context.dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.CLOUD_SERVER_URL)
            } else {
                preferences[PreferencesKeys.CLOUD_SERVER_URL] = url.trim().trimEnd('/')
            }
        }
    }

    val cloudSession: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CLOUD_SESSION] }

    suspend fun setCloudSession(session: String?) {
        context.dataStore.edit { preferences ->
            if (session.isNullOrBlank()) preferences.remove(PreferencesKeys.CLOUD_SESSION)
            else preferences[PreferencesKeys.CLOUD_SESSION] = session
        }
    }

    val cloudSessionHost: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CLOUD_SESSION_HOST] }

    suspend fun setCloudSessionHost(host: String?) {
        context.dataStore.edit { preferences ->
            if (host.isNullOrBlank()) preferences.remove(PreferencesKeys.CLOUD_SESSION_HOST)
            else preferences[PreferencesKeys.CLOUD_SESSION_HOST] = host
        }
    }

    val cloudUser: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CLOUD_USER] }

    suspend fun setCloudUser(user: String?) {
        context.dataStore.edit { preferences ->
            if (user.isNullOrBlank()) preferences.remove(PreferencesKeys.CLOUD_USER)
            else preferences[PreferencesKeys.CLOUD_USER] = user
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun clearCloudAccount(accountId: String?) {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CLOUD_SESSION)
            preferences.remove(PreferencesKeys.CLOUD_SESSION_HOST)
            preferences.remove(PreferencesKeys.CLOUD_USER)
            if (accountId != null) {
                preferences.remove(syncVersionKey(accountId))
                preferences.remove(pendingDeletesKey(accountId))
                preferences.remove(pendingUpsertsKey(accountId))
            }
        }
    }

    suspend fun cloudSyncVersion(accountId: String): Long {
        return context.dataStore.data
            .map { preferences -> preferences[syncVersionKey(accountId)]?.toLongOrNull() ?: 0L }
            .first()
    }

    suspend fun setCloudSyncVersion(accountId: String, version: Long) {
        context.dataStore.edit { preferences ->
            preferences[syncVersionKey(accountId)] = version.toString()
        }
    }

    suspend fun clearCloudSyncVersion(accountId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(syncVersionKey(accountId))
            preferences.remove(pendingDeletesKey(accountId))
            preferences.remove(pendingUpsertsKey(accountId))
        }
    }

    suspend fun addCloudPendingDelete(accountId: String, type: String, id: String) {
        context.dataStore.edit { preferences ->
            val key = pendingDeletesKey(accountId)
            val values = preferences[key].orEmpty().toMutableSet()
            values += "$type:$id"
            preferences[key] = values
        }
    }

    suspend fun addCloudPendingUpsert(accountId: String, type: String, id: String) {
        context.dataStore.edit { preferences ->
            val key = pendingUpsertsKey(accountId)
            val values = preferences[key].orEmpty().toMutableSet()
            values += "$type:$id"
            preferences[key] = values
        }
    }

    suspend fun cloudPendingUpserts(accountId: String): Set<String> {
        return context.dataStore.data
            .map { preferences -> preferences[pendingUpsertsKey(accountId)].orEmpty() }
            .first()
    }

    suspend fun removeCloudPendingUpsert(accountId: String, type: String, id: String) {
        context.dataStore.edit { preferences ->
            val key = pendingUpsertsKey(accountId)
            val values = preferences[key].orEmpty().toMutableSet()
            values.remove("$type:$id")
            if (values.isEmpty()) preferences.remove(key) else preferences[key] = values
        }
    }

    suspend fun cloudPendingDeletes(accountId: String): Set<String> {
        return context.dataStore.data
            .map { preferences -> preferences[pendingDeletesKey(accountId)].orEmpty() }
            .first()
    }

    suspend fun removeCloudPendingDelete(accountId: String, type: String, id: String) {
        context.dataStore.edit { preferences ->
            val key = pendingDeletesKey(accountId)
            val values = preferences[key].orEmpty().toMutableSet()
            values.remove("$type:$id")
            if (values.isEmpty()) preferences.remove(key) else preferences[key] = values
        }
    }

    private fun syncVersionKey(accountId: String) =
        stringPreferencesKey("cloud_sync_version_$accountId")

    private fun pendingDeletesKey(accountId: String) =
        stringSetPreferencesKey("cloud_pending_deletes_$accountId")

    private fun pendingUpsertsKey(accountId: String) =
        stringSetPreferencesKey("cloud_pending_upserts_$accountId")
}
