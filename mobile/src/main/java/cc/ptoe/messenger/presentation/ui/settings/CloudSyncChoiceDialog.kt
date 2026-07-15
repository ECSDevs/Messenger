package cc.ptoe.messenger.presentation.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            Text(
                "本地已有数据，云端也已有数据。请选择要保留的数据源。\n\n" +
                    "使用本地数据会覆盖云端；使用云端数据会覆盖本地。"
            )
        },
        confirmButton = {
            TextButton(onClick = onUseLocal) { Text("使用本地数据") }
        },
        dismissButton = {
            TextButton(onClick = onUseCloud) { Text("使用云端数据") }
        }
    )
}
