package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.ChatModel
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun getAll(): Flow<List<ChatModel>>
    fun getByProviderId(providerId: String): Flow<List<ChatModel>>
    fun getEnabledByProviderId(providerId: String): Flow<List<ChatModel>>
    fun getById(id: String): Flow<ChatModel?>
    suspend fun insert(model: ChatModel)
    suspend fun insertAll(models: List<ChatModel>)
    suspend fun update(model: ChatModel)
    suspend fun delete(id: String)
    suspend fun setEnabled(id: String, isEnabled: Boolean)
}
