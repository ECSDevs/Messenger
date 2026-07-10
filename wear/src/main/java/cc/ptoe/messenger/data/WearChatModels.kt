package cc.ptoe.messenger.data

import org.json.JSONArray
import org.json.JSONObject

data class WearAgent(
    val id: String,
    val name: String,
    val avatarPath: String?,
    val isDefault: Boolean,
    val isReady: Boolean
)

data class WearConversation(
    val id: String,
    val title: String,
    val agentId: String,
    val lastMessage: String?,
    val updatedAt: Long,
    val createdAt: Long
)

enum class WearMessageRole {
    USER,
    ASSISTANT
}

data class WearChatMessage(
    val id: String,
    val conversationId: String,
    val role: WearMessageRole,
    val content: String,
    val timestamp: Long,
    val isPending: Boolean = false,
    val isError: Boolean = false
)

data class WearSyncSnapshot(
    val agents: List<WearAgent>,
    val conversations: List<WearConversation>,
    val messages: Map<String, List<WearChatMessage>>,
    val userAvatarPath: String?,
    val updatedAt: Long
)

data class WearChatResponse(
    val requestId: String,
    val content: String?,
    val error: String?,
    val userMessageId: String? = null,
    val assistantMessageId: String? = null
)

data class WearNewChatResponse(
    val requestId: String,
    val conversationId: String?,
    val agentId: String?,
    val error: String?
)

object WearSyncProtocol {
    const val STATE_PATH = "/messenger/sync/state"
    const val KEY_AGENTS = "agents"
    const val KEY_CONVERSATIONS = "conversations"
    const val KEY_MESSAGES = "messages"
    const val KEY_USER_AVATAR = "user_avatar"
    const val KEY_UPDATED_AT = "updated_at"
    const val CHAT_REQUEST_PATH = "/messenger/wear/chat/request"
    const val CHAT_RESPONSE_PATH = "/messenger/wear/chat/response"
    const val NEW_CHAT_REQUEST_PATH = "/messenger/wear/chat/new"
    const val NEW_CHAT_RESPONSE_PATH = "/messenger/wear/chat/new_response"

    fun agentAvatarKey(agentId: String): String = "agent_avatar_$agentId"

    fun encodeChatRequest(
        requestId: String,
        conversationId: String,
        text: String
    ): ByteArray {
        return JSONObject().apply {
            put("requestId", requestId)
            put("conversationId", conversationId)
            put("text", text)
        }.toString().encodeToByteArray()
    }

    fun encodeNewChatRequest(
        requestId: String,
        agentId: String?
    ): ByteArray {
        return JSONObject().apply {
            put("requestId", requestId)
            if (!agentId.isNullOrBlank()) {
                put("agentId", agentId)
            }
        }.toString().encodeToByteArray()
    }

    fun decodeChatResponse(payload: ByteArray): WearChatResponse {
        val root = JSONObject(payload.decodeToString())
        return WearChatResponse(
            requestId = root.optString("requestId"),
            content = root.optString("content").takeIf { it.isNotBlank() },
            error = root.optString("error").takeIf { it.isNotBlank() },
            userMessageId = root.optString("userMessageId").takeIf { it.isNotBlank() },
            assistantMessageId = root.optString("assistantMessageId").takeIf { it.isNotBlank() }
        )
    }

    fun decodeNewChatResponse(payload: ByteArray): WearNewChatResponse {
        val root = JSONObject(payload.decodeToString())
        return WearNewChatResponse(
            requestId = root.optString("requestId"),
            conversationId = root.optString("conversationId").takeIf { it.isNotBlank() },
            agentId = root.optString("agentId").takeIf { it.isNotBlank() },
            error = root.optString("error").takeIf { it.isNotBlank() }
        )
    }
}

internal object WearChatJsonCodec {
    fun encodeAgents(agents: List<WearAgent>): String {
        return JSONArray().apply {
            agents.forEach { agent ->
                put(
                    JSONObject().apply {
                        put("id", agent.id)
                        put("name", agent.name)
                        put("avatarPath", agent.avatarPath)
                        put("isDefault", agent.isDefault)
                        put("isReady", agent.isReady)
                    }
                )
            }
        }.toString()
    }

