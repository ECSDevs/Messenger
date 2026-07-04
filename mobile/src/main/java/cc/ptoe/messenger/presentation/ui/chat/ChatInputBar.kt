package cc.ptoe.messenger.presentation.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconButton(
            onClick = { },
            modifier = Modifier
                .padding(end = 4.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(text = "输入消息...")
            },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            maxLines = 5,
            minLines = 1,
            enabled = !isGenerating
        )

        if (isGenerating) {
            IconButton(
                onClick = onStopClick,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "停止",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            IconButton(
                onClick = onSendClick,
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
        }
    }
}
