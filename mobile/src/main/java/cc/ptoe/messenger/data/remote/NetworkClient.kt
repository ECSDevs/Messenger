package cc.ptoe.messenger.data.remote

import cc.ptoe.messenger.data.remote.api.OpenAiApi
import cc.ptoe.messenger.data.remote.interceptor.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val apiCache = ConcurrentHashMap<String, OpenAiApi>()

    fun createOpenAiApi(baseUrl: String, apiKey: String): OpenAiApi {
        val normalizedUrl = ensureTrailingSlash(baseUrl)
        val cacheKey = "$normalizedUrl|$apiKey"

        return apiCache.getOrPut(cacheKey) {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor { apiKey })
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            retrofit.create(OpenAiApi::class.java)
        }
    }

    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}