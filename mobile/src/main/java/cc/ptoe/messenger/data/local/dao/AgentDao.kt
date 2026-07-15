package cc.ptoe.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import cc.ptoe.messenger.data.local.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents")
    fun getAll(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    fun getById(id: String): Flow<AgentEntity?>

    @Upsert
    suspend fun insert(agent: AgentEntity)

    @Update
    suspend fun update(agent: AgentEntity)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM agents")
    suspend fun getAllEntities(): List<AgentEntity>

    @Query("DELETE FROM agents")
    suspend fun deleteAll()
}
