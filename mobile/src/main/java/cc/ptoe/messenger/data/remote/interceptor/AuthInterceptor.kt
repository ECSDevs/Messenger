package cc.ptoe.messenger.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val apiKey: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val apiKey = apiKey() ?: return chain.proceed(originalRequest)

        val request = originalRequest.newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .build()

        return chain.proceed(request)
    }
}
