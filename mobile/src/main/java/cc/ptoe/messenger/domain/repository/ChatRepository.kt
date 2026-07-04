package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    fun getMessagesByConversationId(conversationId: String): Flow<List<Message>>
    suspend fun createConversation(title: String, providerId: String): Conversation
    suspend fun sendMessage(conversationId: String, content: String)
    suspend fun deleteConversation(conversationId: String)
}
