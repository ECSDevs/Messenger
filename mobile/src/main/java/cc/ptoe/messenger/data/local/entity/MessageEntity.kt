package cc.ptoe.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    /**
     * Plain-text projection. For multimodal messages this is the
     * concatenation of text parts only — image data is not inlined here.
     */
    val content: String,
    /**
     * JSON-encoded [ContentPart] list. Stored as text for forward
     * compatibility (new part types don't require a schema bump);
     * [MessageRepositoryImpl] handles the (de)serialization so callers
     * see the typed [cc.ptoe.messenger.domain.model.Message.parts]
     * list directly.
     *
     * Empty / null means the message only had text and [content] is
     * authoritative.
     */
    val partsJson: String? = null,
    val timestamp: Long,
    val status: String,
    val errorMessage: String? = null
)
