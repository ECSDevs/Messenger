package cc.ptoe.messenger.presentation.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.ptoe.messenger.domain.model.MessageRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    messageRole: MessageRole,
    onCopyClick: () -> Unit,
    onRegenerateClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            ListItem(
                headlineContent = { Text("复制") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onCopyClick()
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDismiss()
                    }
                }
            )
            if (messageRole == MessageRole.ASSISTANT) {
                ListItem(
                    headlineContent = { Text("重新生成") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        onRegenerateClick()
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismiss()
                        }
                    }
                )
            }
            ListItem(
                headlineContent = { Text("删除") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onDeleteClick()
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDismiss()
                    }
                }
            )
        }
    }
}
