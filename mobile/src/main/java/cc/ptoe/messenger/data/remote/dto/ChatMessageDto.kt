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

package cc.ptoe.messenger.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Chat completion message. The [content] field is rendered as either a
 * plain string (text-only messages) or an array of [ContentPartDto]
 * (multimodal messages). The shape follows the OpenAI Chat Completions
 * spec; providers that don't accept arrays still get a string because
 * [toRequestContent] picks the appropriate representation at request
 * build time.
 */
data class ChatMessageDto(
    @SerializedName("role") val role: String,
    /**
     * Raw JSON element so the same DTO can hold either a string or an
     * array. We don't deserialize it into a sealed class because the
     * server echoes it back unchanged and we want to stay forward
     * compatible with future part types.
     */
    @SerializedName("content") val content: JsonElement,
    @SerializedName("reasoning_content") val reasoningContent: String? = null
)
