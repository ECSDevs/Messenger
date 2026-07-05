package cc.ptoe.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatar: String? = null,
    val systemPrompt: String,
    val defaultModelId: String?,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int?,
    val isDefault: Boolean = false,
    val followDefaultSystemPrompt: Boolean = false,
    val followDefaultModel: Boolean = false,
    val followDefaultTemperature: Boolean = false,
    val followDefaultTopP: Boolean = false,
    val followDefaultMaxTokens: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
