package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.local.dao.MessageDao
import cc.ptoe.messenger.data.local.entity.MessageEntity
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val messageDao: MessageDao,
    private val onChanged: (String) -> Unit = {}
) : MessageRepository {

    override fun getByConversationId(conversationId: String): Flow<List<Message>> {
        return messageDao.getByConversationId(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insert(message: Message) {
        messageDao.insert(message.toEntity())
        onChanged(message.conversationId)
    }

    override suspend fun update(message: Message) {
        messageDao.update(message.toEntity())
        onChanged(message.conversationId)
    }

    override suspend fun delete(id: String) {
        val conversationId = messageDao.getAllEntities().firstOrNull { it.id == id }?.conversationId
        messageDao.delete(id)
        conversationId?.let(onChanged)
    }

    override suspend fun deleteByConversationId(conversationId: String) {
        messageDao.deleteByConversationId(conversationId)
        onChanged(conversationId)
    }

    private fun MessageEntity.toDomain(): Message {
        return Message(
            id = id,
            conversationId = conversationId,
            role = MessageRole.valueOf(role.uppercase()),
            content = content,
            timestamp = timestamp,
            status = MessageStatus.valueOf(status.uppercase()),
            errorMessage = errorMessage
        )
    }

    private fun Message.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role.name.lowercase(),
            content = content,
            timestamp = timestamp,
            status = status.name.lowercase(),
            errorMessage = errorMessage
        )
    }
}
