package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.local.dao.ConversationDao
import cc.ptoe.messenger.data.local.entity.ConversationEntity
import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao
) : ConversationRepository {

    override fun getAll(): Flow<List<Conversation>> {
        return conversationDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getByAgentId(agentId: String): Flow<List<Conversation>> {
        return conversationDao.getByAgentId(agentId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getById(id: String): Flow<Conversation?> {
        return conversationDao.getById(id).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun insert(conversation: Conversation) {
        conversationDao.insert(conversation.toEntity())
    }

    override suspend fun update(conversation: Conversation) {
        conversationDao.update(conversation.toEntity())
    }

    override suspend fun delete(id: String) {
        conversationDao.delete(id)
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            id = id,
            title = title,
            providerId = providerId,
            agentId = agentId,
            overrideModelId = overrideModelId,
            overrideTemperature = overrideTemperature,
            overrideTopP = overrideTopP,
            overrideMaxTokens = overrideMaxTokens,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastMessage = lastMessage
        )
    }

    private fun Conversation.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            providerId = providerId,
            agentId = agentId,
            overrideModelId = overrideModelId,
            overrideTemperature = overrideTemperature,
            overrideTopP = overrideTopP,
            overrideMaxTokens = overrideMaxTokens,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastMessage = lastMessage
        )
    }
}
