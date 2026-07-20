package cc.ptoe.messenger.presentation.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.R
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.ui.components.EmptyState
import cc.ptoe.messenger.presentation.ui.components.InputDialog
import cc.ptoe.messenger.presentation.ui.components.AgentAvatar
import cc.ptoe.messenger.presentation.ui.components.SingleChoiceDialog
import cc.ptoe.messenger.presentation.utils.DateTimeUtils
import cc.ptoe.messenger.presentation.viewmodel.ConversationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (String) -> Unit,
    viewModel: ConversationsViewModel = viewModel(
        factory = ConversationsViewModel.provideFactory(
            conversationRepository = MessengerApplication.instance.conversationRepository,
            messageRepository = MessengerApplication.instance.messageRepository,
            currentAgentRepository = MessengerApplication.instance.currentAgentRepository,
            agentRepository = MessengerApplication.instance.agentRepository,
            modelRepository = MessengerApplication.instance.modelRepository
        )
    )
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentAgent by viewModel.currentAgent.collectAsStateWithLifecycle()
    val allAgents by viewModel.allAgents.collectAsStateWithLifecycle()
    val showAllAgents by viewModel.showAllAgents.collectAsStateWithLifecycle()
    val agentsById = remember(allAgents) { allAgents.associateBy(Agent::id) }

    var showAgentDropdown by remember { mutableStateOf(false) }
    var renameConversationId by remember { mutableStateOf<String?>(null) }
    var deleteConversationId by remember { mutableStateOf<String?>(null) }
    var renameInitialTitle by remember { mutableStateOf("") }
    var showAgentPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showAgentDropdown = true }
                    ) {
                        Text(
                            text = if (showAllAgents) stringResource(R.string.conversations_all_agents) else (currentAgent?.name ?: stringResource(R.string.conversations_title)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.conversations_switch_agent)
                        )

                        DropdownMenu(
                            expanded = showAgentDropdown,
                            onDismissRequest = { showAgentDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversations_all_agents)) },
                                onClick = {
                                    viewModel.showAllAgents()
                                    showAgentDropdown = false
                                }
                            )
                            allAgents.forEach { agent ->
                                DropdownMenuItem(
                                    text = { Text(text = agent.name) },
                                    onClick = {
                                        viewModel.switchAgent(agent.id)
                                        showAgentDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (showAllAgents) {
                        showAgentPicker = true
                    } else {
                        viewModel.createNewConversation { conversationId ->
                            onConversationClick(conversationId)
                        }
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.conversations_new))
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ChatBubbleOutline,
                message = stringResource(R.string.conversations_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationListItem(
                        conversation = conversation,
                        avatar = agentsById[conversation.agentId]?.avatar,
                        onClick = { onConversationClick(conversation.id) },
                        onCloneClick = {
                            viewModel.cloneConversation(conversation.id, onConversationClick)
                        },
                        onRenameClick = {
                            renameInitialTitle = conversation.title
                            renameConversationId = conversation.id
                        },
                        onDeleteClick = {
                            deleteConversationId = conversation.id
                        }
                    )
                }
            }
        }

        if (renameConversationId != null) {
            InputDialog(
                title = stringResource(R.string.conversations_rename_title),
                initialValue = renameInitialTitle,
                hint = stringResource(R.string.conversations_rename_hint),
                confirmButtonText = stringResource(R.string.action_confirm),
                dismissButtonText = stringResource(R.string.action_cancel),
                onConfirm = { newTitle ->
                    renameConversationId?.let { id ->
                        viewModel.renameConversation(id, newTitle)
                    }
                    renameConversationId = null
                },
                onDismiss = { renameConversationId = null }
            )
        }

        if (deleteConversationId != null) {
            ConfirmationDialog(
                title = stringResource(R.string.conversations_delete_title),
                text = stringResource(R.string.conversations_delete_confirm),
                confirmButtonText = stringResource(R.string.action_delete),
                dismissButtonText = stringResource(R.string.action_cancel),
                onConfirm = {
                    deleteConversationId?.let { id ->
                        viewModel.deleteConversation(id)
                    }
                    deleteConversationId = null
                },
                onDismiss = { deleteConversationId = null }
            )
        }

        if (showAgentPicker) {
            SingleChoiceDialog(
                title = stringResource(R.string.conversations_select_agent_title),
                items = allAgents,
                initialSelectedId = currentAgent?.id ?: allAgents.firstOrNull()?.id,
                itemId = { it.id },
                itemLabel = { it.name },
                confirmButtonText = stringResource(R.string.action_confirm),
                dismissButtonText = stringResource(R.string.action_cancel),
                onConfirm = { agent ->
                    showAgentPicker = false
                    if (agent != null) {
                        viewModel.createNewConversation(agentId = agent.id) { conversationId ->
                            onConversationClick(conversationId)
                        }
                    }
                },
                onDismiss = { showAgentPicker = false }
            )
        }
    }
}

@Composable
private fun ConversationListItem(
    conversation: Conversation,
    avatar: String?,
    onClick: () -> Unit,
    onCloneClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AgentAvatar(
            avatar = avatar,
            size = 40.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DateTimeUtils.formatMessageTime(conversation.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conversation.lastMessage ?: stringResource(R.string.conversations_no_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.action_more)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_clone)) },
                    onClick = {
                        expanded = false
                        onCloneClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = {
                        expanded = false
                        onRenameClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        expanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}
