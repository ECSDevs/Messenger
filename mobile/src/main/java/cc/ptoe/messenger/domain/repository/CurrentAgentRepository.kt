package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Agent
import kotlinx.coroutines.flow.Flow

interface CurrentAgentRepository {
    val currentAgentId: Flow<String?>
    val currentAgent: Flow<Agent?>
    suspend fun setCurrentAgentId(agentId: String?)
}
