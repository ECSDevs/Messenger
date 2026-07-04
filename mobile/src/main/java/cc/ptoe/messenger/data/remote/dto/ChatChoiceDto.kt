package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatChoiceDto(
    @SerializedName("index") val index: Int,
    @SerializedName("message") val message: ChatMessageDto,
    @SerializedName("finish_reason") val finishReason: String? = null
)
