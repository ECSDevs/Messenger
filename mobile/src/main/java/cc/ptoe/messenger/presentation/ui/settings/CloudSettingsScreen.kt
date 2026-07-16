package cc.ptoe.messenger.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.data.cloud.CloudLoginOutcome
import cc.ptoe.messenger.data.cloud.CloudSyncRepository
import cc.ptoe.messenger.data.cloud.DEFAULT_CLOUD_SERVER_URL
import cc.ptoe.messenger.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    onBackClick: () -> Unit,
    cloudSyncRepository: CloudSyncRepository,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            MessengerApplication.instance.themePreferences,
            MessengerApplication.instance.appPreferences,
            cloudSyncRepository
        )
    )
) {
    val user by viewModel.cloudUser.collectAsStateWithLifecycle()
    val savedServerUrl by viewModel.cloudServerUrl.collectAsStateWithLifecycle()
    var serverUrl by remember(savedServerUrl) { mutableStateOf(savedServerUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var pendingLogin by remember { mutableStateOf<CloudLoginOutcome?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun notify(result: Result<*>, success: String, close: Boolean = false) {
        result.onSuccess {
            isBusy = false
            showMessage(success)
            if (close) onBackClick()
        }.onFailure {
            isBusy = false
            showMessage(it.message ?: "操作失败")
        }
    }

    fun completeLogin(
        outcome: CloudLoginOutcome,
        useLocalData: Boolean,
        successMessage: String
    ) {
        viewModel.completeLogin(outcome, useLocalData) { result ->
            notify(result, successMessage, close = true)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Messenger Cloud") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "云端账户与同步",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "在设备之间安全同步 Agent、对话和模型提供商。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CloudSectionCard(
                title = "服务器",
                icon = Icons.Default.Cloud
            ) {
                Text(
                    text = "云端服务地址决定数据发送到哪里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isBusy
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { serverUrl = DEFAULT_CLOUD_SERVER_URL },
                        enabled = !isBusy
                    ) {
                        Text("使用默认服务器")
                    }
                }
            }

            if (user == null) {
                CloudSectionCard(
                    title = if (register) "创建云端账户" else "登录云端账户",
                    icon = Icons.Default.Lock
                ) {
                    Text(
                        text = if (register) {
                            "创建账户后，可以在其他设备上恢复你的数据。"
                        } else {
                            "登录后即可同步你的 Messenger 数据。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("邮箱") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Button(
                        onClick = {
                            isBusy = true
                            val normalizedServerUrl = serverUrl.trim()
                            serverUrl = normalizedServerUrl
                            val callback: (Result<CloudLoginOutcome>) -> Unit = { result ->
                                result.onSuccess { outcome ->
                                    when {
                                        !outcome.hasLocalData -> {
                                            completeLogin(outcome, useLocalData = false, "登录成功")
                                        }
                                        outcome.cloudVersion == 0L -> {
                                            completeLogin(outcome, useLocalData = true, "本地数据已上传")
                                        }
                                        else -> {
                                            isBusy = false
                                            pendingLogin = outcome
                                        }
                                    }
                                }.onFailure {
                                    isBusy = false
                                    showMessage(it.message ?: "登录失败")
                                }
                            }
                            if (register) {
                                viewModel.register(email.trim(), password, normalizedServerUrl, callback)
                            } else {
                                viewModel.login(email.trim(), password, normalizedServerUrl, callback)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy &&
                            email.isNotBlank() &&
                            password.isNotBlank() &&
                            serverUrl.isNotBlank()
                    ) {
                        Text(if (register) "注册并登录" else "登录")
                    }
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { register = !register },
                            enabled = !isBusy
                        ) {
                            Text(if (register) "已有账户？登录" else "没有账户？注册")
                        }
                    }
                }
            } else {
                CloudSectionCard(
                    title = "账户",
                    icon = Icons.Default.Cloud
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CloudIconContainer(icon = Icons.Default.Cloud)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user!!.email,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "已连接到 Messenger Cloud",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                CloudSectionCard(
                    title = "同步数据",
                    icon = Icons.Default.Sync
                ) {
                    Text(
                        text = "上传会以本地数据覆盖云端；恢复会以云端数据覆盖本地。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            isBusy = true
                            viewModel.upload(serverUrl.trim()) { notify(it, "本地数据已上传") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text("上传并同步本地数据")
                    }
                    FilledTonalButton(
                        onClick = {
                            isBusy = true
                            viewModel.restore(serverUrl.trim()) { notify(it, "云端数据已恢复") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text("从云端恢复数据")
                    }
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                CloudSectionCard(
                    title = "账户安全",
                    icon = Icons.Default.Lock
                ) {
                    OutlinedButton(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text("更改密码")
                    }
                    TextButton(
                        onClick = { viewModel.logout { notify(it, "已退出登录") } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("退出登录")
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "删除云端账户",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "永久删除云端账户及其同步数据，本地数据不会受到影响。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("永久注销账户", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showPasswordDialog = false },
            title = { Text("更改密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("当前密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("新密码") },
                        supportingText = { Text("至少 8 个字符") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isBusy = true
                        viewModel.changePassword(currentPassword, newPassword) { result ->
                            notify(result, "密码已更新")
                            if (result.isSuccess) {
                                currentPassword = ""
                                newPassword = ""
                                showPasswordDialog = false
                            }
                        }
                    },
                    enabled = !isBusy && currentPassword.isNotBlank() && newPassword.length >= 8
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPasswordDialog = false },
                    enabled = !isBusy
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            title = { Text("永久注销账户") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("此操作会永久删除云端账户、所有同步数据和头像，无法恢复。本地对话、设置和头像不会受到影响。")
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("当前密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isBusy = true
                        viewModel.deleteAccount(deletePassword) { result ->
                            notify(result, "账户已注销", close = true)
                            if (result.isSuccess) {
                                deletePassword = ""
                                showDeleteDialog = false
                            }
                        }
                    },
                    enabled = !isBusy && deletePassword.isNotBlank()
                ) {
                    Text("永久注销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isBusy
                ) {
                    Text("取消")
                }
            }
        )
    }

    pendingLogin?.let { outcome ->
        CloudSyncChoiceDialog(
            outcome = outcome,
            onUseLocal = {
                pendingLogin = null
                isBusy = true
                completeLogin(outcome, useLocalData = true, "本地数据已上传")
            },
            onUseCloud = {
                pendingLogin = null
                isBusy = true
                completeLogin(outcome, useLocalData = false, "云端数据已恢复")
            },
            onDismiss = { pendingLogin = null }
        )
    }
}

@Composable
private fun CloudSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CloudIconContainer(icon = icon)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                content()
            }
        )
    }
}

@Composable
private fun CloudIconContainer(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
    }
}
