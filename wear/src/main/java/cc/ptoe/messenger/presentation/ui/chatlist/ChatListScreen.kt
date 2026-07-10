package cc.ptoe.messenger.presentation.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.ptoe.messenger.presentation.ui.components.BannerMessage
import cc.ptoe.messenger.presentation.ui.components.WearAvatar
import cc.ptoe.messenger.presentation.viewmodel.WearChatListItem
import cc.ptoe.messenger.presentation.viewmodel.WearChatUiState

@Composable
fun ChatListScreen(
    uiState: WearChatUiState,
    onChatOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Chats",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            textAlign = TextAlign.Center
        )

        if (uiState.bannerMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            BannerMessage(uiState.bannerMessage)
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (uiState.chats.isEmpty()) {
            EmptyChatList(
                hasAgents = uiState.agents.isNotEmpty(),
                isCreatingChat = uiState.isCreatingChat,
                onNewChat = onNewChat,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.chats, key = { it.conversation.id }) { chat ->
                    ChatListItem(
                        chat = chat,
                        onClick = { onChatOpen(chat.conversation.id) }
                    )
                }
                item(key = "new_chat") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onNewChat,
                        enabled = !uiState.isCreatingChat && uiState.agents.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isCreatingChat) "Creating..." else "New chat")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(
    chat: WearChatListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conversation = chat.conversation
    val agent = chat.agent
    val preview = conversation.lastMessage?.ifBlank { null } ?: "No messages yet"
    val isReady = agent?.isReady != false

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            WearAvatar(
                avatarPath = agent?.avatarPath,
                fallbackText = agent?.name ?: conversation.title,
                size = 36.dp
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isReady) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title.ifBlank { agent?.name ?: "Chat" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (agent != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyChatList(
    hasAgents: Boolean,
    isCreatingChat: Boolean,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasAgents) "No chats yet" else "Waiting for phone",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasAgents) {
                "Start a chat to message from your watch."
            } else {
                "Open Messenger on your phone to sync conversations automatically."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (hasAgents) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onNewChat,
                enabled = !isCreatingChat
            ) {
                Text(if (isCreatingChat) "Creating..." else "New chat")
            }
        }
    }
}
