package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getAll(): Flow<List<Conversation>>
    fun getByAgentId(agentId: String): Flow<List<Conversation>>
    fun getById(id: String): Flow<Conversation?>
    suspend fun insert(conversation: Conversation)
    suspend fun update(conversation: Conversation)
    suspend fun updateLastMessage(id: String, lastMessage: String, updatedAt: Long)
    suspend fun delete(id: String)
}
