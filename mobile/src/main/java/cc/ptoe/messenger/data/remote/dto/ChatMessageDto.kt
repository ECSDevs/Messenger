package cc.ptoe.messenger.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Chat completion message. The [content] field is rendered as either a
 * plain string (text-only messages) or an array of [ContentPartDto]
 * (multimodal messages). The shape follows the OpenAI Chat Completions
 * spec; providers that don't accept arrays still get a string because
 * [toRequestContent] picks the appropriate representation at request
 * build time.
 */
data class ChatMessageDto(
    @SerializedName("role") val role: String,
    /**
     * Raw JSON element so the same DTO can hold either a string or an
     * array. We don't deserialize it into a sealed class because the
     * server echoes it back unchanged and we want to stay forward
     * compatible with future part types.
     */
    @SerializedName("content") val content: JsonElement,
    @SerializedName("reasoning_content") val reasoningContent: String? = null
)
