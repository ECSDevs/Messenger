package cc.ptoe.messenger.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val providerId: String,
    val agentId: String,
    val overrideModelId: String? = null,
    val overrideTemperature: Float? = null,
    val overrideTopP: Float? = null,
    val overrideMaxTokens: Int? = null,
    val overrideReasoningEffort: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String?,
    val reasoningFormat: String? = null
)
