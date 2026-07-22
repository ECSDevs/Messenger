package cc.ptoe.messenger.data.remote.sse

sealed class ChatStreamEvent {
    data class Content(val text: String) : ChatStreamEvent()
    data class Done(val finishReason: String?) : ChatStreamEvent()
    data class Error(val message: String) : ChatStreamEvent()
    /**
     * 表示流式响应中检测到了 `reasoning_content` 字段，
     * 调用方应将对话标注为 "reasoning_content" 格式，
     * 后续请求中需将 think 标签还原为 `reasoning_content` 字段。
     */
    data object ReasoningDetected : ChatStreamEvent()
}
