package cc.ptoe.messenger.data.cloud

import cc.ptoe.messenger.data.local.entity.AgentEntity
import cc.ptoe.messenger.data.local.entity.ConversationEntity
import cc.ptoe.messenger.data.local.entity.MessageEntity
import cc.ptoe.messenger.data.local.entity.ModelEntity
import cc.ptoe.messenger.data.local.entity.ProviderEntity

data class CloudUser(val id: String, val email: String, val createdAt: Long? = null, val lastLoginAt: Long? = null)
data class CloudManifest(val version: Int, val uploadedAt: Long, val recordCounts: CloudRecordCounts)
data class CloudRecordCounts(val providers: Int, val agents: Int, val conversations: Int, val messages: Int)
data class CloudDevice(val platform: String = "android", val appVersion: String? = null, val deviceName: String? = null)
data class CloudBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val device: CloudDevice,
    val providers: List<ProviderEntity>,
    val models: List<ModelEntity> = emptyList(),
    val agents: List<AgentEntity>,
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>
)
