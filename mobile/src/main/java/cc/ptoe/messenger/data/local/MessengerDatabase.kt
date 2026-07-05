package cc.ptoe.messenger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cc.ptoe.messenger.data.local.dao.AgentDao
import cc.ptoe.messenger.data.local.dao.ConversationDao
import cc.ptoe.messenger.data.local.dao.MessageDao
import cc.ptoe.messenger.data.local.dao.ModelDao
import cc.ptoe.messenger.data.local.dao.ProviderDao
import cc.ptoe.messenger.data.local.entity.AgentEntity
import cc.ptoe.messenger.data.local.entity.ConversationEntity
import cc.ptoe.messenger.data.local.entity.MessageEntity
import cc.ptoe.messenger.data.local.entity.ModelEntity
import cc.ptoe.messenger.data.local.entity.ProviderEntity

@Database(
    entities = [
        ProviderEntity::class,
        ModelEntity::class,
        AgentEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MessengerDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun agentDao(): AgentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
