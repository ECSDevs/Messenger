package cc.ptoe.messenger.presentation.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.presentation.ui.components.AgentAvatar
import cc.ptoe.messenger.presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.provideFactory(
            messageRepository = MessengerApplication.instance.messageRepository,
            conversationRepository = MessengerApplication.instance.conversationRepository,
            agentRepository = MessengerApplication.instance.agentRepository,
            apiRepository = MessengerApplication.instance.apiRepository,
            modelRepository = MessengerApplication.instance.modelRepository,
            providerRepository = MessengerApplication.instance.providerRepository
        )
    )
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val agent by viewModel.agent.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val needsModelSetup by viewModel.needsModelSetup.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var selectedMessageRole by remember { mutableStateOf(MessageRole.USER) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
            }
            viewModel.clearError()
        }
    }

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            val firstVisibleIndex = listState.firstVisibleItemIndex
            if (firstVisibleIndex <= lastIndex - 3) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AgentAvatar(
                            avatar = agent?.avatar,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = conversation?.title ?: "对话",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = agent?.name ?: "",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "对话设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSendClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    onStopClick = {
                        viewModel.stopGeneration()
                    },
                    isGenerating = isGenerating
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            if (messages.isEmpty()) {
                EmptyChatState(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = false,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        val bubbleModifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (message.status == MessageStatus.ERROR && message.role == MessageRole.ASSISTANT) {
                                        viewModel.retrySend(message.id)
                                    }
                                },
                                onLongClick = {
                                    selectedMessageId = message.id
                                    selectedMessageRole = message.role
                                    showActionMenu = true
                                }
                            )
                            .padding(vertical = 2.dp)

                        when (message.role) {
                            MessageRole.USER -> {
                                UserMessageBubble(
                                    message = message,
                                    modifier = bubbleModifier
                                )
                            }
                            MessageRole.ASSISTANT -> {
                                AiMessageBubble(
                                    message = message,
                                    isGenerating = isGenerating,
                                    onRetryClick = {
                                        if (message.status == MessageStatus.ERROR) {
                                            viewModel.retrySend(message.id)
                                        }
                                    },
                                    modifier = bubbleModifier
                                )
                            }
                            MessageRole.SYSTEM -> {}
                        }
                    }
                }
            }
        }
    }

    MessageActionMenu(
        isVisible = showActionMenu,
        onDismiss = {
            showActionMenu = false
            selectedMessageId = null
        },
        messageRole = selectedMessageRole,
        onCopyClick = {
            val message = messages.find { it.id == selectedMessageId }
            if (message != null) {
                viewModel.copyMessage(context, message.content)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        },
        onRegenerateClick = {
            selectedMessageId?.let {
                viewModel.regenerateMessage(it)
            }
        },
        onDeleteClick = {
            selectedMessageId?.let {
                viewModel.deleteMessage(it)
            }
        }
    )

    if (needsModelSetup) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelSetupPrompt() },
            title = { Text("未设置模型") },
            text = { Text("当前 Agent 未设置模型，请先设置模型后再开始聊天。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissModelSetupPrompt()
                    onSettingsClick()
                }) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissModelSetupPrompt() }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "💬",
                fontSize = 36.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "开始对话吧",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "输入消息开始与 AI 聊天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
