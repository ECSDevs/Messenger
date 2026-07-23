/*
 * Copyright 2026 ECSDevs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    val reasoningEffort: String? = null,
    val isDefault: Boolean,
    val followDefaultSystemPrompt: Boolean,
    val followDefaultModel: Boolean,
    val followDefaultTemperature: Boolean,
    val followDefaultTopP: Boolean,
    val followDefaultMaxTokens: Boolean,
    val followDefaultReasoningEffort: Boolean = false,
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
    /**
     * JSON-encoded [cc.ptoe.messenger.domain.model.ContentPart] list,
     * identical in shape to the local [cc.ptoe.messenger.data.local.entity.MessageEntity.partsJson]
     * column. Null for text-only messages and for documents from
     * pre-multimodal server builds.
     */
    val partsJson: String? = null,
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
    val overrideReasoningEffort: String? = null,
    val reasoningFormat: String? = null,
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

/**
 * /api/sync?collection=... 分页响应。documents 字段是异构数组,
 * 由三个具体类型 Page DTO(CloudSyncAgentsPage / CloudSyncConversationsPage /
 * CloudSyncProvidersPage)分别承接,Gson 按方法的泛型签名反序列化。
 */
data class CloudSyncAgentsPage(
    val collection: String = "agents",
    val documents: List<CloudAgentDocument> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val latestVersion: Long
)

data class CloudSyncConversationsPage(
    val collection: String = "conversations",
    val documents: List<CloudConversationDocument> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val latestVersion: Long
)

data class CloudSyncProvidersPage(
    val collection: String = "providers",
    val documents: List<CloudProviderDocument> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
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
    val reasoningEffort: String? = null,
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
