package cc.ptoe.messenger.presentation.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.ptoe.messenger.data.WearAgent
import cc.ptoe.messenger.data.WearConversation
import cc.ptoe.messenger.presentation.ui.components.BannerMessage
import cc.ptoe.messenger.presentation.ui.components.DraftInput
import cc.ptoe.messenger.presentation.ui.components.MessageBubble
import cc.ptoe.messenger.presentation.ui.components.WearAvatar
import cc.ptoe.messenger.presentation.viewmodel.WearChatUiState

@Composable
fun ChatScreen(
    uiState: WearChatUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    val selectedAgent = uiState.selectedAgent
    val selectedConversation = uiState.selectedConversation
    val canSend = selectedAgent?.isReady == true && !uiState.isSending

    LaunchedEffect(uiState.messages.size, selectedConversation?.id) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        ChatTopBar(
            conversation = selectedConversation,
            agent = selectedAgent,
            onBack = onBack
        )

        if (uiState.bannerMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            BannerMessage(uiState.bannerMessage)
        }

        if (selectedAgent?.isReady == false) {
            Spacer(modifier = Modifier.height(6.dp))
            BannerMessage("Set a model for this agent on your phone before chatting.")
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            if (uiState.messages.isEmpty()) {
                EmptyConversation(
                    conversation = selectedConversation,
                    agent = selectedAgent
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            agentAvatarPath = selectedAgent?.avatarPath,
                            userAvatarPath = uiState.userAvatarPath,
                            agentName = selectedAgent?.name ?: "AI"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DraftInput(
                value = draft,
                enabled = selectedConversation != null && !uiState.isSending,
                onValueChange = { draft = it },
                onSend = {
                    if (draft.isBlank() || !canSend) return@DraftInput
                    val message = draft.trim()
                    draft = ""
                    onSendMessage(message)
                },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (draft.isBlank() || !canSend) return@Button
                    val message = draft.trim()
                    draft = ""
                    onSendMessage(message)
                },
                enabled = canSend && draft.isNotBlank(),
                modifier = Modifier.size(40.dp)
            ) {
                Text(
                    text = if (uiState.isSending) "..." else "➤",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    conversation: WearConversation?,
    agent: WearAgent?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        WearAvatar(
            avatarPath = agent?.avatarPath,
            fallbackText = agent?.name ?: conversation?.title ?: "Chat",
            size = 28.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation?.title?.ifBlank { null } ?: agent?.name ?: "Chat",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    agent == null -> "Syncing..."
                    agent.isReady -> agent.name
                    else -> "Model required"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (agent?.isReady == false) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyConversation(
    conversation: WearConversation?,
    agent: WearAgent?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = conversation?.title?.ifBlank { null }
                ?: agent?.let { "Chat with ${it.name}" }
                ?: "Chat",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Say hello to start the conversation.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
