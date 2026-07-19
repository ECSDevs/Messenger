package cc.ptoe.messenger.data.remote.sse

import cc.ptoe.messenger.data.remote.dto.ChatCompletionChunkDto
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object ChatStreamParser {

    private val gson = Gson()

    fun parseToEvents(jsonFlow: Flow<String>): Flow<ChatStreamEvent> = flow {
        jsonFlow.collect { json ->
            // OpenAI protocol: a literal "[DONE]" sentinel marks the end of the stream.
            // Some compatible APIs never set finish_reason on the last chunk and only
            // send this sentinel — translate it to Done so the consumer doesn't treat
            // the clean end-of-stream as an error.
            if (json == "[DONE]") {
                emit(ChatStreamEvent.Done(null))
                return@collect
            }
            try {
                val chunk = gson.fromJson(json, ChatCompletionChunkDto::class.java)
                val choice = chunk?.choices?.firstOrNull()
                if (choice != null) {
                    val content = choice.delta.content
                    if (!content.isNullOrEmpty()) {
                        emit(ChatStreamEvent.Content(content))
                    }
                    val finishReason = choice.finishReason
                    if (finishReason != null) {
                        emit(ChatStreamEvent.Done(finishReason))
                    }
                }
            } catch (e: JsonSyntaxException) {
                // Skip malformed chunks (keep-alive comments, non-standard fields,
                // partial frames) without killing the whole stream. Only surface
                // an error if the stream never produces any terminal event — that
                // fallback is handled by the consumer's hasFinished guard.
            } catch (e: Exception) {
                emit(ChatStreamEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    fun parseToText(jsonFlow: Flow<String>): Flow<String> = flow {
        jsonFlow.collect { json ->
            if (json == "[DONE]") return@collect
            try {
                val chunk = gson.fromJson(json, ChatCompletionChunkDto::class.java)
                val choice = chunk?.choices?.firstOrNull()
                val content = choice?.delta?.content
                if (!content.isNullOrEmpty()) {
                    emit(content)
                }
            } catch (_: Exception) {
            }
        }
    }
}
