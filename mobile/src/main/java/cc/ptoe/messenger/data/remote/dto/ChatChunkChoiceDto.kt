package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatChunkChoiceDto(
    @SerializedName("index") val index: Int,
    @SerializedName("delta") val delta: ChatDeltaDto,
    @SerializedName("finish_reason") val finishReason: String? = null
)
