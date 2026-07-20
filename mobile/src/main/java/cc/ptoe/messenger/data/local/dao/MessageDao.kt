package cc.ptoe.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import cc.ptoe.messenger.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getByConversationId(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId IN (:conversationIds) ORDER BY conversationId, timestamp ASC")
    suspend fun getByConversationIds(conversationIds: List<String>): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT conversationId FROM messages WHERE id = :id")
    suspend fun getConversationIdById(id: String): String?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllEntities(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}
