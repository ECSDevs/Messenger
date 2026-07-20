package cc.ptoe.messenger.presentation.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.ptoe.messenger.R
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
        title = { Text(stringResource(R.string.sync_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sync_choice_message))
                Text(
                    stringResource(R.string.sync_choice_warning),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUseLocal) { Text(stringResource(R.string.sync_choice_use_local)) }
        },
        dismissButton = {
            TextButton(onClick = onUseCloud) { Text(stringResource(R.string.sync_choice_use_cloud)) }
        }
    )
}
