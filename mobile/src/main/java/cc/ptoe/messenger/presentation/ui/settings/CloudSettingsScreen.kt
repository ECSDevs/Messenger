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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
            Text("云端账户与备份", style = MaterialTheme.typography.headlineSmall)
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
                        val callback: (Result<*>) -> Unit = { result -> notify(result, "登录成功", close = true) }
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
                Button(onClick = { viewModel.setCloudServerUrl(serverUrl) {}; viewModel.upload { notify(it, "备份上传成功") } }, modifier = Modifier.fillMaxWidth()) {
                    Text("上传备份")
                }
                Button(onClick = { viewModel.restore { notify(it, "云端数据已恢复") } }, modifier = Modifier.fillMaxWidth()) {
                    Text("从云端恢复")
                }
                TextButton(onClick = { viewModel.logout { notify(it, "已退出登录") } }) { Text("退出登录") }
            }
        }
    }
}
