package cc.ptoe.messenger.presentation.ui.settings

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.presentation.theme.ThemeMode
import cc.ptoe.messenger.presentation.ui.components.AgentAvatar
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.ui.components.ListItem
import cc.ptoe.messenger.presentation.ui.components.SectionHeader
import cc.ptoe.messenger.presentation.viewmodel.SettingsViewModel
import cc.ptoe.messenger.data.cloud.CloudSyncRepository
import cc.ptoe.messenger.data.cloud.DEFAULT_CLOUD_SERVER_URL
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onProvidersClick: () -> Unit,
    onLicensesClick: () -> Unit,
    cloudSyncRepository: CloudSyncRepository,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            themePreferences = MessengerApplication.instance.themePreferences,
            appPreferences = MessengerApplication.instance.appPreferences,
            cloudSyncRepository = cloudSyncRepository
        )
    )
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val userAvatar by viewModel.userAvatar.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val croppedUri = UCrop.getOutput(result.data!!) ?: return@rememberLauncherForActivityResult
            val previousAvatar = userAvatar
            coroutineScope.launch {
                val path = withContext(Dispatchers.IO) {
                    copyUserAvatarToInternal(context, croppedUri)
                }
                if (path != null) {
                    previousAvatar?.let { File(it).takeIf { file -> file.exists() }?.delete() }
                    viewModel.setUserAvatar(path)
                }
            }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "设置") }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                SectionHeader(title = "个人")
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
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
                            avatar = userAvatar,
                            size = 96.dp,
                            fallbackIcon = Icons.Default.AccountCircle
                        )
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
                                contentDescription = "更换头像",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Text(
                        text = "我的头像",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "用于聊天中的用户消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (userAvatar != null) {
                        TextButton(
                            onClick = {
                                userAvatar?.let { File(it).takeIf { file -> file.exists() }?.delete() }
                                viewModel.setUserAvatar(null)
                            }
                        ) {
                            Text("移除头像")
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "外观")
            }
            item {
                ListItem(
                    title = "主题",
                    subtitle = getThemeLabel(themeMode),
                    icon = Icons.Default.Palette,
                    onClick = { showThemeDialog = true }
                )
            }

            item {
                SectionHeader(title = "数据")
            }
            item {
                ListItem(
                    title = "Messenger Cloud",
                    subtitle = viewModel.cloudUser.value?.email ?: "未登录 · ${viewModel.cloudServerUrl.value}",
                    icon = Icons.Default.Cloud,
                    onClick = { showCloudDialog = true }
                )
            }
            item {
                ListItem(
                    title = "模型提供商",
                    subtitle = "管理 AI 模型提供商和 API Key",
                    icon = Icons.Default.Cloud,
                    onClick = onProvidersClick
                )
            }
            item {
                ListItem(
                    title = "清除所有数据",
                    subtitle = "删除所有对话、Agent 和 Provider",
                    icon = Icons.Default.Delete,
                    titleColor = MaterialTheme.colorScheme.error,
                    subtitleColor = MaterialTheme.colorScheme.error,
                    iconColor = MaterialTheme.colorScheme.error,
                    onClick = { showClearDataDialog = true }
                )
            }

            item {
                SectionHeader(title = "关于")
            }
            item {
                ListItem(
                    title = "版本",
                    subtitle = getAppVersion(context),
                    icon = Icons.Default.Info,
                    showArrow = false,
                    onClick = null
                )
            }
            item {
                ListItem(
                    title = "开源许可",
                    subtitle = null,
                    icon = Icons.Default.Code,
                    onClick = onLicensesClick
                )
            }
        }

        if (showThemeDialog) {
            ThemePickerDialog(
                currentTheme = themeMode,
                onDismiss = { showThemeDialog = false },
                onConfirm = { mode ->
                    viewModel.setThemeMode(mode)
                    showThemeDialog = false
                }
            )
        }

        if (showClearDataDialog) {
            ConfirmationDialog(
                title = "清除所有数据",
                text = "此操作将删除所有对话、Agent、Provider 和相关数据。此操作不可撤销，确定要继续吗？",
                confirmButtonText = "清除",
                dismissButtonText = "取消",
                onConfirm = {
                    viewModel.clearAllData()
                    showClearDataDialog = false
                },
                onDismiss = { showClearDataDialog = false }
            )
        }

        if (showCloudDialog) {
            CloudAccountDialog(
                serverUrl = viewModel.cloudServerUrl.value,
                user = viewModel.cloudUser.value?.email,
                onDismiss = { showCloudDialog = false },
                onServerUrl = { value -> viewModel.setCloudServerUrl(value) {} },
                onLogin = { email, password, isRegister ->
                    if (isRegister) viewModel.register(email, password) {}
                    else viewModel.login(email, password) {}
                },
                onLogout = { viewModel.logout {} },
                onUpload = { viewModel.upload {} },
                onRestore = { viewModel.restore {} }
            )
        }
    }
}

@Composable
private fun CloudAccountDialog(
    serverUrl: String,
    user: String?,
    onDismiss: () -> Unit,
    onServerUrl: (String) -> Unit,
    onLogin: (String, String, Boolean) -> Unit,
    onLogout: () -> Unit,
    onUpload: () -> Unit,
    onRestore: () -> Unit
) {
    var url by remember(serverUrl) { mutableStateOf(serverUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Messenger Cloud") },
        text = {
            Column {
                OutlinedTextField(url, { url = it }, label = { Text("服务器地址") }, singleLine = true)
                TextButton(onClick = { url = DEFAULT_CLOUD_SERVER_URL }) { Text("使用默认服务器") }
                if (user == null) {
                    OutlinedTextField(email, { email = it }, label = { Text("邮箱") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true)
                    TextButton(onClick = { register = !register }) { Text(if (register) "已有账户？登录" else "没有账户？注册") }
                } else {
                    Text(user, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = {
                    onServerUrl(url)
                    if (user == null) onLogin(email, password, register) else onUpload()
                }) { Text(if (user == null) if (register) "注册并同步" else "登录并同步" else "上传备份") }
                if (user != null) {
                    TextButton(onClick = onRestore) { Text("从云端恢复") }
                    TextButton(onClick = onLogout) { Text("退出登录") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun copyUserAvatarToInternal(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "user_avatars").apply { mkdirs() }
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

private fun getThemeLabel(themeMode: ThemeMode): String {
    return when (themeMode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }
}

private fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = packageInfo.longVersionCode.toInt()
        "$versionName ($versionCode)"
    } catch (e: PackageManager.NameNotFoundException) {
        "未知"
    }
}
