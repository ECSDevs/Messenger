package cc.ptoe.messenger.data.remote.sse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader

object SSEParser {

    fun parse(responseBody: ResponseBody): Flow<String> = flow {
        var reader: BufferedReader? = null
        try {
            val inputStream = responseBody.byteStream()
            reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ")
                    if (data == "[DONE]") {
                        break
                    }
                    emit(data)
                }
            }
        } finally {
            try {
                reader?.close()
            } catch (_: Exception) {
            }
            try {
                responseBody.close()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)
}
