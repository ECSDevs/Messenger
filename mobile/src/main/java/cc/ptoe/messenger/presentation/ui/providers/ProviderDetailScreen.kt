package cc.ptoe.messenger.presentation.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.domain.model.ChatModel
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.ui.components.EmptyState
import cc.ptoe.messenger.presentation.ui.components.LoadingIndicator
import cc.ptoe.messenger.presentation.viewmodel.ProviderDetailViewModel
import cc.ptoe.messenger.presentation.viewmodel.SyncStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    providerId: String,
    onBackClick: () -> Unit,
    viewModel: ProviderDetailViewModel = viewModel(
        factory = ProviderDetailViewModel.provideFactory(
            providerRepository = MessengerApplication.instance.providerRepository,
            modelRepository = MessengerApplication.instance.modelRepository,
            apiRepository = MessengerApplication.instance.apiRepository,
            providerId = providerId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ChatModel?>(null) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var selectedModelIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(uiState.syncStatus) {
        if (uiState.syncStatus == SyncStatus.SUCCESS && uiState.syncedModels.isNotEmpty()) {
            selectedModelIds = uiState.syncedModels.map { it.modelId }.toSet()
            showSyncDialog = true
        } else if (uiState.syncStatus == SyncStatus.ERROR) {
            uiState.syncError?.let { error ->
                snackbarHostState.showSnackbar(error)
                viewModel.resetSyncState()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.provider?.name ?: "模型管理",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingIndicator(
                modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            )
        } else if (uiState.error != null) {
            EmptyState(
                icon = Icons.Default.Cloud,
                message = uiState.error ?: "加载失败",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                uiState.provider?.let { provider ->
                    ProviderInfoCard(
                        name = provider.name,
                        baseUrl = provider.baseUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                ModelListHeader(
                    onSyncClick = {
                        viewModel.syncModels()
                    },
                    onAddClick = {
                        showAddDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (models.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Cloud,
                        message = "暂无模型\n点击上方「同步模型」从 API 拉取\n或点击「手动添加」输入模型 ID",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(models, key = { it.id }) { model ->
                            ModelListItem(
                                model = model,
                                onToggleEnabled = { enabled ->
                                    viewModel.toggleModelEnabled(model.id, enabled)
                                },
                                onDeleteClick = {
                                    showDeleteDialog = model
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        if (uiState.syncStatus == SyncStatus.LOADING) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("同步模型") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("正在获取模型列表...")
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }

        if (showAddDialog) {
            AddModelDialog(
                onConfirm = { modelId, displayName ->
                    val success = viewModel.addModelManually(modelId, displayName)
                    if (success) {
                        showAddDialog = false
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("模型 ID 不能为空")
                        }
                    }
                },
                onDismiss = { showAddDialog = false }
            )
        }

        if (showDeleteDialog != null) {
            ConfirmationDialog(
                title = "删除模型",
                text = "确定要删除模型 \"${showDeleteDialog?.displayName}\" 吗？",
                confirmButtonText = "删除",
                dismissButtonText = "取消",
                onConfirm = {
                    showDeleteDialog?.let { model ->
                        viewModel.deleteModel(model.id)
                    }
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }

        if (showSyncDialog && uiState.syncedModels.isNotEmpty()) {
            SyncModelSelectionDialog(
                models = uiState.syncedModels,
                selectedModelIds = selectedModelIds,
                onToggleSelection = { modelId ->
                    selectedModelIds = if (modelId in selectedModelIds) {
                        selectedModelIds - modelId
                    } else {
                        selectedModelIds + modelId
                    }
                },
                onConfirm = {
                    coroutineScope.launch {
                        viewModel.saveSelectedModels(selectedModelIds.toList())
                        showSyncDialog = false
                        snackbarHostState.showSnackbar("模型已保存")
                    }
                },
                onDismiss = {
                    showSyncDialog = false
                    viewModel.resetSyncState()
                }
            )
        }
    }
}

@Composable
private fun ProviderInfoCard(
    name: String,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelListHeader(
    onSyncClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "模型列表",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onSyncClick) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.width(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("同步模型")
        }
        OutlinedButton(onClick = onAddClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.width(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("手动添加")
        }
    }
}

@Composable
private fun ModelListItem(
    model: ChatModel,
    onToggleEnabled: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (model.displayName != model.modelId) {
                Text(
                    text = model.modelId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = model.isEnabled,
            onCheckedChange = onToggleEnabled
        )
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AddModelDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加模型") },
        text = {
            Column {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID") },
                    placeholder = { Text("gpt-3.5-turbo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    placeholder = { Text("默认与模型 ID 相同") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(modelId.trim(), displayName.trim()) },
                enabled = modelId.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SyncModelSelectionDialog(
    models: List<ChatModel>,
    selectedModelIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要保存的模型") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = "共 ${models.size} 个模型可用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(models, key = { it.modelId }) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = model.modelId in selectedModelIds,
                                onCheckedChange = { onToggleSelection(model.modelId) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedModelIds.isNotEmpty()
            ) {
                Text("保存选中 (${selectedModelIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
