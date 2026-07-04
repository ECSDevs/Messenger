package cc.ptoe.messenger.domain.model

data class ChatModel(
    val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val isEnabled: Boolean,
    val createdAt: Long
)
