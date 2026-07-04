package cc.ptoe.messenger.presentation.ui.providers

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.presentation.ui.components.ConfirmationDialog
import cc.ptoe.messenger.presentation.ui.components.EmptyState
import cc.ptoe.messenger.presentation.viewmodel.ProvidersViewModel
import cc.ptoe.messenger.presentation.viewmodel.ProviderWithModelCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onProviderClick: (String) -> Unit,
    viewModel: ProvidersViewModel = viewModel(
        factory = ProvidersViewModel.provideFactory(
            providerRepository = MessengerApplication.instance.providerRepository,
            modelRepository = MessengerApplication.instance.modelRepository
        )
    )
) {
    val providers by viewModel.providersWithModelCount.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )

    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "模型提供商") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "添加")
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (providers.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Cloud,
                message = "暂无提供商，点击右下角添加",
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
                items(providers, key = { it.provider.id }) { item ->
                    ProviderListItem(
                        item = item,
                        onClick = { onProviderClick(item.provider.id) },
                        onEditClick = { onEditClick(item.provider.id) },
                        onDeleteClick = { showDeleteDialog = item.provider.id }
                    )
                }
            }
        }

        if (showDeleteDialog != null) {
            ConfirmationDialog(
                title = "删除提供商",
                text = "确定要删除这个提供商吗？相关的模型也会被删除。",
                confirmButtonText = "删除",
                dismissButtonText = "取消",
                onConfirm = {
                    showDeleteDialog?.let { id ->
                        viewModel.deleteProvider(id)
                    }
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }
    }
}

@Composable
private fun ProviderListItem(
    item: ProviderWithModelCount,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
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
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.provider.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.provider.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.modelCount} 个模型 · 点击管理模型",
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
                    text = { Text("删除") },
                    onClick = {
                        expanded = false
                        onDeleteClick()
                    }
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "查看模型",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
