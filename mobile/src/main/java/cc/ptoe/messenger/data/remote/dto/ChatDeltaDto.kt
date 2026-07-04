package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatDeltaDto(
    @SerializedName("role") val role: String? = null,
    @SerializedName("content") val content: String? = null
)
