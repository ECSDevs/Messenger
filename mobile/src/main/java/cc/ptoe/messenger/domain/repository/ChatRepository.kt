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

package cc.ptoe.messenger.domain.repository

import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    fun getMessagesByConversationId(conversationId: String): Flow<List<Message>>
    suspend fun createConversation(title: String, providerId: String): Conversation
    suspend fun sendMessage(conversationId: String, content: String)
    suspend fun deleteConversation(conversationId: String)
}
