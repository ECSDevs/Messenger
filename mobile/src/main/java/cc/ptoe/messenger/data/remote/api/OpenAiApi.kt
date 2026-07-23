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

package cc.ptoe.messenger.data.remote.api

import cc.ptoe.messenger.data.remote.dto.ChatCompletionRequestDto
import cc.ptoe.messenger.data.remote.dto.ChatCompletionResponseDto
import cc.ptoe.messenger.data.remote.dto.ModelsResponseDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAiApi {

    @GET("models")
    suspend fun getModels(): ModelsResponseDto

    @Streaming
    @POST("chat/completions")
    suspend fun createChatCompletionStream(
        @Body request: ChatCompletionRequestDto
    ): ResponseBody

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequestDto
    ): ChatCompletionResponseDto
}
