package cc.ptoe.messenger.presentation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cc.ptoe.messenger.presentation.theme.ThemeMode

data class ThemeOption(
    val mode: ThemeMode,
    val label: String,
    val icon: ImageVector
)

@Composable
fun ThemePickerDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onConfirm: (ThemeMode) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }

    val themeOptions = listOf(
        ThemeOption(ThemeMode.SYSTEM, "跟随系统", Icons.Default.SettingsBrightness),
        ThemeOption(ThemeMode.LIGHT, "浅色", Icons.Default.LightMode),
        ThemeOption(ThemeMode.DARK, "深色", Icons.Default.DarkMode)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "选择主题")
        },
        text = {
            Column {
                themeOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTheme = option.mode }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTheme == option.mode,
                            onClick = { selectedTheme = option.mode }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTheme) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
