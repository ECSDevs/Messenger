package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.local.dao.ModelDao
import cc.ptoe.messenger.data.local.entity.ModelEntity
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ModelRepositoryImpl(
    private val modelDao: ModelDao,
    private val onChanged: (String, Boolean) -> Unit = { _, _ -> }
) : ModelRepository {

    override fun getAll(): Flow<List<ChatModel>> {
        return modelDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getByProviderId(providerId: String): Flow<List<ChatModel>> {
        return modelDao.getByProviderId(providerId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEnabledByProviderId(providerId: String): Flow<List<ChatModel>> {
        return modelDao.getEnabledByProviderId(providerId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getById(id: String): Flow<ChatModel?> {
        return modelDao.getById(id).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun insert(model: ChatModel) {
        modelDao.insert(model.toEntity())
        onChanged(model.providerId, false)
    }

    override suspend fun insertAll(models: List<ChatModel>) {
        modelDao.insertAll(models.map { it.toEntity() })
        models.firstOrNull()?.let { onChanged(it.providerId, false) }
    }

    override suspend fun update(model: ChatModel) {
        modelDao.update(model.toEntity())
        onChanged(model.providerId, false)
    }

    override suspend fun delete(id: String) {
        val providerId = modelDao.getById(id).first()?.providerId
        modelDao.delete(id)
        providerId?.let { onChanged(it, false) }
    }

    override suspend fun setEnabled(id: String, isEnabled: Boolean) {
        modelDao.setEnabled(id, isEnabled)
        modelDao.getById(id).first()?.let { onChanged(it.providerId, false) }
    }

    private fun ModelEntity.toDomain(): ChatModel {
        return ChatModel(
            id = id,
            providerId = providerId,
            modelId = modelId,
            displayName = displayName,
            isEnabled = isEnabled,
            createdAt = createdAt
        )
    }

    private fun ChatModel.toEntity(): ModelEntity {
        return ModelEntity(
            id = id,
            providerId = providerId,
            modelId = modelId,
            displayName = displayName,
            isEnabled = isEnabled,
            createdAt = createdAt
        )
    }
}
