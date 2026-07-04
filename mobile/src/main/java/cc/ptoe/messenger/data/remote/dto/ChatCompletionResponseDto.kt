package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatCompletionResponseDto(
    @SerializedName("id") val id: String,
    @SerializedName("choices") val choices: List<ChatChoiceDto>,
    @SerializedName("model") val model: String,
    @SerializedName("usage") val usage: UsageDto? = null
)
