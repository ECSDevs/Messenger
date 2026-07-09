package cc.ptoe.messenger.data

import org.json.JSONArray
import org.json.JSONObject

data class WearAgent(
    val id: String,
    val name: String,
    val avatar: String?,
    val isDefault: Boolean,
    val isReady: Boolean
)

enum class WearMessageRole {
    USER,
    ASSISTANT
}

data class WearChatMessage(
    val id: String,
    val role: WearMessageRole,
    val content: String,
    val timestamp: Long,
    val isPending: Boolean = false,
    val isError: Boolean = false
)

data class WearChatResponse(
    val requestId: String,
    val content: String?,
    val error: String?
)

data class WearOutgoingMessage(
    val role: WearMessageRole,
    val content: String
)

internal object WearSyncProtocol {
    const val AGENTS_REQUEST_PATH = "/messenger/wear/agents/request"
    const val AGENTS_RESPONSE_PATH = "/messenger/wear/agents/response"
    const val CHAT_REQUEST_PATH = "/messenger/wear/chat/request"
    const val CHAT_RESPONSE_PATH = "/messenger/wear/chat/response"

    fun encodeChatRequest(
        requestId: String,
        agentId: String,
        history: List<WearOutgoingMessage>
    ): ByteArray {
        val payload = JSONObject().apply {
            put("requestId", requestId)
            put("agentId", agentId)
            put(
                "history",
                JSONArray().apply {
                    history.forEach { message ->
                        put(
                            JSONObject().apply {
                                put(
                                    "role",
                                    when (message.role) {
                                        WearMessageRole.USER -> "user"
                                        WearMessageRole.ASSISTANT -> "assistant"
                                    }
                                )
                                put("content", message.content)
                            }
                        )
                    }
                }
            )
        }
        return payload.toString().encodeToByteArray()
    }

    fun decodeAgents(payload: ByteArray): List<WearAgent> {
        val root = JSONObject(payload.decodeToString())
        val agents = root.optJSONArray("agents") ?: return emptyList()
        return buildList(agents.length()) {
            repeat(agents.length()) { index ->
                val item = agents.getJSONObject(index)
                add(
                    WearAgent(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        avatar = item.optString("avatar").takeIf { it.isNotBlank() },
                        isDefault = item.optBoolean("isDefault"),
                        isReady = item.optBoolean("isReady")
                    )
                )
            }
        }
    }

    fun decodeChatResponse(payload: ByteArray): WearChatResponse {
        val root = JSONObject(payload.decodeToString())
        return WearChatResponse(
            requestId = root.optString("requestId"),
            content = root.optString("content").takeIf { it.isNotBlank() },
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
                        put("avatar", agent.avatar)
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
                        avatar = item.optString("avatar").takeIf { it.isNotBlank() },
                        isDefault = item.optBoolean("isDefault"),
                        isReady = item.optBoolean("isReady")
                    )
                )
            }
        }
    }

    fun encodeMessages(history: Map<String, List<WearChatMessage>>): String {
        return JSONObject().apply {
            history.forEach { (agentId, messages) ->
                put(
                    agentId,
                    JSONArray().apply {
                        messages.forEach { message ->
                            put(
                                JSONObject().apply {
                                    put("id", message.id)
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
            val agentId = keys.next()
            val array = root.optJSONArray(agentId) ?: continue
            result[agentId] = buildList(array.length()) {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        WearChatMessage(
                            id = item.optString("id"),
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
}
