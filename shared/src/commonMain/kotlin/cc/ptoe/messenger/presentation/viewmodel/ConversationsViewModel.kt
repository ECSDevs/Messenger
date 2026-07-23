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

package cc.ptoe.messenger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlin.reflect.KClass
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.ConversationRepository
import cc.ptoe.messenger.domain.repository.CurrentAgentRepository
import cc.ptoe.messenger.domain.repository.MessageRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import cc.ptoe.messenger.generated.resources.Res
import cc.ptoe.messenger.generated.resources.conversations_clone_suffix
import cc.ptoe.messenger.generated.resources.conversations_new_chat
import org.jetbrains.compose.resources.getString
import cc.ptoe.messenger.data.util.randomUuid

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModel(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val currentAgentRepository: CurrentAgentRepository,
    private val agentRepository: AgentRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    val currentAgent: StateFlow<Agent?> = currentAgentRepository.currentAgent
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val allAgents: StateFlow<List<Agent>> = agentRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _showAllAgents = MutableStateFlow(true)
    val showAllAgents: StateFlow<Boolean> = _showAllAgents.asStateFlow()

    val conversations: StateFlow<List<Conversation>> = combine(
        currentAgentRepository.currentAgent,
        _showAllAgents
    ) { agent, showAll -> agent to showAll }
        .flatMapLatest { (agent, showAll) ->
            when {
                showAll -> conversationRepository.getAll()
                agent != null -> conversationRepository.getByAgentId(agent.id)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createNewConversation(
        title: String? = null,
        agentId: String? = null,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val agent = if (agentId != null) {
                agentRepository.getById(agentId).first()
            } else {
                currentAgentRepository.currentAgent.first()
            } ?: return@launch
            currentAgentRepository.setCurrentAgentId(agent.id)
            _showAllAgents.value = false
            val now = System.currentTimeMillis()
            val providerId = determineProviderId(agent)
            val conversation = Conversation(
                id = randomUuid(),
                title = title ?: getString(Res.string.conversations_new_chat),
                providerId = providerId,
                agentId = agent.id,
                createdAt = now,
                updatedAt = now,
                lastMessage = null
            )
            conversationRepository.insert(conversation)
            onCreated(conversation.id)
        }
    }

    private suspend fun determineProviderId(agent: Agent): String {
        val allModels = modelRepository.getAll().first()
        val enabledModels = allModels.filter { it.isEnabled }

        if (enabledModels.isEmpty()) return ""

        val defaultModel = agent.defaultModelId?.let { modelId ->
            enabledModels.find { it.id == modelId }
        }

        return (defaultModel ?: enabledModels.firstOrNull())?.providerId ?: ""
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.getById(conversationId).first()
                ?: return@launch
            val now = System.currentTimeMillis()
            conversationRepository.update(
                conversation.copy(
                    title = newTitle,
                    updatedAt = now
                )
            )
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.delete(conversationId)
        }
    }

    fun cloneConversation(
        conversationId: String,
        onCloned: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val source = conversationRepository.getById(conversationId).first()
                ?: return@launch
            val clonedConversationId = randomUuid()
            val now = System.currentTimeMillis()
            val clonedConversation = source.copy(
                id = clonedConversationId,
                title = source.title + getString(Res.string.conversations_clone_suffix),
                createdAt = now,
                updatedAt = now
            )

            conversationRepository.insert(clonedConversation)
            val clonedMessages = messageRepository.getByConversationId(source.id).first().map { message ->
                message.copy(
                    id = randomUuid(),
                    conversationId = clonedConversationId,
                    status = if (message.status == MessageStatus.SENDING) {
                        MessageStatus.SENT
                    } else {
                        message.status
                    }
                )
            }
            messageRepository.insertAll(clonedMessages)
            onCloned(clonedConversationId)
        }
    }

    fun switchAgent(agentId: String) {
        viewModelScope.launch {
            currentAgentRepository.setCurrentAgentId(agentId)
        }
        _showAllAgents.value = false
    }

    fun showAllAgents() {
        _showAllAgents.value = true
    }

    companion object {
        fun provideFactory(
            conversationRepository: ConversationRepository,
            messageRepository: MessageRepository,
            currentAgentRepository: CurrentAgentRepository,
            agentRepository: AgentRepository,
            modelRepository: ModelRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                return ConversationsViewModel(
                    conversationRepository,
                    messageRepository,
                    currentAgentRepository,
                    agentRepository,
                    modelRepository
                ) as T
            }
        }
    }
}
