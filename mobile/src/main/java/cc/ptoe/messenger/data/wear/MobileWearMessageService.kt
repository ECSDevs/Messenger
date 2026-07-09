package cc.ptoe.messenger.data.wear

import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.domain.model.Provider
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MobileWearMessageService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncProtocol.AGENTS_REQUEST_PATH -> {
                serviceScope.launch {
                    sendAgents(messageEvent.sourceNodeId)
                }
            }
            WearSyncProtocol.CHAT_REQUEST_PATH -> {
                serviceScope.launch {
                    sendChatResponse(
                        nodeId = messageEvent.sourceNodeId,
                        payloadBytes = messageEvent.data
                    )
                }
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun sendAgents(nodeId: String) {
        val app = application as MessengerApplication
        val agents = app.agentRepository.getAll().first()
            .sortedWith(compareByDescending<Agent> { it.isDefault }.thenBy { it.name.lowercase() })

        val payload = JSONObject().apply {
            put(
                "agents",
                JSONArray().apply {
                    agents.forEach { agent ->
                        put(
                            JSONObject().apply {
                                put("id", agent.id)
                                put("name", agent.name)
                                put("avatar", agent.avatar)
                                put("isDefault", agent.isDefault)
                                put("isReady", resolveAgent(agent, app)?.let {
                                    resolveModelAndProvider(it, app) != null
                                } ?: false)
                            }
                        )
                    }
                }
            )
        }

        Wearable.getMessageClient(this)
            .sendMessage(
                nodeId,
                WearSyncProtocol.AGENTS_RESPONSE_PATH,
                payload.toString().encodeToByteArray()
            )
    }

    private suspend fun sendChatResponse(nodeId: String, payloadBytes: ByteArray) {
        val app = application as MessengerApplication
        val request = JSONObject(payloadBytes.decodeToString())
        val requestId = request.optString("requestId")
        val response = JSONObject().apply {
            put("requestId", requestId)
        }

        try {
            val agentId = request.optString("agentId")
            if (agentId.isBlank()) {
                throw IllegalArgumentException("Agent is required.")
            }

            val agent = app.agentRepository.getById(agentId).first()
                ?: throw IllegalStateException("Agent not found on phone.")
            val resolvedAgent = resolveAgent(agent, app)
                ?: throw IllegalStateException("Agent configuration is incomplete.")

            if (resolvedAgent.defaultModelId == null) {
                throw IllegalStateException("This agent has no model set. Configure it on your phone first.")
            }

            val activeModel = resolveModelAndProvider(resolvedAgent, app)
                ?: throw IllegalStateException("No enabled model is available for this agent.")
            val history = request.optJSONArray("history").toWearMessages()
            val reply = app.apiRepository.createChatCompletion(
                provider = activeModel.first,
                modelId = activeModel.second.modelId,
                messages = history,
                systemPrompt = resolvedAgent.systemPrompt,
                temperature = resolvedAgent.temperature,
                topP = resolvedAgent.topP,
                maxTokens = resolvedAgent.maxTokens
            )

            response.put("content", reply.content)
        } catch (e: Exception) {
            response.put("error", e.message ?: "Unknown error")
        }

        Wearable.getMessageClient(this)
            .sendMessage(
                nodeId,
                WearSyncProtocol.CHAT_RESPONSE_PATH,
                response.toString().encodeToByteArray()
            )
    }

    private suspend fun resolveAgent(
        agent: Agent,
        app: MessengerApplication
    ): Agent? {
        if (agent.isDefault) return agent

        val defaultAgent = app.agentRepository.getAll().first().firstOrNull { it.isDefault }
            ?: return agent

        return agent.copy(
            systemPrompt = if (agent.followDefaultSystemPrompt) {
                defaultAgent.systemPrompt
            } else {
                agent.systemPrompt
            },
            defaultModelId = if (agent.followDefaultModel) {
                defaultAgent.defaultModelId
            } else {
                agent.defaultModelId
            },
            temperature = if (agent.followDefaultTemperature) {
                defaultAgent.temperature
            } else {
                agent.temperature
            },
            topP = if (agent.followDefaultTopP) {
                defaultAgent.topP
            } else {
                agent.topP
            },
            maxTokens = if (agent.followDefaultMaxTokens) {
                defaultAgent.maxTokens
            } else {
                agent.maxTokens
            }
        )
    }

    private suspend fun resolveModelAndProvider(
        agent: Agent,
        app: MessengerApplication
    ): Pair<Provider, ChatModel>? {
        val enabledModels = app.modelRepository.getAll().first().filter { it.isEnabled }
        if (enabledModels.isEmpty()) return null

        val model = agent.defaultModelId?.let { modelId ->
            enabledModels.firstOrNull { it.id == modelId }
        } ?: enabledModels.firstOrNull()
            ?: return null

        val provider = app.providerRepository.getById(model.providerId).first() ?: return null
        return provider to model
    }

    private fun JSONArray?.toWearMessages(): List<Message> {
        if (this == null) return emptyList()

        return buildList(length()) {
            repeat(length()) { index ->
                val item = getJSONObject(index)
                val role = when (item.optString("role")) {
                    "assistant" -> MessageRole.ASSISTANT
                    else -> MessageRole.USER
                }
                add(
                    Message(
                        id = "wear-$index",
                        conversationId = "wear",
                        role = role,
                        content = item.optString("content"),
                        timestamp = System.currentTimeMillis() + index,
                        status = MessageStatus.SENT
                    )
                )
            }
        }
    }
}

private object WearSyncProtocol {
    const val AGENTS_REQUEST_PATH = "/messenger/wear/agents/request"
    const val AGENTS_RESPONSE_PATH = "/messenger/wear/agents/response"
    const val CHAT_REQUEST_PATH = "/messenger/wear/chat/request"
    const val CHAT_RESPONSE_PATH = "/messenger/wear/chat/response"
}
