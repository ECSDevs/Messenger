package cc.ptoe.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = AgentEntity::class,
            parentColumns = ["id"],
            childColumns = ["agentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("agentId")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val agentId: String,
    val overrideModelId: String? = null,
    val overrideTemperature: Float? = null,
    val overrideTopP: Float? = null,
    val overrideMaxTokens: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String?
)
