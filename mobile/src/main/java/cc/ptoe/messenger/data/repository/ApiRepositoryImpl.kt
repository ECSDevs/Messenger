package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.remote.NetworkClient
import cc.ptoe.messenger.data.remote.dto.ChatCompletionRequestDto
import cc.ptoe.messenger.data.remote.dto.ChatMessageDto
import cc.ptoe.messenger.data.remote.sse.ChatStreamEvent
import cc.ptoe.messenger.data.remote.sse.ChatStreamParser
import cc.ptoe.messenger.data.remote.sse.SSEParser
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.domain.repository.ApiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import retrofit2.HttpException
import java.util.UUID

class ApiRepositoryImpl : ApiRepository {

    override suspend fun fetchModels(provider: Provider): List<ChatModel> {
        return try {
            val api = NetworkClient.createOpenAiApi(provider.baseUrl, provider.apiKey)
            val response = api.getModels()
            response.data.map { modelDto ->
                ChatModel(
                    id = UUID.randomUUID().toString(),
                    providerId = provider.id,
                    modelId = modelDto.id,
                    displayName = modelDto.id,
                    isEnabled = true,
                    createdAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            throw ApiException("Failed to fetch models: ${e.message}", e)
        }
    }

    override fun streamChatCompletion(
        provider: Provider,
        modelId: String,
        messages: List<Message>,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxTokens: Int?
    ): Flow<ChatStreamEvent> = flow {
        try {
            val api = NetworkClient.createOpenAiApi(provider.baseUrl, provider.apiKey)
            val requestMessages = buildRequestMessages(messages, systemPrompt)
            val request = ChatCompletionRequestDto(
                model = modelId,
                messages = requestMessages,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                stream = true
            )
            val responseBody = api.createChatCompletionStream(request)
            val sseFlow = SSEParser.parse(responseBody)
            val eventFlow = ChatStreamParser.parseToEvents(sseFlow)
            var hasFinished = false
            eventFlow.collect { event ->
                if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) {
                    hasFinished = true
                }
                emit(event)
            }
            if (!hasFinished) {
                emit(ChatStreamEvent.Error("API 未返回有效数据，请检查 API 配置和参数"))
            }
        } catch (e: HttpException) {
            val errorMessage = extractHttpErrorMessage(e)
            emit(ChatStreamEvent.Error(errorMessage))
        } catch (e: Exception) {
            emit(ChatStreamEvent.Error(e.message ?: "Unknown error"))
        }
    }

    override suspend fun createChatCompletion(
        provider: Provider,
        modelId: String,
        messages: List<Message>,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxTokens: Int?
    ): Message {
        return try {
            val api = NetworkClient.createOpenAiApi(provider.baseUrl, provider.apiKey)
            val requestMessages = buildRequestMessages(messages, systemPrompt)
            val request = ChatCompletionRequestDto(
                model = modelId,
                messages = requestMessages,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                stream = false
            )
            val response = api.createChatCompletion(request)
            val choice = response.choices.firstOrNull()
                ?: throw ApiException("No choices in response")
            Message(
                id = UUID.randomUUID().toString(),
                conversationId = "",
                role = MessageRole.ASSISTANT,
                content = choice.message.content,
                timestamp = System.currentTimeMillis(),
                status = cc.ptoe.messenger.domain.model.MessageStatus.SENT
            )
        } catch (e: HttpException) {
            throw ApiException(extractHttpErrorMessage(e), e)
        } catch (e: Exception) {
            throw ApiException("Failed to create chat completion: ${e.message}", e)
        }
    }

    private fun buildRequestMessages(
        messages: List<Message>,
        systemPrompt: String?
    ): List<ChatMessageDto> {
        val result = mutableListOf<ChatMessageDto>()
        if (!systemPrompt.isNullOrEmpty()) {
            result.add(ChatMessageDto(role = "system", content = systemPrompt))
        }
        result.addAll(messages.map { message ->
            ChatMessageDto(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                    MessageRole.TOOL -> "tool"
                },
                content = message.content
            )
        })
        return result
    }

    private fun extractHttpErrorMessage(e: HttpException): String {
        val code = e.code()
        val rawBody = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (rawBody.isNullOrEmpty()) {
            return "HTTP $code: ${e.message()}"
        }
        return try {
            val json = JSONObject(rawBody)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: rawBody
        } catch (_: Exception) {
            "HTTP $code: $rawBody"
        }
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
