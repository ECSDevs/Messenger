package cc.ptoe.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import cc.ptoe.messenger.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE agentId = :agentId ORDER BY updatedAt DESC")
    fun getByAgentId(agentId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getById(id: String): Flow<ConversationEntity?>

    @Upsert
    suspend fun insert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE agentId = :agentId")
    suspend fun deleteByAgentId(agentId: String)

    @Query("UPDATE conversations SET agentId = :newAgentId WHERE agentId = :oldAgentId")
    suspend fun updateAgentId(oldAgentId: String, newAgentId: String)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAllEntities(): List<ConversationEntity>

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
