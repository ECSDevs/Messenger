package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.local.AppPreferences
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.CurrentAgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CurrentAgentRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val agentRepository: AgentRepository
) : CurrentAgentRepository {

    override val currentAgentId: Flow<String?>
        get() = appPreferences.currentAgentId

    override val currentAgent: Flow<Agent?>
        get() = appPreferences.currentAgentId
            .combine(agentRepository.getAll()) { currentId, agents ->
                agents.find { it.id == currentId } ?: agents.firstOrNull()
            }

    override suspend fun setCurrentAgentId(agentId: String?) {
        appPreferences.setCurrentAgentId(agentId)
    }
}
