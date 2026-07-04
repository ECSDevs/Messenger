package cc.ptoe.messenger.presentation.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.presentation.theme.ThemeMode
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.ui.components.ListItem
import cc.ptoe.messenger.presentation.ui.components.SectionHeader
import cc.ptoe.messenger.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onProvidersClick: () -> Unit,
    onLicensesClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            themePreferences = MessengerApplication.instance.themePreferences
        )
    )
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

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
