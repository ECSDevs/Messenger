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

package cc.ptoe.messenger.data.remote.sse

import cc.ptoe.messenger.data.remote.dto.ChatCompletionChunkDto
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object ChatStreamParser {

    private val gson = Gson()

    fun parseToEvents(jsonFlow: Flow<String>): Flow<ChatStreamEvent> = flow {
        var inThinkBlock = false
        var reasoningEmitted = false
        jsonFlow.collect { json ->
            if (json == "[DONE]") {
                if (inThinkBlock) {
                    emit(ChatStreamEvent.Content("</think>\n"))
                    inThinkBlock = false
                }
                emit(ChatStreamEvent.Done(null))
                return@collect
            }
            try {
                val chunk = gson.fromJson(json, ChatCompletionChunkDto::class.java)
                val choice = chunk?.choices?.firstOrNull()
                if (choice != null) {
                    val reasoning = choice.delta.reasoningContent
                    if (reasoning != null && reasoning.isNotEmpty()) {
                        if (!reasoningEmitted) {
                            emit(ChatStreamEvent.ReasoningDetected)
                            reasoningEmitted = true
                        }
                        if (!inThinkBlock) {
                            emit(ChatStreamEvent.Content("<think>"))
                            inThinkBlock = true
                        }
                        emit(ChatStreamEvent.Content(reasoning))
                    }
                    val content = choice.delta.content
                    if (content != null) {
                        val contentText = extractContentText(content)
                        if (contentText.isNotEmpty()) {
                            if (inThinkBlock) {
                                emit(ChatStreamEvent.Content("</think>\n"))
                                inThinkBlock = false
                            }
                            emit(ChatStreamEvent.Content(contentText))
                        }
                    }
                    val finishReason = choice.finishReason
                    if (finishReason != null) {
                        if (inThinkBlock) {
                            emit(ChatStreamEvent.Content("</think>\n"))
                            inThinkBlock = false
                        }
                        emit(ChatStreamEvent.Done(finishReason))
                    }
                }
            } catch (e: JsonSyntaxException) {
            } catch (e: Exception) {
                emit(ChatStreamEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    fun parseToText(jsonFlow: Flow<String>): Flow<String> = flow {
        var inThinkBlock = false
        jsonFlow.collect { json ->
            if (json == "[DONE]") return@collect
            try {
                val chunk = gson.fromJson(json, ChatCompletionChunkDto::class.java)
                val choice = chunk?.choices?.firstOrNull()
                val reasoning = choice?.delta?.reasoningContent
                if (reasoning != null && reasoning.isNotEmpty()) {
                    if (!inThinkBlock) {
                        emit("<think>")
                        inThinkBlock = true
                    }
                    emit(reasoning)
                }
                val content = choice?.delta?.content
                if (content != null) {
                    val text = extractContentText(content)
                    if (text.isNotEmpty()) {
                        if (inThinkBlock) {
                            emit("</think>\n")
                            inThinkBlock = false
                        }
                        emit(text)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun extractContentText(content: JsonElement): String {
        return if (content.isJsonPrimitive) {
            content.asString
        } else if (content.isJsonArray) {
            content.asJsonArray.extractText()
        } else {
            ""
        }
    }

    private fun JsonArray.extractText(): String {
        val builder = StringBuilder()
        for (element in this) {
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                val type = obj.get("type")?.asString
                when (type) {
                    "text" -> builder.append(obj.get("text")?.asString ?: "")
                    "image_url" -> {
                    }
                }
            }
        }
        return builder.toString()
    }
}
