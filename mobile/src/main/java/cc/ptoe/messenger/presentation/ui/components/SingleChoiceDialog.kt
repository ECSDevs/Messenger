package cc.ptoe.messenger.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp

@Composable
fun <T> SingleChoiceDialog(
    title: String,
    items: List<T>,
    initialSelectedId: String? = null,
    itemId: (T) -> String,
    itemLabel: (T) -> String,
    confirmButtonText: String,
    dismissButtonText: String,
    onConfirm: (T?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(initialSelectedId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { item ->
                    val id = itemId(item)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = id == selectedId,
                                onClick = { selectedId = id }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = id == selectedId,
                            onClick = { selectedId = id }
                        )
                        Text(
                            text = itemLabel(item),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = items.firstOrNull { itemId(it) == selectedId }
                    onConfirm(selected)
                },
                enabled = selectedId != null
            ) {
                Text(text = confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissButtonText)
            }
        }
    )
}
