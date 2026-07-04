package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatCompletionChunkDto(
    @SerializedName("id") val id: String,
    @SerializedName("choices") val choices: List<ChatChunkChoiceDto>,
    @SerializedName("model") val model: String
)
