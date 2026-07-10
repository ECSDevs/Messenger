package cc.ptoe.messenger.data.wear

import android.util.Log
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.domain.model.Provider
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MobileWearSyncManager(
    private val app: MessengerApplication,
    private val scope: CoroutineScope
) {
    private val dataClient = Wearable.getDataClient(app)
    private var syncJob: Job? = null

    fun start() {
        if (syncJob != null) return
        syncJob = scope.launch {
            combine(
                app.agentRepository.getAll(),
                app.conversationRepository.getAll(),
                app.appPreferences.userAvatar,
                app.modelRepository.getAll(),
                app.providerRepository.getAll()
            ) { agents, conversations, userAvatar, _, _ ->
                SyncSnapshotInput(agents, conversations, userAvatar)
            }.collectLatest { input ->
                delay(350)
                runCatching { pushSnapshot(input) }
                    .onFailure { Log.w(TAG, "Wear DataLayer sync failed", it) }
            }
        }
    }

    suspend fun pushNow() {
        val input = SyncSnapshotInput(
            agents = app.agentRepository.getAll().first(),
            conversations = app.conversationRepository.getAll().first(),
            userAvatarPath = app.appPreferences.userAvatar.first()
        )
        pushSnapshot(input)
    }

    private suspend fun pushSnapshot(input: SyncSnapshotInput) {
        val agents = input.agents
            .sortedWith(compareByDescending<Agent> { it.isDefault }.thenBy { it.name.lowercase() })
        val conversations = input.conversations
            .sortedByDescending { it.updatedAt }
            .take(MAX_CONVERSATIONS)

        val agentsJson = JSONArray().apply {
            agents.forEach { agent ->
                put(
                    JSONObject().apply {
                        put("id", agent.id)
                        put("name", agent.name)
                        put("isDefault", agent.isDefault)
                        put(
                            "isReady",
                            resolveAgent(agent)?.let { resolveModelAndProvider(it) != null } ?: false
                        )
                        put("hasAvatar", !agent.avatar.isNullOrBlank() && File(agent.avatar).exists())
                    }
                )
            }
        }

        val conversationsJson = JSONArray().apply {
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
        }

        val messagesJson = JSONObject()
        conversations.forEach { conversation ->
            val messages = app.messageRepository.getByConversationId(conversation.id).first()
                .filter { it.role != MessageRole.SYSTEM }
                .takeLast(MAX_MESSAGES_PER_CONVERSATION)
            messagesJson.put(
                conversation.id,
                JSONArray().apply {
                    messages.forEach { message ->
                        put(
                            JSONObject().apply {
                                put("id", message.id)
                                put("conversationId", message.conversationId)
                                put(
                                    "role",
                                    when (message.role) {
                                        MessageRole.ASSISTANT -> "assistant"
                                        else -> "user"
                                    }
                                )
                                put("content", message.content)
                                put("timestamp", message.timestamp)
                                put(
                                    "isError",
                                    message.status == MessageStatus.ERROR
                                )
                                put(
                                    "isPending",
                                    message.status == MessageStatus.SENDING
                                )
                            }
                        )
                    }
                }
            )
        }

        val request = PutDataMapRequest.create(WearSyncProtocol.STATE_PATH).apply {
            dataMap.putString(WearSyncProtocol.KEY_AGENTS, agentsJson.toString())
            dataMap.putString(WearSyncProtocol.KEY_CONVERSATIONS, conversationsJson.toString())
            dataMap.putString(WearSyncProtocol.KEY_MESSAGES, messagesJson.toString())
            dataMap.putLong(WearSyncProtocol.KEY_UPDATED_AT, System.currentTimeMillis())

            val userAvatarFile = input.userAvatarPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() }
            if (userAvatarFile != null) {
                dataMap.putAsset(
                    WearSyncProtocol.KEY_USER_AVATAR,
                    Asset.createFromBytes(userAvatarFile.readBytes())
                )
            }

            agents.forEach { agent ->
                val assetKey = WearSyncProtocol.agentAvatarKey(agent.id)
                val avatarFile = agent.avatar
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.takeIf { it.exists() }
                if (avatarFile != null) {
                    dataMap.putAsset(assetKey, Asset.createFromBytes(avatarFile.readBytes()))
                }
            }
        }

        dataClient.putDataItem(request.asPutDataRequest().setUrgent()).awaitTask()
    }

    private suspend fun resolveAgent(agent: Agent): Agent? {
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

    private suspend fun resolveModelAndProvider(agent: Agent): Pair<Provider, ChatModel>? {
        val enabledModels = app.modelRepository.getAll().first().filter { it.isEnabled }
        if (enabledModels.isEmpty()) return null
        val model = agent.defaultModelId?.let { modelId ->
            enabledModels.firstOrNull { it.id == modelId }
        } ?: enabledModels.firstOrNull() ?: return null
        val provider = app.providerRepository.getById(model.providerId).first() ?: return null
        return provider to model
    }

    private data class SyncSnapshotInput(
        val agents: List<Agent>,
        val conversations: List<Conversation>,
        val userAvatarPath: String?
    )

    companion object {
        private const val TAG = "MobileWearSync"
        private const val MAX_CONVERSATIONS = 30
        private const val MAX_MESSAGES_PER_CONVERSATION = 40
    }
}

