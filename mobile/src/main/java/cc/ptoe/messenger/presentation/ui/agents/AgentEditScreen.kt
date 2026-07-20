package cc.ptoe.messenger.presentation.ui.agents

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yalantis.ucrop.UCrop
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.R
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.domain.model.Provider
import cc.ptoe.messenger.presentation.ui.components.AgentAvatar
import cc.ptoe.messenger.presentation.ui.components.SectionHeader
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.viewmodel.AgentEditViewModel
import androidx.compose.material3.ExposedDropdownMenuAnchorType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditScreen(
    agentId: String? = null,
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AgentEditViewModel = viewModel(
        factory = AgentEditViewModel.provideFactory(
            agentRepository = MessengerApplication.instance.agentRepository,
            modelRepository = MessengerApplication.instance.modelRepository,
            providerRepository = MessengerApplication.instance.providerRepository,
            cloudSyncRepository = MessengerApplication.instance.cloudSyncRepository,
            agentId = agentId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val models by viewModel.modelsForSelectedProvider.collectAsStateWithLifecycle(initialValue = emptyList())
    val cloudUser by MessengerApplication.instance.cloudSyncRepository.user.collectAsStateWithLifecycle(initialValue = null)

    // 非默认 Agent 才显示"跟随默认 Agent"开关
    val showFollowToggles = !uiState.isDefault

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showPushDialog by remember { mutableStateOf(false) }
    var showUnpublishDialog by remember { mutableStateOf(false) }
    var showPullDialog by remember { mutableStateOf(false) }

    val strPublishedSuccess = stringResource(R.string.agent_edit_published_success)
    val strPublishFailed = stringResource(R.string.agent_edit_publish_failed)
    val strPushedSuccess = stringResource(R.string.agent_edit_pushed_success)
    val strPushFailed = stringResource(R.string.agent_edit_push_failed)
    val strUnpublishedSuccess = stringResource(R.string.agent_edit_unpublished_success)
    val strUnpublishFailed = stringResource(R.string.agent_edit_unpublish_failed)
    val strUpdatedSuccess = stringResource(R.string.agent_edit_updated_success)
    val strUpdateFailed = stringResource(R.string.agent_edit_update_failed)
    val strAlreadyUpToDate = stringResource(R.string.agent_edit_already_up_to_date)
    val strGetUpdateFailed = stringResource(R.string.agent_edit_get_update_failed)

    // 裁剪结果：uCrop 输出到缓存 URI，再复制到内部存储持久化
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val croppedUri = UCrop.getOutput(result.data!!) ?: return@rememberLauncherForActivityResult
            val previousAvatar = uiState.avatar
            coroutineScope.launch {
                val path = withContext(Dispatchers.IO) {
                    copyAvatarToInternal(context, croppedUri)
                }
                if (path != null) {
                    previousAvatar?.let { File(it).takeIf { f -> f.exists() }?.delete() }
                    viewModel.onAvatarChange(path)
                }
            }
        }
    }

    // 选图后启动 uCrop 进行 1:1 裁剪
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val destinationUri = Uri.fromFile(
                File(context.cacheDir, "crop_${UUID.randomUUID()}.jpg")
            )
            val cropIntent = UCrop.of(uri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(512, 512)
                .getIntent(context)
            cropLauncher.launch(cropIntent)
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            uiState.isDefault -> stringResource(R.string.agent_edit_title_default)
                            uiState.isEditing -> stringResource(R.string.agent_edit_title_edit)
                            else -> stringResource(R.string.agent_edit_title_new)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (viewModel.save()) {
                        }
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 头像
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clickable {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AgentAvatar(
                        avatar = uiState.avatar,
                        size = 96.dp
                    )
                    // 右下角相机徽标，提示可点击更换
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .zIndex(1f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = stringResource(R.string.agent_edit_change_avatar),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            if (uiState.avatar != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        uiState.avatar?.let { File(it).takeIf { f -> f.exists() }?.delete() }
                        viewModel.onAvatarChange(null)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.agent_edit_remove_avatar))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text(stringResource(R.string.agent_edit_name_label)) },
                isError = uiState.nameError != null,
                supportingText = {
                    uiState.nameError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                singleLine = true,
                enabled = !uiState.isDefault, // 默认 Agent 名称不允许修改（保持"默认 Agent"标识）
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 系统提示词
            if (showFollowToggles) {
                FollowToggleRow(
                    label = stringResource(R.string.agent_edit_follow_system_prompt),
                    checked = uiState.followDefaultSystemPrompt,
                    onCheckedChange = { viewModel.onFollowSystemPromptChange(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            val systemPromptValue = if (showFollowToggles && uiState.followDefaultSystemPrompt) {
                uiState.defaultAgent?.systemPrompt ?: ""
            } else {
                uiState.systemPrompt
            }
            OutlinedTextField(
                value = systemPromptValue,
                onValueChange = { viewModel.onSystemPromptChange(it) },
                label = { Text(stringResource(R.string.agent_edit_system_prompt_label)) },
                placeholder = { Text(stringResource(R.string.agent_edit_system_prompt_placeholder)) },
                minLines = 3,
                maxLines = 8,
                enabled = !showFollowToggles || !uiState.followDefaultSystemPrompt,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Provider + Model
            if (showFollowToggles) {
                FollowToggleRow(
                    label = stringResource(R.string.agent_edit_follow_model),
                    checked = uiState.followDefaultModel,
                    onCheckedChange = { viewModel.onFollowModelChange(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            val modelSectionEnabled = !showFollowToggles || !uiState.followDefaultModel
            if (modelSectionEnabled) {
                ProviderDropdown(
                    providers = providers,
                    selectedProviderId = uiState.selectedProviderId,
                    onProviderChange = { viewModel.onProviderChange(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                ModelDropdown(
                    models = models,
                    selectedModelId = uiState.defaultModelId,
                    enabled = uiState.selectedProviderId != null,
                    onModelChange = { viewModel.onDefaultModelChange(it) }
                )
            } else {
                // 跟随默认 Agent：只读展示默认 Agent 的模型信息
                FollowedValueBox(
                    label = stringResource(R.string.agent_edit_followed_model_label),
                    value = stringResource(R.string.agent_edit_followed_model_value)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(title = stringResource(R.string.agent_edit_advanced_settings))

            Spacer(modifier = Modifier.height(8.dp))

            // Temperature
            if (showFollowToggles) {
                FollowToggleRow(
                    label = stringResource(R.string.agent_edit_follow_temperature),
                    checked = uiState.followDefaultTemperature,
                    onCheckedChange = { viewModel.onFollowTemperatureChange(it) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            val tempEnabled = !showFollowToggles || !uiState.followDefaultTemperature
            val tempValue = if (showFollowToggles && uiState.followDefaultTemperature) {
                uiState.defaultAgent?.temperature ?: uiState.temperature
            } else {
                uiState.temperature
            }
            Text(
                text = stringResource(R.string.agent_edit_temperature_label, tempValue),
                style = MaterialTheme.typography.bodyMedium,
                color = if (tempEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Slider(
                value = tempValue,
                onValueChange = { viewModel.onTemperatureChange(it) },
                enabled = tempEnabled,
                valueRange = 0f..2f,
                steps = 19,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Max Tokens
            if (showFollowToggles) {
                FollowToggleRow(
                    label = stringResource(R.string.agent_edit_follow_max_tokens),
                    checked = uiState.followDefaultMaxTokens,
                    onCheckedChange = { viewModel.onFollowMaxTokensChange(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            val maxTokensEnabled = !showFollowToggles || !uiState.followDefaultMaxTokens
            val maxTokensValue = if (showFollowToggles && uiState.followDefaultMaxTokens) {
                uiState.defaultAgent?.maxTokens?.toString() ?: ""
            } else {
                uiState.maxTokens ?: ""
            }
            OutlinedTextField(
                value = maxTokensValue,
                onValueChange = { value ->
                    viewModel.onMaxTokensChange(value.ifBlank { null })
                },
                label = { Text(stringResource(R.string.agent_edit_max_tokens_label)) },
                placeholder = { Text(stringResource(R.string.agent_edit_max_tokens_placeholder)) },
                singleLine = true,
                enabled = maxTokensEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            if (cloudUser != null && uiState.isEditing && !uiState.isDefault) {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = stringResource(R.string.agent_edit_market_section))
                Spacer(modifier = Modifier.height(8.dp))
                when (uiState.marketAgentRole) {
                    "publisher" -> {
                        Button(
                            onClick = { showPushDialog = true },
                            enabled = !uiState.marketActionInProgress,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.agent_edit_push_update))
                        }
                        TextButton(
                            onClick = { showUnpublishDialog = true },
                            enabled = !uiState.marketActionInProgress,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.agent_edit_unpublish))
                        }
                    }
                    "importer" -> {
                        Button(
                            onClick = {
                                viewModel.checkMarketAgentUpdate { result ->
                                    coroutineScope.launch {
                                        result.onSuccess { update ->
                                            if (update.hasUpdate) showPullDialog = true
                                            else snackbarHostState.showSnackbar(strAlreadyUpToDate)
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(error.message ?: strGetUpdateFailed)
                                        }
                                    }
                                }
                            },
                            enabled = !uiState.marketActionInProgress,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.agent_edit_get_update))
                        }
                    }
                    else -> Button(
                        onClick = { showPublishDialog = true },
                        enabled = !uiState.marketActionInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.agent_edit_publish_to_market))
                    }
                }
            }
        }
    }

    if (showPublishDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.agent_edit_publish_title),
            text = stringResource(R.string.agent_edit_publish_desc),
            confirmButtonText = stringResource(R.string.agent_edit_publish_confirm),
            dismissButtonText = stringResource(R.string.action_cancel),
            onConfirm = {
                showPublishDialog = false
                viewModel.publishMarketAgent { result ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(result.fold({ strPublishedSuccess }, { it.message ?: strPublishFailed }))
                    }
                }
            },
            onDismiss = { showPublishDialog = false }
        )
    }
    if (showPushDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.agent_edit_push_title),
            text = stringResource(R.string.agent_edit_push_desc),
            confirmButtonText = stringResource(R.string.action_push),
            dismissButtonText = stringResource(R.string.action_cancel),
            onConfirm = {
                showPushDialog = false
                viewModel.pushMarketAgentUpdate { result ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(result.fold({ strPushedSuccess }, { it.message ?: strPushFailed }))
                    }
                }
            },
            onDismiss = { showPushDialog = false }
        )
    }
    if (showUnpublishDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.agent_edit_unpublish_title),
            text = stringResource(R.string.agent_edit_unpublish_desc),
            confirmButtonText = stringResource(R.string.action_unpublish),
            dismissButtonText = stringResource(R.string.action_cancel),
            onConfirm = {
                showUnpublishDialog = false
                viewModel.removeMarketAgent { result ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(result.fold({ strUnpublishedSuccess }, { it.message ?: strUnpublishFailed }))
                    }
                }
            },
            onDismiss = { showUnpublishDialog = false }
        )
    }
    if (showPullDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.agent_edit_get_update_title),
            text = stringResource(R.string.agent_edit_get_update_desc),
            confirmButtonText = stringResource(R.string.action_update),
            dismissButtonText = stringResource(R.string.action_cancel),
            onConfirm = {
                showPullDialog = false
                viewModel.applyMarketAgentUpdate { result ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(result.fold({ strUpdatedSuccess }, { it.message ?: strUpdateFailed }))
                    }
                }
            },
            onDismiss = { showPullDialog = false }
        )
    }
}

@Composable
private fun FollowToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun FollowedValueBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { },
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth()
    )
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
    val displayText = selectedProvider?.name ?: stringResource(R.string.agent_edit_select_provider)

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
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (providers.isEmpty()) {
                Text(
                    text = stringResource(R.string.agent_edit_no_provider),
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
    val displayText = selectedModel?.displayName
        ?: if (enabled) stringResource(R.string.agent_edit_select_model) else stringResource(R.string.agent_edit_select_provider_first)

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
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (models.isEmpty()) {
                Text(
                    text = stringResource(R.string.agent_edit_no_model),
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

private fun copyAvatarToInternal(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "agent_avatars").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        dest.absolutePath
    } catch (e: Exception) {
        null
    }
}
