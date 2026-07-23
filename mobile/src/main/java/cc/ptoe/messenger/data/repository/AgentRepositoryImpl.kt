/*
 * Copyright 2026 ECSDevs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.local.dao.AgentDao
import cc.ptoe.messenger.data.local.entity.AgentEntity
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.repository.AgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class AgentRepositoryImpl(
    private val agentDao: AgentDao,
    private val onChanged: (Agent?, Agent?) -> Unit = { _, _ -> },
    private val avatarDirectory: File? = null
) : AgentRepository {

    override fun getAll(): Flow<List<Agent>> {
        return agentDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getById(id: String): Flow<Agent?> {
        return agentDao.getById(id).map { entity ->
            entity?.toDomain()
        }
    }

    override fun getDefaultAgent(): Flow<Agent?> {
        return agentDao.getDefaultAgent().map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun insert(agent: Agent) {
        agentDao.insert(agent.toEntity())
        onChanged(null, agent)
    }

    override suspend fun update(agent: Agent) {
        val previous = agentDao.getById(agent.id).first()?.toDomain()
        agentDao.update(agent.toEntity())
        onChanged(previous, agent)
    }

    override suspend fun clone(id: String): Agent? {
        val source = agentDao.getById(id).first()?.toDomain() ?: return null
        val existingNames = agentDao.getAllEntities().map { it.name }.toSet()
        val now = System.currentTimeMillis()
        val cloned = source.copy(
            id = UUID.randomUUID().toString(),
            name = uniqueCloneName(source.name, existingNames),
            avatar = copyAvatar(source.avatar),
            isDefault = false,
            marketAgentId = null,
            marketAgentVersion = null,
            marketAgentRole = null,
            createdAt = now,
            updatedAt = now
        )
        agentDao.insert(cloned.toEntity())
        onChanged(null, cloned)
        return cloned
    }

    private fun copyAvatar(avatar: String?): String? {
        val source = avatar?.let(::File)?.takeIf { it.isFile && it.length() > 0L } ?: return null
        val directory = (avatarDirectory ?: source.parentFile)?.apply { mkdirs() } ?: return null
        val extension = source.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val destination = File(directory, "${UUID.randomUUID()}$extension")
        return runCatching {
            source.copyTo(destination, overwrite = false)
            destination.absolutePath
        }.getOrNull()
    }

    override suspend fun delete(id: String) {
        // 默认 Agent 不允许删除
        val agent = agentDao.getById(id).first()
        if (agent?.isDefault == true) return
        agentDao.delete(id)
        onChanged(agent?.toDomain(), null)
    }

    private fun AgentEntity.toDomain(): Agent {
        return Agent(
            id = id,
            name = name,
            avatar = avatar,
            systemPrompt = systemPrompt,
            defaultModelId = defaultModelId,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            reasoningEffort = reasoningEffort,
            isDefault = isDefault,
            followDefaultSystemPrompt = followDefaultSystemPrompt,
            followDefaultModel = followDefaultModel,
            followDefaultTemperature = followDefaultTemperature,
            followDefaultTopP = followDefaultTopP,
            followDefaultMaxTokens = followDefaultMaxTokens,
            followDefaultReasoningEffort = followDefaultReasoningEffort,
            marketAgentId = marketAgentId,
            marketAgentVersion = marketAgentVersion,
            marketAgentRole = marketAgentRole,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Agent.toEntity(): AgentEntity {
        return AgentEntity(
            id = id,
            name = name,
            avatar = avatar,
            systemPrompt = systemPrompt,
            defaultModelId = defaultModelId,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            reasoningEffort = reasoningEffort,
            isDefault = isDefault,
            followDefaultSystemPrompt = followDefaultSystemPrompt,
            followDefaultModel = followDefaultModel,
            followDefaultTemperature = followDefaultTemperature,
            followDefaultTopP = followDefaultTopP,
            followDefaultMaxTokens = followDefaultMaxTokens,
            followDefaultReasoningEffort = followDefaultReasoningEffort,
            marketAgentId = marketAgentId,
            marketAgentVersion = marketAgentVersion,
            marketAgentRole = marketAgentRole,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun uniqueCloneName(sourceName: String, existingNames: Set<String>): String {
        val baseName = sourceName.trim().replace(CLONE_SUFFIX_PATTERN, "")
        val cloneNamePattern = Regex("^${Regex.escape(baseName)}（副本\\s*(\\d+)?）$")
        val usedCopyNumbers = existingNames.mapNotNull { name ->
            cloneNamePattern.matchEntire(name.trim())?.let { match ->
                match.groupValues[1].toIntOrNull() ?: 1
            }
        }.toSet()
        if (1 !in usedCopyNumbers) return "$baseName$CLONE_SUFFIX"

        var copyNumber = 2
        while (copyNumber in usedCopyNumbers) {
            copyNumber++
        }
        return "$baseName$CLONE_SUFFIX_WITH_NUMBER_PREFIX$copyNumber）"
    }

    private companion object {
        const val CLONE_SUFFIX = "（副本）"
        const val CLONE_SUFFIX_WITH_NUMBER_PREFIX = "（副本"
        val CLONE_SUFFIX_PATTERN = Regex("（副本\\s*\\d*）$")
    }
}