private suspend fun <T> Task<T>.awaitTask(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}

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
}

class MobileWearChatHandler(private val app: MessengerApplication) {

    suspend fun handleChatRequest(payloadBytes: ByteArray): ByteArray {
        val request = JSONObject(payloadBytes.decodeToString())
        val requestId = request.optString("requestId")
        val response = JSONObject().apply { put("requestId", requestId) }

        try {
            val conversationId = request.optString("conversationId")
            val text = request.optString("text").trim()
            if (conversationId.isBlank()) {
                throw IllegalArgumentException("Conversation is required.")
            }
            if (text.isBlank()) {
                throw IllegalArgumentException("Message is empty.")
            }

            val conversation = app.conversationRepository.getById(conversationId).first()
                ?: throw IllegalStateException("Conversation not found on phone.")
            val agent = app.agentRepository.getById(conversation.agentId).first()
                ?: throw IllegalStateException("Agent not found on phone.")
            val resolvedAgent = resolveAgent(agent, conversation)
            if (resolvedAgent.defaultModelId == null) {
                throw IllegalStateException("This agent has no model set. Configure it on your phone first.")
            }
            val activeModel = resolveModelAndProvider(resolvedAgent)
                ?: throw IllegalStateException("No enabled model is available for this agent.")

            val now = System.currentTimeMillis()
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = MessageRole.USER,
                content = text,
                timestamp = now,
                status = MessageStatus.SENT
            )
            app.messageRepository.insert(userMessage)

            val history = app.messageRepository.getByConversationId(conversationId).first()
                .filter {
                    it.role != MessageRole.SYSTEM &&
                        it.status != MessageStatus.ERROR &&
                        it.status != MessageStatus.SENDING
                }

            val reply = app.apiRepository.createChatCompletion(
                provider = activeModel.first,
                modelId = activeModel.second.modelId,
                messages = history,
                systemPrompt = resolvedAgent.systemPrompt,
                temperature = resolvedAgent.temperature,
                topP = resolvedAgent.topP,
                maxTokens = resolvedAgent.maxTokens
            )

            val assistantMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = reply.content,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT
            )
            app.messageRepository.insert(assistantMessage)
            app.conversationRepository.update(
                conversation.copy(
                    lastMessage = reply.content,
                    updatedAt = assistantMessage.timestamp
                )
            )

            response.put("content", reply.content)
            response.put("userMessageId", userMessage.id)
            response.put("assistantMessageId", assistantMessage.id)
        } catch (e: Exception) {
            response.put("error", e.message ?: "Unknown error")
        }

        return response.toString().encodeToByteArray()
    }

    suspend fun handleNewConversation(payloadBytes: ByteArray): ByteArray {
        val request = JSONObject(payloadBytes.decodeToString())
        val requestId = request.optString("requestId")
        val response = JSONObject().apply { put("requestId", requestId) }

        try {
            val agentId = request.optString("agentId").ifBlank { null }
            val agent = if (agentId != null) {
                app.agentRepository.getById(agentId).first()
            } else {
                app.agentRepository.getAll().first().firstOrNull { it.isDefault }
                    ?: app.agentRepository.getAll().first().firstOrNull()
            } ?: throw IllegalStateException("No agents available on phone.")

            val now = System.currentTimeMillis()
            val providerId = determineProviderId(agent)
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                title = "新对话",
                providerId = providerId,
                agentId = agent.id,
                createdAt = now,
                updatedAt = now,
                lastMessage = null
            )
            app.conversationRepository.insert(conversation)
            response.put("conversationId", conversation.id)
            response.put("agentId", agent.id)
        } catch (e: Exception) {
            response.put("error", e.message ?: "Unknown error")
        }

        return response.toString().encodeToByteArray()
    }

    private suspend fun resolveAgent(agent: Agent, conversation: Conversation): Agent {
        val withDefault = if (agent.isDefault) {
            agent
        } else {
            val defaultAgent = app.agentRepository.getAll().first().firstOrNull { it.isDefault }
                ?: agent
            agent.copy(
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
        return withDefault.copy(
            defaultModelId = conversation.overrideModelId ?: withDefault.defaultModelId,
            temperature = conversation.overrideTemperature ?: withDefault.temperature,
            topP = conversation.overrideTopP ?: withDefault.topP,
            maxTokens = conversation.overrideMaxTokens ?: withDefault.maxTokens
        )
    }

    private suspend fun resolveModelAndProvider(agent: Agent): Pair<Provider, ChatModel>? {
        val enabledModels = app.modelRepository.getAll().first().filter { it.isEnabled }
        if (enabledModels.isEmpty()) return null
        val model = agent.defaultModelId?.let { modelId ->
            enabledModels.firstOrNull { it.id == modelId }
        } ?: enabledModels.firstOrNull() ?: return null
        val provider = app.providerRepository.getById(model.providerId).first() ?: return null
        return provider to model
    }

    private suspend fun determineProviderId(agent: Agent): String {
        val resolved = resolveAgent(
            agent,
            Conversation(
                id = "",
                title = "",
                providerId = "",
                agentId = agent.id,
                createdAt = 0L,
                updatedAt = 0L,
                lastMessage = null
            )
        )
        return resolveModelAndProvider(resolved)?.second?.providerId.orEmpty()
    }
}
