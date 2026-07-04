package cc.ptoe.messenger.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val errorMessage: String? = null
)

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}
