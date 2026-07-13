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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
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
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageRole
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.presentation.ui.components.AgentAvatar
import cc.ptoe.messenger.presentation.utils.DateTimeUtils
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
    val streamingMessageId by viewModel.streamingMessageId.collectAsStateWithLifecycle()
    val userAvatar by MessengerApplication.instance.appPreferences.userAvatar.collectAsStateWithLifecycle(initialValue = null)

    var inputText by remember { mutableStateOf("") }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var selectedMessageRole by remember { mutableStateOf(MessageRole.USER) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 是否已完成初次滚动到底部（切换会话时重置）
    var hasInitiallyScrolled by remember(conversationId) { mutableStateOf(false) }
    // 上一次列表项数量，用于区分"新增消息"与"流式内容更新"
    var previousItemCount by remember(conversationId) { mutableIntStateOf(0) }

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

    // 预构建列表项（消息 + 日期分隔符），保持时间正序；
    // 配合 LazyColumn 的 reverseLayout，展示时反转使最新消息位于底部（index 0）
    val chatItems = remember(messages) { buildChatItems(messages) }

    // 进入聊天页时默认滑动到底部（reverseLayout 下 index 0 即底部）
    LaunchedEffect(chatItems.size) {
        if (!hasInitiallyScrolled && chatItems.isNotEmpty()) {
            hasInitiallyScrolled = true
            listState.scrollToItem(0)
        }
    }

    // 自动保持底部：消息更新或生成状态变化时，若用户未主动上滑则跟随到底部
    // reverseLayout 下 firstVisibleItemIndex 越小越靠近底部；0 表示完全在底部
    LaunchedEffect(messages, isGenerating) {
        if (!hasInitiallyScrolled || chatItems.isEmpty()) return@LaunchedEffect
        // 用户停留在底部附近（容差 3 项）时自动跟随到最新消息；
        // 用户主动上滑远离底部时则停止跟随，便于查看历史消息
        if (listState.firstVisibleItemIndex <= 3) {
            // 新增消息用动画滚动，流式内容更新用瞬时滚动以避免动画堆叠卡顿
            if (chatItems.size != previousItemCount) {
                listState.animateScrollToItem(0)
            } else {
                listState.scrollToItem(0)
            }
        }
        previousItemCount = chatItems.size
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
                                fontSize = 18.sp,
                                maxLines = 1
                            )
                            Text(
                                text = agent?.name ?: "",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
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
            // Google Messages 风格：底部输入栏与顶栏同色，无分隔线
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
            ) {
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
        // Google Messages 风格：聊天区使用纯净的 surface 背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (messages.isEmpty()) {
                EmptyChatState(
                    avatar = agent?.avatar,
                    agentName = agent?.name,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = chatItems.asReversed(),
                        key = { item ->
                            when (item) {
                                is ChatListItem.DateSeparator -> "date_${item.id}"
                                is ChatListItem.MessageItem -> "msg_${item.message.id}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is ChatListItem.DateSeparator -> {
                                DateSeparator(timestamp = item.timestamp)
                            }
                            is ChatListItem.MessageItem -> {
                                val message = item.message
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

                                when (message.role) {
                                    MessageRole.USER -> {
                                        UserMessageBubble(
                                            message = message,
                                            modifier = bubbleModifier,
                                            isLastInGroup = item.isLastInGroup,
                                            avatar = userAvatar
                                        )
                                    }
                                    MessageRole.ASSISTANT -> {
                                        AiMessageBubble(
                                            message = message,
                                            isGenerating = isGenerating,
                                            isLastInGroup = item.isLastInGroup,
                                            avatar = agent?.avatar,
                                            onRetryClick = {
                                                if (message.status == MessageStatus.ERROR) {
                                                    viewModel.retrySend(message.id)
                                                }
                                            },
                                            typewriterState = viewModel.typewriterState,
                                            streamingMessageId = streamingMessageId,
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

/**
 * 聊天列表项：日期分隔符或消息
 */
private sealed class ChatListItem {
    data class DateSeparator(
        val id: String,
        val timestamp: Long
    ) : ChatListItem()

    data class MessageItem(
        val message: Message,
        val isLastInGroup: Boolean
    ) : ChatListItem()
}

/**
 * 构建聊天列表项：在每天首条消息前插入日期分隔符（Google Messages 风格）
 * 同时计算每条消息是否为同发送者组内的最后一条（用于气泡尾巴样式）
 */
private fun buildChatItems(messages: List<Message>): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()

    val items = mutableListOf<ChatListItem>()
    var lastDay: Long? = null

    messages.forEachIndexed { index, message ->
        // 日期分隔符
        if (lastDay == null || !DateTimeUtils.isSameDay(lastDay, message.timestamp)) {
            items.add(ChatListItem.DateSeparator(id = message.id, timestamp = message.timestamp))
            lastDay = message.timestamp
        }
        // 是否为组内最后一条：下一条不存在或角色不同
        val isLastInGroup = index == messages.lastIndex ||
            messages[index + 1].role != message.role
        items.add(ChatListItem.MessageItem(message = message, isLastInGroup = isLastInGroup))
    }

    return items
}

@Composable
private fun DateSeparator(timestamp: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = DateTimeUtils.formatDateSeparator(timestamp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyChatState(
    avatar: String?,
    agentName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AgentAvatar(avatar = avatar, size = 72.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = agentName?.takeIf { it.isNotBlank() } ?: "开始对话吧",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "输入消息开始与 AI 聊天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
