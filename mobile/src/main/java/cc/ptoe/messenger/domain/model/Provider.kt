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

package cc.ptoe.messenger.domain.model

data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    val maskedApiKey: String
        get() = maskApiKey(apiKey)

    companion object {
        fun maskApiKey(apiKey: String): String {
            if (apiKey.length <= 8) {
                return "*".repeat(apiKey.length)
            }
            val prefix = apiKey.take(4)
            val suffix = apiKey.takeLast(4)
            return "$prefix****$suffix"
        }
    }
}
