package cc.ptoe.messenger.data.remote.sse

sealed class ChatStreamEvent {
    data class Content(val text: String) : ChatStreamEvent()
    data class Done(val finishReason: String?) : ChatStreamEvent()
    data class Error(val message: String) : ChatStreamEvent()
}
