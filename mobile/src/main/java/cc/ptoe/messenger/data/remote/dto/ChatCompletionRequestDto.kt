package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Thinking parameter for DeepSeek API compatibility.
 * When type is "disabled", the model will not output reasoning_content.
 */
data class ThinkingDto(
    @SerializedName("type") val type: String
)

data class ChatCompletionRequestDto(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessageDto>,
    @SerializedName("temperature") val temperature: Float? = null,
    @SerializedName("top_p") val topP: Float? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null,
    @SerializedName("thinking") val thinking: ThinkingDto? = null,
    @SerializedName("stream") val stream: Boolean = false
)
