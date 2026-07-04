package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ModelsResponseDto(
    @SerializedName("data") val data: List<ModelDto>
)
