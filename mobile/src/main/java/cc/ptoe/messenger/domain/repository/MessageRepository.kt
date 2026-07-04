package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getByConversationId(conversationId: String): Flow<List<Message>>
    suspend fun insert(message: Message)
    suspend fun update(message: Message)
    suspend fun delete(id: String)
    suspend fun deleteByConversationId(conversationId: String)
}
