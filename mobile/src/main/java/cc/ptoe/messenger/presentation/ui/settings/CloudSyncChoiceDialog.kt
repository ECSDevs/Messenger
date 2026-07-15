package cc.ptoe.messenger.presentation.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import cc.ptoe.messenger.data.cloud.CloudLoginOutcome

@Composable
fun CloudSyncChoiceDialog(
    outcome: CloudLoginOutcome,
    onUseLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择同步方向") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("本地和云端都已有数据，请选择要保留的数据源。")
                Text(
                    "使用本地数据会覆盖云端；使用云端数据会覆盖本地。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUseLocal) { Text("使用本地数据") }
        },
        dismissButton = {
            TextButton(onClick = onUseCloud) { Text("使用云端数据") }
        }
    )
}
