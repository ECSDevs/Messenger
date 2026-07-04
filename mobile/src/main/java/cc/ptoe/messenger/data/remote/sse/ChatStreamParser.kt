package cc.ptoe.messenger.data.remote.sse

import cc.ptoe.messenger.data.remote.dto.ChatCompletionChunkDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object ChatStreamParser {

    private val gson = Gson()

    fun parseToEvents(jsonFlow: Flow<String>): Flow<ChatStreamEvent> = flow {
        jsonFlow.collect { json ->
            try {
                val chunk = gson.fromJson(json, ChatCompletionChunkDto::class.java)
                val choice = chunk.choices.firstOrNull()
                if (choice != null) {
                    val delta = choice.delta
                    val content = delta.content
                    if (!content.isNullOrEmpty()) {
                        emit(ChatStreamEvent.Content(content))
                    }
                    val finishReason = choice.finishReason
                    if (finishReason != null) {
                        emit(ChatStreamEvent.Done(finishReason))
                    }
                }
            } catch (e: Exception) {
                emit(ChatStreamEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    fun parseToText(jsonFlow: Flow<String>): Flow<String> = flow {
        jsonFlow.collect { json ->
            try {
                val chunk = gson.fromJson(json, ChatCompletionChunkDto::class.java)
                val choice = chunk.choices.firstOrNull()
                val content = choice?.delta?.content
                if (!content.isNullOrEmpty()) {
                    emit(content)
                }
            } catch (_: Exception) {
            }
        }
    }
}
