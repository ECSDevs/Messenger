package cc.ptoe.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import cc.ptoe.messenger.data.local.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models")
    fun getAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId")
    fun getByProviderId(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId AND isEnabled = 1")
    fun getEnabledByProviderId(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    fun getById(id: String): Flow<ModelEntity?>

    @Upsert
    suspend fun insert(model: ModelEntity)

    @Upsert
    suspend fun insertAll(models: List<ModelEntity>)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteByProviderId(providerId: String)

    @Update
    suspend fun update(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM models")
    suspend fun getAllEntities(): List<ModelEntity>

    @Query("DELETE FROM models")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM models")
    suspend fun count(): Int

    @Query("UPDATE models SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)
}
