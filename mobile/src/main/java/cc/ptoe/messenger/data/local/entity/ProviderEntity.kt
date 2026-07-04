package cc.ptoe.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val createdAt: Long,
    val updatedAt: Long
)
