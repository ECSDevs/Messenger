package cc.ptoe.messenger.data.repository

import cc.ptoe.messenger.data.remote.NetworkClient
import cc.ptoe.messenger.data.remote.dto.ChatCompletionRequestDto
import cc.ptoe.messenger.data.remote.dto.ChatMessageDto
import cc.ptoe.messenger.data.remote.sse.ChatStreamEvent
import cc.ptoe.messenger.data.remote.sse.ChatStreamParser
import cc.ptoe.messenger.data.remote.sse.SSEParser
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.ContentPart
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.domain.repository.ApiRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import retrofit2.HttpException
import java.util.UUID

class ApiRepositoryImpl : ApiRepository {

    private val gson = Gson()

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
                content = extractResponseContent(choice.message.content),
                timestamp = System.currentTimeMillis(),
                status = cc.ptoe.messenger.domain.model.MessageStatus.SENT
            )
        } catch (e: HttpException) {
            throw ApiException(extractHttpErrorMessage(e), e)
        } catch (e: Exception) {
            throw ApiException("Failed to create chat completion: ${e.message}", e)
        }
    }

    /**
     * Build the request payload for the chat completion API.
     *
     * Pure-text messages are sent as a `content` string (the legacy
     * shape that every provider accepts). Multimodal messages are sent
     * as a `content` array using the OpenAI image_url / text parts so
     * vision-capable models can read the bitmap.
     *
     * The role string is converted here so the DTO stays independent of
     * the domain enum.
     */
    private fun buildRequestMessages(
        messages: List<Message>,
        systemPrompt: String?
    ): List<ChatMessageDto> {
        val result = mutableListOf<ChatMessageDto>()
        if (!systemPrompt.isNullOrEmpty()) {
            result.add(ChatMessageDto(role = "system", content = JsonPrimitive(systemPrompt)))
        }
        messages.forEach { message ->
            val role = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
                MessageRole.TOOL -> "tool"
            }
            val parts = message.parts
            if (message.hasImages && parts.any { it is ContentPart.Image }) {
                result.add(ChatMessageDto(role = role, content = buildMultipartContent(parts)))
            } else {
                // Text-only path: fall back to the legacy `content` string
                // so providers that don't accept arrays (or that mirror
                // OpenAI without vision) still get a working request.
                val text = if (parts.isNotEmpty()) {
                    parts.filterIsInstance<ContentPart.Text>().joinToString("\n") { it.text }
                } else {
                    message.content
                }
                result.add(ChatMessageDto(role = role, content = JsonPrimitive(text)))
            }
        }
        return result
    }

    /**
     * Translate the domain [ContentPart] list to the OpenAI multipart
     * shape. We always emit `image_url` parts (with the data: URI
     * captured at send time) because that's the only variant the spec
     * defines for vision inputs. Text segments pass through as `text`.
     */
    private fun buildMultipartContent(parts: List<ContentPart>): JsonArray {
        val array = JsonArray()
        parts.forEach { part ->
            val obj = JsonObject()
            when (part) {
                is ContentPart.Text -> {
                    obj.addProperty("type", "text")
                    obj.addProperty("text", part.text)
                }
                is ContentPart.Image -> {
                    obj.addProperty("type", "image_url")
                    val imageUrl = JsonObject()
                    imageUrl.addProperty("url", part.image.dataUri)
                    imageUrl.addProperty("detail", "auto")
                    obj.add("image_url", imageUrl)
                }
            }
            array.add(obj)
        }
        return array
    }

    /**
     * Coerce a [com.google.gson.JsonElement] reply (string OR multipart
     * array) into a flat string the existing text bubble can render.
     * For multipart responses we keep the text segments verbatim and
     * skip image parts (they're displayed by the bubble separately if
     * we ever add an inline image renderer). Markdown image syntax
     * like `![alt](url)` from a model is preserved by leaving the raw
     * markdown in text parts.
     */
    private fun extractResponseContent(content: com.google.gson.JsonElement): String {
        val primitive = content.asJsonPrimitive
        if (primitive != null && primitive.isString) {
            return primitive.asString
        }
        val array = content.asJsonArray
        if (array != null) {
            val parts = array.mapNotNull { element ->
                val obj = element.asJsonObject ?: return@mapNotNull null
                when (obj.get("type")?.asString) {
                    "text" -> obj.get("text")?.asString
                    "image_url" -> obj.get("image_url")?.asJsonObject
                        ?.get("url")?.asString
                        ?.let { "![image]($it)" }
                    else -> null
                }
            }
            return parts.joinToString("\n")
        }
        return content.toString()
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
