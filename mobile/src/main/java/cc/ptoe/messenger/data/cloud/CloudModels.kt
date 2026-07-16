package cc.ptoe.messenger.data.cloud

import com.google.gson.annotations.SerializedName

data class CloudUser(
    val id: String,
    val email: String,
    val avatarUrl: String? = null,
    val avatarVersion: Long? = null,
    val syncVersion: Long = 0,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val lastLoginAt: Long? = null
)

data class CloudSyncResult(
    val latestVersion: Long,
    val agents: Int,
    val conversations: Int,
    val providers: Int
)

data class CloudLoginOutcome(
    val user: CloudUser,
    val hasLocalData: Boolean,
    val cloudVersion: Long
)

data class CloudAgentDocument(
    @SerializedName("_id") val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val avatarVersion: Long? = null,
    val systemPrompt: String,
    val defaultModelId: String? = null,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int? = null,
    val isDefault: Boolean,
    val followDefaultSystemPrompt: Boolean,
    val followDefaultModel: Boolean,
    val followDefaultTemperature: Boolean,
    val followDefaultTopP: Boolean,
    val followDefaultMaxTokens: Boolean,
    val marketAgentId: String? = null,
    val marketAgentVersion: Long? = null,
    val marketAgentRole: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val deleted: Boolean
)

data class CloudMessageDocument(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val errorMessage: String? = null
)

data class CloudConversationDocument(
    @SerializedName("_id") val id: String,
    val agentId: String,
    val title: String,
    val providerId: String,
    val overrideModelId: String? = null,
    val overrideTemperature: Double? = null,
    val overrideTopP: Double? = null,
    val overrideMaxTokens: Int? = null,
    val messages: List<CloudMessageDocument> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val deleted: Boolean
)

data class CloudModelDocument(
    val id: String,
    val modelId: String,
    val displayName: String,
    val isEnabled: Boolean,
    val createdAt: Long
)

data class CloudProviderDocument(
    @SerializedName("_id") val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<CloudModelDocument> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val deleted: Boolean
)

data class CloudSyncResponse(
    val agents: List<CloudAgentDocument> = emptyList(),
    val conversations: List<CloudConversationDocument> = emptyList(),
    val providers: List<CloudProviderDocument> = emptyList(),
    val latestVersion: Long
)

data class CloudUpsertResponse(
    val id: String,
    val version: Long
)

data class CloudAvatarResponse(
    val url: String?,
    val version: Long,
    val avatarVersion: Long? = null
)

data class CloudMarketAgent(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val avatarVersion: Long? = null,
    val systemPrompt: String,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long
)

data class CloudMarketAgentListResponse(
    val agents: List<CloudMarketAgent> = emptyList(),
    val nextCursor: String? = null
)

data class CloudMarketAgentResponse(
    val agent: CloudMarketAgent,
    val isOwner: Boolean = false
)

data class CloudMarketAgentUpdate(
    val agent: CloudMarketAgent,
    val hasUpdate: Boolean
)
