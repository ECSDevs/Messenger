package cc.ptoe.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cc.ptoe.messenger.data.local.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers")
    fun getAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    fun getById(id: String): Flow<ProviderEntity?>

    @Insert
    suspend fun insert(provider: ProviderEntity)

    @Update
    suspend fun update(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)
}