    fun decodeAgents(json: String?): List<WearAgent> {
        if (json.isNullOrBlank()) return emptyList()
        val agents = JSONArray(json)
        return buildList(agents.length()) {
            repeat(agents.length()) { index ->
                val item = agents.getJSONObject(index)
                add(
                    WearAgent(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        avatarPath = item.optString("avatarPath").takeIf { it.isNotBlank() }
                            ?: item.optString("avatar").takeIf { it.isNotBlank() },
                        isDefault = item.optBoolean("isDefault"),
                        isReady = item.optBoolean("isReady")
                    )
                )
            }
        }
    }

    fun encodeConversations(conversations: List<WearConversation>): String {
        return JSONArray().apply {
            conversations.forEach { conversation ->
                put(
                    JSONObject().apply {
                        put("id", conversation.id)
                        put("title", conversation.title)
                        put("agentId", conversation.agentId)
                        put("lastMessage", conversation.lastMessage)
                        put("updatedAt", conversation.updatedAt)
                        put("createdAt", conversation.createdAt)
                    }
                )
            }
        }.toString()
    }

    fun decodeConversations(json: String?): List<WearConversation> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    WearConversation(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        agentId = item.optString("agentId"),
                        lastMessage = item.optString("lastMessage").takeIf { it.isNotBlank() },
                        updatedAt = item.optLong("updatedAt"),
                        createdAt = item.optLong("createdAt")
                    )
                )
            }
        }
    }

    fun encodeMessages(history: Map<String, List<WearChatMessage>>): String {
        return JSONObject().apply {
            history.forEach { (conversationId, messages) ->
                put(
                    conversationId,
                    JSONArray().apply {
                        messages.forEach { message ->
                            put(
                                JSONObject().apply {
                                    put("id", message.id)
                                    put("conversationId", message.conversationId)
                                    put(
                                        "role",
                                        when (message.role) {
                                            WearMessageRole.USER -> "user"
                                            WearMessageRole.ASSISTANT -> "assistant"
                                        }
                                    )
                                    put("content", message.content)
                                    put("timestamp", message.timestamp)
                                    put("isPending", message.isPending)
                                    put("isError", message.isError)
                                }
                            )
                        }
                    }
                )
            }
        }.toString()
    }

    fun decodeMessages(json: String?): Map<String, List<WearChatMessage>> {
        if (json.isNullOrBlank()) return emptyMap()
        val root = JSONObject(json)
        val result = linkedMapOf<String, List<WearChatMessage>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val conversationId = keys.next()
            val array = root.optJSONArray(conversationId) ?: continue
            result[conversationId] = buildList(array.length()) {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        WearChatMessage(
                            id = item.optString("id"),
                            conversationId = item.optString("conversationId").ifBlank { conversationId },
                            role = if (item.optString("role") == "assistant") {
                                WearMessageRole.ASSISTANT
                            } else {
                                WearMessageRole.USER
                            },
                            content = item.optString("content"),
                            timestamp = item.optLong("timestamp"),
                            isPending = item.optBoolean("isPending"),
                            isError = item.optBoolean("isError")
                        )
                    )
                }
            }
        }
        return result
    }

    fun decodeAgentsFromSync(json: String, avatarPaths: Map<String, String?>): List<WearAgent> {
        if (json.isBlank()) return emptyList()
        val agents = JSONArray(json)
        return buildList(agents.length()) {
            repeat(agents.length()) { index ->
                val item = agents.getJSONObject(index)
                val id = item.optString("id")
                add(
                    WearAgent(
                        id = id,
                        name = item.optString("name"),
                        avatarPath = avatarPaths[id],
                        isDefault = item.optBoolean("isDefault"),
                        isReady = item.optBoolean("isReady")
                    )
                )
            }
        }
    }

    fun decodeConversationsFromSync(json: String): List<WearConversation> {
        return decodeConversations(json)
    }

    fun decodeMessagesFromSync(json: String): Map<String, List<WearChatMessage>> {
        return decodeMessages(json)
    }
}
