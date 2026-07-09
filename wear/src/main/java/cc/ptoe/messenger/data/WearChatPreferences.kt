package cc.ptoe.messenger.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearChatDataStore by preferencesDataStore(name = "wear_chat")

class WearChatPreferences(private val context: Context) {

    private object Keys {
        val AGENTS = stringPreferencesKey("agents")
        val SELECTED_AGENT_ID = stringPreferencesKey("selected_agent_id")
        val MESSAGE_HISTORY = stringPreferencesKey("message_history")
    }

    val agents: Flow<List<WearAgent>> = context.wearChatDataStore.data.map { preferences ->
        WearChatJsonCodec.decodeAgents(preferences[Keys.AGENTS])
    }

    val selectedAgentId: Flow<String?> = context.wearChatDataStore.data.map { preferences ->
        preferences[Keys.SELECTED_AGENT_ID]
    }

    val messageHistory: Flow<Map<String, List<WearChatMessage>>> =
        context.wearChatDataStore.data.map { preferences ->
            WearChatJsonCodec.decodeMessages(preferences[Keys.MESSAGE_HISTORY])
        }

    suspend fun setAgents(agents: List<WearAgent>) {
        context.wearChatDataStore.edit { preferences ->
            preferences[Keys.AGENTS] = WearChatJsonCodec.encodeAgents(agents)
        }
    }

    suspend fun setSelectedAgentId(agentId: String?) {
        context.wearChatDataStore.edit { preferences ->
            if (agentId.isNullOrBlank()) {
                preferences.remove(Keys.SELECTED_AGENT_ID)
            } else {
                preferences[Keys.SELECTED_AGENT_ID] = agentId
            }
        }
    }

    suspend fun setMessageHistory(history: Map<String, List<WearChatMessage>>) {
        context.wearChatDataStore.edit { preferences ->
            preferences[Keys.MESSAGE_HISTORY] = WearChatJsonCodec.encodeMessages(history)
        }
    }
}
