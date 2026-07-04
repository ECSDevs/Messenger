package cc.ptoe.messenger.presentation.ui.conversations

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.domain.model.Conversation
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.ui.components.EmptyState
import cc.ptoe.messenger.presentation.ui.components.InputDialog
import cc.ptoe.messenger.presentation.utils.DateTimeUtils
import cc.ptoe.messenger.presentation.viewmodel.ConversationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (String) -> Unit,
    viewModel: ConversationsViewModel = viewModel(
        factory = ConversationsViewModel.provideFactory(
            conversationRepository = MessengerApplication.instance.conversationRepository,
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

    var showAgentDropdown by remember { mutableStateOf(false) }
    var renameConversationId by remember { mutableStateOf<String?>(null) }
    var deleteConversationId by remember { mutableStateOf<String?>(null) }
    var renameInitialTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showAgentDropdown = true }
                    ) {
                        Text(
                            text = if (showAllAgents) "全部 Agent" else (currentAgent?.name ?: "对话"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "切换 Agent"
                        )

                        DropdownMenu(
                            expanded = showAgentDropdown,
                            onDismissRequest = { showAgentDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部 Agent") },
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
                    viewModel.createNewConversation { conversationId ->
                        onConversationClick(conversationId)
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新建对话")
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ChatBubbleOutline,
                message = "暂无对话\n点击右下角开始新对话",
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
                        onClick = { onConversationClick(conversation.id) },
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
                title = "重命名对话",
                initialValue = renameInitialTitle,
                hint = "请输入新标题",
                confirmButtonText = "确定",
                dismissButtonText = "取消",
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
                title = "删除对话",
                text = "确定要删除这个对话吗？",
                confirmButtonText = "删除",
                dismissButtonText = "取消",
                onConfirm = {
                    deleteConversationId?.let { id ->
                        viewModel.deleteConversation(id)
                    }
                    deleteConversationId = null
                },
                onDismiss = { deleteConversationId = null }
            )
        }
    }
}

@Composable
private fun ConversationListItem(
    conversation: Conversation,
    onClick: () -> Unit,
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
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
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
                text = conversation.lastMessage ?: "暂无消息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多"
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        expanded = false
                        onRenameClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        expanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}
