package cc.ptoe.messenger.domain.model

data class Agent(
    val id: String,
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
    val marketAgentId: String? = null,
    val marketAgentVersion: Long? = null,
    val marketAgentRole: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
