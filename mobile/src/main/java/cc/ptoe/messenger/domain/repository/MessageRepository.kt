package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getByConversationId(conversationId: String): Flow<List<Message>>
    suspend fun getByConversationIds(conversationIds: List<String>): List<Message>
    suspend fun insert(message: Message)
    suspend fun insertAll(messages: List<Message>)
    suspend fun update(message: Message)
    suspend fun delete(id: String)
    suspend fun deleteByConversationId(conversationId: String)
    suspend fun getConversationIdById(id: String): String?
}
