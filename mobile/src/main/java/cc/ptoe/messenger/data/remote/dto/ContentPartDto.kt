package cc.ptoe.messenger.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * OpenAI-style multimodal content part. Only [type] = "text" and "image_url"
 * are produced by the client today; other variants are kept in the JSON
 * untouched so the server can pass them through.
 */
data class ContentPartDto(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrlDto? = null
)

data class ImageUrlDto(
    /**
     * Either an https:// URL or a data: URI. The wire format is
     * deliberately permissive; the client only ever emits data: URIs
     * because selected bitmaps are base64-encoded at send time.
     */
    @SerializedName("url") val url: String,
    /**
     * Optional detail hint (`auto` / `low` / `high`). We default to
     * "auto" and let the server choose.
     */
    @SerializedName("detail") val detail: String? = "auto"
)
