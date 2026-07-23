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
import androidx.lifecycle.viewModelScope
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AgentWithModel(
    val agent: Agent,
    val model: ChatModel?
)

class AgentsViewModel(
    private val agentRepository: AgentRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    val agentsWithModel = combine(
        agentRepository.getAll(),
        modelRepository.getAll()
    ) { agents, models ->
        agents.map { agent ->
            val model = models.find { it.id == agent.defaultModelId }
            AgentWithModel(
                agent = agent,
                model = model
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            agentRepository.delete(agentId)
        }
    }

    fun cloneAgent(agentId: String) {
        viewModelScope.launch {
            agentRepository.clone(agentId)
        }
    }

    companion object {
        fun provideFactory(
            agentRepository: AgentRepository,
            modelRepository: ModelRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AgentsViewModel(agentRepository, modelRepository) as T
            }
        }
    }
}
