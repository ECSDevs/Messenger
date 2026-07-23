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
                        // Forward the sentinel so downstream can emit Done
                        // instead of mistaking the clean end-of-stream for an error.
                        emit("[DONE]")
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
