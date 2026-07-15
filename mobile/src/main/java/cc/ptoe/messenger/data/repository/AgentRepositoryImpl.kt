package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.local.dao.AgentDao
import cc.ptoe.messenger.data.local.entity.AgentEntity
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.repository.AgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AgentRepositoryImpl(
    private val agentDao: AgentDao,
    private val onChanged: (Agent?, Agent?) -> Unit = { _, _ -> }
) : AgentRepository {

    override fun getAll(): Flow<List<Agent>> {
        return agentDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getById(id: String): Flow<Agent?> {
        return agentDao.getById(id).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun insert(agent: Agent) {
        agentDao.insert(agent.toEntity())
        onChanged(null, agent)
    }

    override suspend fun update(agent: Agent) {
        val previous = agentDao.getById(agent.id).first()?.toDomain()
        agentDao.update(agent.toEntity())
        onChanged(previous, agent)
    }

    override suspend fun delete(id: String) {
        // 默认 Agent 不允许删除
        val agent = agentDao.getById(id).first()
        if (agent?.isDefault == true) return
        agentDao.delete(id)
        onChanged(agent?.toDomain(), null)
    }

    private fun AgentEntity.toDomain(): Agent {
        return Agent(
            id = id,
            name = name,
            avatar = avatar,
            systemPrompt = systemPrompt,
            defaultModelId = defaultModelId,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            isDefault = isDefault,
            followDefaultSystemPrompt = followDefaultSystemPrompt,
            followDefaultModel = followDefaultModel,
            followDefaultTemperature = followDefaultTemperature,
            followDefaultTopP = followDefaultTopP,
            followDefaultMaxTokens = followDefaultMaxTokens,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Agent.toEntity(): AgentEntity {
        return AgentEntity(
            id = id,
            name = name,
            avatar = avatar,
            systemPrompt = systemPrompt,
            defaultModelId = defaultModelId,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            isDefault = isDefault,
            followDefaultSystemPrompt = followDefaultSystemPrompt,
            followDefaultModel = followDefaultModel,
            followDefaultTemperature = followDefaultTemperature,
            followDefaultTopP = followDefaultTopP,
            followDefaultMaxTokens = followDefaultMaxTokens,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
