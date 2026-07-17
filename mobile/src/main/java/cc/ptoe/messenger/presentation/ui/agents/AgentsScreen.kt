package cc.ptoe.messenger.presentation.ui.agents

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.material3.SmallFloatingActionButton
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
import cc.ptoe.messenger.presentation.ui.components.AgentAvatar
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.ui.components.EmptyState
import cc.ptoe.messenger.presentation.viewmodel.AgentWithModel
import cc.ptoe.messenger.presentation.viewmodel.AgentsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    onAddClick: () -> Unit,
    onMarketClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onAgentClick: (String) -> Unit,
    viewModel: AgentsViewModel = viewModel(
        factory = AgentsViewModel.provideFactory(
            agentRepository = MessengerApplication.instance.agentRepository,
            modelRepository = MessengerApplication.instance.modelRepository
        )
    )
) {
    val agents by viewModel.agentsWithModel.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )

    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    val cloudUser by MessengerApplication.instance.cloudSyncRepository.user.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Agents") }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (cloudUser != null && fabExpanded) {
                    SmallFloatingActionButton(onClick = {
                        fabExpanded = false
                        onMarketClick()
                    }) {
                        Icon(imageVector = Icons.Default.Storefront, contentDescription = "Agent 市场")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SmallFloatingActionButton(onClick = {
                        fabExpanded = false
                        onAddClick()
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "新建 Agent")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                FloatingActionButton(onClick = {
                    if (cloudUser == null) onAddClick() else fabExpanded = !fabExpanded
                }) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (fabExpanded) "关闭操作菜单" else "添加"
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (agents.isEmpty()) {
            EmptyState(
                icon = Icons.Default.SmartToy,
                message = "暂无 Agent\n创建你的第一个 Agent 吧",
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
                items(agents, key = { it.agent.id }) { item ->
                    AgentListItem(
                        item = item,
                        onClick = { onAgentClick(item.agent.id) },
                        onEditClick = { onEditClick(item.agent.id) },
                        onCloneClick = { viewModel.cloneAgent(item.agent.id) },
                        onDeleteClick = {
                            if (!item.agent.isDefault) {
                                showDeleteDialog = item.agent.id
                            }
                        },
                        canDelete = !item.agent.isDefault
                    )
                }
            }
        }

        if (showDeleteDialog != null) {
            ConfirmationDialog(
                title = "删除 Agent",
                text = "确定要删除这个 Agent 吗？",
                confirmButtonText = "删除",
                dismissButtonText = "取消",
                onConfirm = {
                    showDeleteDialog?.let { id ->
                        viewModel.deleteAgent(id)
                    }
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }
    }
}

@Composable
private fun AgentListItem(
    item: AgentWithModel,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onCloneClick: () -> Unit,
    onDeleteClick: () -> Unit,
    canDelete: Boolean,
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
            avatar = item.agent.avatar,
            size = 40.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.agent.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.agent.isDefault) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "默认",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.agent.systemPrompt.ifBlank { "无系统提示词" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "模型: ${item.model?.displayName ?: "默认"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = { Text("编辑") },
                    onClick = {
                        expanded = false
                        onEditClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("克隆") },
                    onClick = {
                        expanded = false
                        onCloneClick()
                    }
                )
                if (canDelete) {
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
}
