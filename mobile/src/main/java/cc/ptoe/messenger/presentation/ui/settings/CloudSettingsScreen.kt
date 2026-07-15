package cc.ptoe.messenger.presentation.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.data.cloud.CloudSyncRepository
import cc.ptoe.messenger.data.cloud.DEFAULT_CLOUD_SERVER_URL
import cc.ptoe.messenger.data.cloud.CloudLoginOutcome
import cc.ptoe.messenger.presentation.viewmodel.SettingsViewModel

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
    val context = LocalContext.current
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

    fun notify(result: Result<*>, success: String, close: Boolean = false) {
        result.onSuccess {
            Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
            if (close) onBackClick()
        }.onFailure {
            Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messenger Cloud") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("云端账户与同步", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("服务器地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextButton(onClick = { serverUrl = DEFAULT_CLOUD_SERVER_URL }) { Text("使用默认服务器") }

            if (user == null) {
                OutlinedTextField(email, { email = it }, label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(
                    onClick = {
                        viewModel.setCloudServerUrl(serverUrl) { result ->
                            result.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        }
                        val callback: (Result<CloudLoginOutcome>) -> Unit = { result ->
                            result.onSuccess { outcome ->
                                if (!outcome.hasLocalData) {
                                    viewModel.completeLogin(outcome, useLocalData = false) { syncResult ->
                                        notify(syncResult, "登录成功", close = true)
                                    }
                                } else if (outcome.cloudVersion == 0L) {
                                    viewModel.completeLogin(outcome, useLocalData = true) { syncResult ->
                                        notify(syncResult, "本地数据已上传", close = true)
                                    }
                                } else {
                                    pendingLogin = outcome
                                }
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "登录失败", Toast.LENGTH_LONG).show()
                            }
                        }
                        if (register) viewModel.register(email, password, serverUrl, callback)
                        else viewModel.login(email, password, serverUrl, callback)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (register) "注册并登录" else "登录") }
                TextButton(onClick = { register = !register }) {
                    Text(if (register) "已有账户？登录" else "没有账户？注册")
                }
            } else {
                Text(user!!.email, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { viewModel.upload(serverUrl) { notify(it, "同步成功") } }, modifier = Modifier.fillMaxWidth()) {
                    Text("上传并同步")
                }
                Button(onClick = { viewModel.restore(serverUrl) { notify(it, "同步成功") } }, modifier = Modifier.fillMaxWidth()) {
                    Text("从云端同步")
                }
                OutlinedButton(onClick = { showPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("更改密码")
                }
                TextButton(onClick = { viewModel.logout { notify(it, "已退出登录") } }) { Text("退出登录") }
                TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("注销账户", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("更改密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("当前密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("新密码") },
                        supportingText = { Text("至少 8 个字符") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changePassword(currentPassword, newPassword) { result ->
                            notify(result, "密码已更新")
                            if (result.isSuccess) {
                                currentPassword = ""
                                newPassword = ""
                                showPasswordDialog = false
                            }
                        }
                    },
                    enabled = currentPassword.isNotBlank() && newPassword.length >= 8
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("取消") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("注销账户") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("此操作会永久删除云端账户、所有同步数据和头像，无法恢复。本地对话、设置和头像不会受到影响。")
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("当前密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(deletePassword) { result ->
                            notify(result, "账户已注销", close = true)
                            if (result.isSuccess) {
                                deletePassword = ""
                                showDeleteDialog = false
                            }
                        }
                    },
                    enabled = deletePassword.isNotBlank()
                ) {
                    Text("永久注销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    pendingLogin?.let { outcome ->
        CloudSyncChoiceDialog(
            outcome = outcome,
            onUseLocal = {
                pendingLogin = null
                viewModel.completeLogin(outcome, useLocalData = true) { result ->
                    notify(result, "本地数据已上传", close = true)
                }
            },
            onUseCloud = {
                pendingLogin = null
                viewModel.completeLogin(outcome, useLocalData = false) { result ->
                    notify(result, "云端数据已恢复", close = true)
                }
            },
            onDismiss = { pendingLogin = null }
        )
    }
}
