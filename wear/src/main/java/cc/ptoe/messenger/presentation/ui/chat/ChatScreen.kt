package cc.ptoe.messenger.presentation.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import cc.ptoe.messenger.data.WearAgent
import cc.ptoe.messenger.data.WearConversation
import cc.ptoe.messenger.presentation.ui.components.BannerMessage
import cc.ptoe.messenger.presentation.ui.components.DraftInput
import cc.ptoe.messenger.presentation.ui.components.MessageBubble
import cc.ptoe.messenger.presentation.ui.components.verticalRotaryScroll
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

    // reverseLayout: index 0 = 底部（最新消息）；向上滚动 index 增大 = 更早的历史
    val conversationId = selectedConversation?.id
    var hasInitiallyScrolled by remember(conversationId) { mutableStateOf(false) }
    var previousItemCount by remember(conversationId) { mutableIntStateOf(0) }

    // 进入会话时滚动到底部（reverseLayout 下 index 0）
    LaunchedEffect(conversationId, uiState.messages.isNotEmpty()) {
        if (!hasInitiallyScrolled && uiState.messages.isNotEmpty()) {
            hasInitiallyScrolled = true
            listState.scrollToItem(0)
        }
    }

    // 流式跟踪：用户停留在底部附近时跟随最新消息；上滑查看历史时不打扰
    LaunchedEffect(uiState.messages, uiState.isSending) {
        if (!hasInitiallyScrolled || uiState.messages.isEmpty()) return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 2) {
            // 新增消息用动画滚动，流式内容更新用瞬时滚动避免动画堆叠
            if (uiState.messages.size != previousItemCount) {
                listState.animateScrollToItem(0)
            } else {
                listState.scrollToItem(0)
            }
        }
        previousItemCount = uiState.messages.size
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp)
        ) {
            if (uiState.bannerMessage != null) {
                BannerMessage(uiState.bannerMessage)
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (selectedAgent?.isReady == false) {
                BannerMessage("Set a model for this agent on your phone before chatting.")
                Spacer(modifier = Modifier.height(6.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.messages.isEmpty()) {
                    EmptyConversation(
                        conversation = selectedConversation,
                        agent = selectedAgent
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalRotaryScroll(listState)
                    ) {
                        // reverseLayout 下传入反转列表，使最新消息位于 index 0（底部）
                        // LazyColumn 仅组合可见项，向上滚动时才组合更早的历史消息
                        items(uiState.messages.asReversed(), key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = if (uiState.isSending) "..." else ">",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        ScrollIndicator(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
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
