package cc.ptoe.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId")]
)
data class ModelEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val isEnabled: Boolean,
    val createdAt: Long
)
