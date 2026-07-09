package cc.ptoe.messenger.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.ptoe.messenger.WearMessengerApplication
import cc.ptoe.messenger.data.WearAgent
import cc.ptoe.messenger.data.WearChatMessage
import cc.ptoe.messenger.data.WearMessageRole
import cc.ptoe.messenger.presentation.theme.MessengerTheme
import cc.ptoe.messenger.presentation.viewmodel.WearChatUiState
import cc.ptoe.messenger.presentation.viewmodel.WearChatViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as WearMessengerApplication).wearChatRepository

        setContent {
            val wearChatViewModel: WearChatViewModel = viewModel(
                factory = WearChatViewModel.provideFactory(repository)
            )
            MessengerTheme {
                WearChatApp(wearChatViewModel)
            }
        }
    }
}

@Composable
private fun WearChatApp(
    viewModel: WearChatViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.bannerMessage) {
        if (uiState.bannerMessage != null) {
            delay(3500)
            viewModel.dismissBanner()
        }
    }

    AppScaffold {
        WearChatScreen(
            uiState = uiState,
            onAgentSelected = viewModel::selectAgent,
            onRefreshAgents = viewModel::refreshAgents,
            onSendMessage = viewModel::sendMessage
        )
    }
}

@Composable
private fun WearChatScreen(
    uiState: WearChatUiState,
    onAgentSelected: (String) -> Unit,
    onRefreshAgents: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    val selectedAgent = uiState.selectedAgent
    val canSend = selectedAgent?.isReady == true && !uiState.isSending

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Messenger",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedAgent?.name ?: "Phone-backed Wear chat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.agents.isEmpty()) {
                EmptyState(
                    isSyncing = uiState.isSyncing,
                    onRefreshAgents = onRefreshAgents
                )
                return@Column
            }

            AgentStrip(
                agents = uiState.agents,
                selectedAgentId = selectedAgent?.id,
                onAgentSelected = onAgentSelected
            )

            if (uiState.bannerMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                BannerMessage(uiState.bannerMessage)
            }

            if (selectedAgent?.isReady == false) {
                Spacer(modifier = Modifier.height(8.dp))
                BannerMessage("Set a model for this agent on your phone before chatting.")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    EmptyConversation(selectedAgent = selectedAgent)
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            DraftInput(
                value = draft,
                enabled = selectedAgent != null && !uiState.isSending,
                onValueChange = { draft = it },
                onSend = {
                    if (draft.isBlank() || !canSend) return@DraftInput
                    val message = draft.trim()
                    draft = ""
                    onSendMessage(message)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRefreshAgents,
                    enabled = !uiState.isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.isSyncing) "Syncing" else "Sync")
                }
                Button(
                    onClick = {
                        if (draft.isBlank() || !canSend) return@Button
                        val message = draft.trim()
                        draft = ""
                        onSendMessage(message)
                    },
                    enabled = canSend && draft.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.isSending) "Sending" else "Send")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    isSyncing: Boolean,
    onRefreshAgents: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sync your agents from the phone to start chatting.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onRefreshAgents,
            enabled = !isSyncing
        ) {
            Text(if (isSyncing) "Syncing" else "Sync Agents")
        }
    }
}

@Composable
private fun AgentStrip(
    agents: List<WearAgent>,
    selectedAgentId: String?,
    onAgentSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(agents, key = { it.id }) { agent ->
            val selected = agent.id == selectedAgentId
            val background = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onAgentSelected(agent.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = agent.name,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium
                )
                if (!agent.isReady) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyConversation(selectedAgent: WearAgent?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = selectedAgent?.let { "Start chatting with ${it.name}." } ?: "Pick an agent to begin.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MessageBubble(message: WearChatMessage) {
    val isUser = message.role == WearMessageRole.USER
    val background = when {
        isUser -> MaterialTheme.colorScheme.primary
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .then(
                    if (message.isPending) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(18.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = when {
                    message.isPending -> "Thinking..."
                    message.content.isBlank() -> "No response."
                    else -> message.content
                },
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DraftInput(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = false,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = { onSend() }
        ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = placeholderColor
                    )
                }
                innerTextField()
            }
        }
    )
}
