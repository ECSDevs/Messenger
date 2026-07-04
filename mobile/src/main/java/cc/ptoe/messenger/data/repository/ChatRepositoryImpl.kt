package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.domain.repository.ChatRepository

class ChatRepositoryImpl : ChatRepository {
    override fun getAllConversations(): kotlinx.coroutines.flow.Flow<List<cc.ptoe.messenger.domain.model.Conversation>> {
        TODO("Not yet implemented")
    }

    override fun getMessagesByConversationId(conversationId: String): kotlinx.coroutines.flow.Flow<List<cc.ptoe.messenger.domain.model.Message>> {
        TODO("Not yet implemented")
    }

    override suspend fun createConversation(title: String, providerId: String): cc.ptoe.messenger.domain.model.Conversation {
        TODO("Not yet implemented")
    }

    override suspend fun sendMessage(conversationId: String, content: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteConversation(conversationId: String) {
        TODO("Not yet implemented")
    }
}
