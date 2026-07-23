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

package cc.ptoe.messenger.presentation.ui.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import java.util.Locale
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.R
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.presentation.ui.components.SectionHeader
import cc.ptoe.messenger.presentation.viewmodel.ConversationSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSettingsScreen(
    conversationId: String,
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ConversationSettingsViewModel = viewModel(
        factory = ConversationSettingsViewModel.provideFactory(
            conversationRepository = MessengerApplication.instance.conversationRepository,
            agentRepository = MessengerApplication.instance.agentRepository,
            modelRepository = MessengerApplication.instance.modelRepository,
            providerRepository = MessengerApplication.instance.providerRepository,
            conversationId = conversationId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val models by viewModel.modelsForSelectedProvider.collectAsStateWithLifecycle(initialValue = emptyList())
    val agent by viewModel.agent.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversation_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save() }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onTitleChange(it) },
                label = { Text(stringResource(R.string.conversation_settings_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.conversation_settings_agent_label, agent?.name ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(title = stringResource(R.string.conversation_settings_override_section))
            Text(
                text = stringResource(R.string.conversation_settings_override_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 模型覆盖
            OverrideToggleRow(
                label = stringResource(R.string.conversation_settings_override_model),
                checked = uiState.overrideModelEnabled,
                onCheckedChange = { checked ->
                    viewModel.onOverrideModelChange(checked)
                }
            )
            if (uiState.overrideModelEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                ProviderDropdown(
                    providers = providers,
                    selectedProviderId = uiState.selectedProviderId,
                    onProviderChange = { viewModel.onProviderChange(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ModelDropdown(
                    models = models,
                    selectedModelId = uiState.overrideModelId,
                    enabled = uiState.selectedProviderId != null,
                    onModelChange = { viewModel.onModelChange(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Temperature 覆盖
            OverrideToggleRow(
                label = stringResource(R.string.conversation_settings_override_temperature),
                checked = uiState.overrideTemperatureEnabled,
                onCheckedChange = { checked ->
                    viewModel.onOverrideTemperatureChange(checked, agent?.temperature)
                }
            )
            if (uiState.overrideTemperatureEnabled) {
                val tempValue = uiState.overrideTemperatureValue ?: 0.7f
                Text(
                    text = stringResource(R.string.conversation_settings_temperature_value, tempValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Slider(
                    value = tempValue,
                    onValueChange = { viewModel.onTemperatureChange(it) },
                    valueRange = 0f..2f,
                    steps = 19,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reasoning Effort 覆盖
            OverrideToggleRow(
                label = stringResource(R.string.conversation_settings_override_reasoning_effort),
                checked = uiState.overrideReasoningEffortEnabled,
                onCheckedChange = { checked ->
                    viewModel.onOverrideReasoningEffortChange(checked, agent?.reasoningEffort)
                }
            )
            if (uiState.overrideReasoningEffortEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                ReasoningEffortOverrideDropdown(
                    selectedEffort = uiState.overrideReasoningEffortValue,
                    onEffortChange = { viewModel.onReasoningEffortChange(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Max Tokens 覆盖
            OverrideToggleRow(
                label = stringResource(R.string.conversation_settings_override_max_tokens),
                checked = uiState.overrideMaxTokensEnabled,
                onCheckedChange = { checked ->
                    viewModel.onOverrideMaxTokensChange(checked, agent?.maxTokens)
                }
            )
            if (uiState.overrideMaxTokensEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.overrideMaxTokensValue?.toString() ?: "",
                    onValueChange = { value ->
                        viewModel.onMaxTokensChange(value.toIntOrNull())
                    },
                    label = { Text(stringResource(R.string.agent_edit_max_tokens_label)) },
                    placeholder = { Text(stringResource(R.string.agent_edit_max_tokens_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun OverrideToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private val reasoningEffortOptions = listOf(
    Pair(null, "Default"),
    Pair("none", "None"),
    Pair("minimal", "Minimal"),
    Pair("low", "Low"),
    Pair("medium", "Medium"),
    Pair("high", "High"),
    Pair("xhigh", "xHigh"),
    Pair("max", "Max"),
    Pair("ultra", "Ultra")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasoningEffortOverrideDropdown(
    selectedEffort: String?,
    onEffortChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = reasoningEffortOptions.find { it.first == selectedEffort }?.second
        ?: stringResource(R.string.agent_edit_reasoning_effort_default)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.agent_edit_reasoning_effort_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            reasoningEffortOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onEffortChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    providers: List<Provider>,
    selectedProviderId: String?,
    onProviderChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProvider = providers.find { it.id == selectedProviderId }
    val displayText = selectedProvider?.name ?: stringResource(R.string.conversation_settings_select_provider)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.agent_edit_provider_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (providers.isEmpty()) {
                Text(
                    text = stringResource(R.string.conversation_settings_no_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                providers.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.name) },
                        onClick = {
                            onProviderChange(provider.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<ChatModel>,
    selectedModelId: String?,
    enabled: Boolean,
    onModelChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.id == selectedModelId }
    val displayText = selectedModel?.displayName ?: if (enabled) stringResource(R.string.conversation_settings_select_model) else stringResource(R.string.conversation_settings_select_provider_first)

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { newExpanded ->
            if (enabled) expanded = newExpanded
        }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = { },
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.agent_edit_model_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (models.isEmpty()) {
                Text(
                    text = stringResource(R.string.conversation_settings_no_model),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (model.displayName != model.modelId) {
                                    Text(
                                        text = model.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelChange(model.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
