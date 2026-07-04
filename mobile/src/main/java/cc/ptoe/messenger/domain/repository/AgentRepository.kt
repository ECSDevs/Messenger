package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Agent
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    fun getAll(): Flow<List<Agent>>
    fun getById(id: String): Flow<Agent?>
    suspend fun insert(agent: Agent)
    suspend fun update(agent: Agent)
    suspend fun delete(id: String)
}
