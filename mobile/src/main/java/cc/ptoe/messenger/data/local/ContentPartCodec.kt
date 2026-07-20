package cc.ptoe.messenger.data.local

import cc.ptoe.messenger.domain.model.ContentPart
import cc.ptoe.messenger.domain.model.MessageImage
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

/**
 * (De)serialization helpers for the `parts` column on [MessageEntity].
 * Centralized here so the wire format and the [ContentPart] model stay
 * in lockstep — every read/write path uses the same JSON shape and the
 * same Gson instance.
 *
 * The encoded form is a JSON array of discriminated objects:
 *
 *   `[{"type":"text","text":"..."},{"type":"image","dataUri":"...","localPath":"..."}]`
 *
 * We intentionally use our own `type` strings ("text" / "image") rather
 * than the OpenAI ones ("text" / "image_url") because the local model
 * carries the bitmap blob, not a URL. The translation to OpenAI's wire
 * shape happens in [cc.ptoe.messenger.data.repository.ApiRepositoryImpl].
 */
object ContentPartCodec {

    private val gson = Gson()
    private val listType = object : TypeToken<List<StoredContentPart>>() {}.type

    fun encode(parts: List<ContentPart>): String? {
        if (parts.isEmpty()) return null
        val stored = parts.map { it.toStored() }
        return gson.toJson(stored)
    }

    fun decode(json: String?): List<ContentPart> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val parsed: List<StoredContentPart> = gson.fromJson(json, listType)
            parsed.mapNotNull { it.toDomain() }
        } catch (_: Exception) {
            // A malformed entry (e.g. a payload from a future build) must
            // never crash the message list — we just degrade to empty
            // parts and let the text projection stand on its own.
            emptyList()
        }
    }

    private fun ContentPart.toStored(): StoredContentPart = when (this) {
        is ContentPart.Text -> StoredContentPart(type = "text", text = text)
        is ContentPart.Image -> StoredContentPart(
            type = "image",
            dataUri = image.dataUri,
            localPath = image.localPath
        )
    }

    private fun StoredContentPart.toDomain(): ContentPart? = when (type) {
        "text" -> text?.let { ContentPart.Text(it) }
        "image" -> {
            if (dataUri.isNullOrEmpty() || localPath.isNullOrEmpty()) null
            else ContentPart.Image(MessageImage(dataUri = dataUri, localPath = localPath))
        }
        else -> null
    }

    /**
     * Mirror of [ContentPart] used purely for Gson (de)serialization.
     * Kept private so the domain model stays free of [SerializedName]
     * annotations.
     */
    private data class StoredContentPart(
        val type: String,
        val text: String? = null,
        val dataUri: String? = null,
        val localPath: String? = null
    )

    /**
     * Returns the raw JSON array form of [parts] if there is at least
     * one element, or a top-level [JsonObject] holding the concatenated
     * text otherwise. Used by the cloud sync layer to embed parts into
     * the message document without depending on the DTO's own shape.
     */
    fun encodeRawArray(parts: List<ContentPart>): JsonArray? {
        if (parts.isEmpty()) return null
        return JsonArray().apply {
            parts.forEach { part ->
                val obj = JsonObject()
                when (part) {
                    is ContentPart.Text -> {
                        obj.addProperty("type", "text")
                        obj.addProperty("text", part.text)
                    }
                    is ContentPart.Image -> {
                        obj.addProperty("type", "image")
                        obj.addProperty("dataUri", part.image.dataUri)
                        obj.addProperty("localPath", part.image.localPath)
                    }
                }
                add(obj)
            }
        }
    }
}
