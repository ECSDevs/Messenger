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
