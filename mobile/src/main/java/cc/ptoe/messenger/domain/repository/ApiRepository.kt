package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.data.remote.sse.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

interface ApiRepository {

    suspend fun fetchModels(provider: Provider): List<ChatModel>

    fun streamChatCompletion(
        provider: Provider,
        modelId: String,
        messages: List<Message>,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxTokens: Int?
    ): Flow<ChatStreamEvent>

    suspend fun createChatCompletion(
        provider: Provider,
        modelId: String,
        messages: List<Message>,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxTokens: Int?
    ): Message
}
