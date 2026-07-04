package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ModelDto(
    @SerializedName("id") val id: String,
    @SerializedName("object") val `object`: String,
    @SerializedName("created") val created: Long? = null,
    @SerializedName("owned_by") val ownedBy: String? = null
)
