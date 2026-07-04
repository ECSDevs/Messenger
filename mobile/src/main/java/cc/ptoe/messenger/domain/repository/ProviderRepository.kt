package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Provider
import kotlinx.coroutines.flow.Flow

interface ProviderRepository {
    fun getAll(): Flow<List<Provider>>
    fun getById(id: String): Flow<Provider?>
    suspend fun insert(provider: Provider)
    suspend fun update(provider: Provider)
    suspend fun delete(id: String)
}
