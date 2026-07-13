package cc.ptoe.messenger.presentation.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.presentation.ui.components.AgentAvatar
import cc.ptoe.messenger.presentation.utils.DateTimeUtils

@Composable
fun UserMessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isLastInGroup: Boolean = true,
    avatar: String? = null
) {
    val isError = message.status == MessageStatus.ERROR
    val bubbleColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Google Messages 风格：仅当组内最后一条消息时显示尾巴（右下角小尖角）
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = if (isLastInGroup) 4.dp else 18.dp
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // The bubble is wrapped in a weighted Box so the avatar (non-weighted)
            // is measured first and always gets its full 32dp. Without this, a long
            // message consumes all the row width during the bubble's measurement and
            // squeezes the avatar down to nothing. The Box aligns the bubble to its
            // end so it stays flush against the avatar on the right.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = message.content,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // 行内状态指示（Google Messages 风格：放气泡右下角，半透明）
                    Spacer(modifier = Modifier.width(8.dp))
                    MessageStatusIndicator(message = message, tint = Color.White.copy(alpha = 0.75f))
                }
            }
            if (isLastInGroup) {
                Spacer(modifier = Modifier.width(8.dp))
                AgentAvatar(
                    avatar = avatar,
                    size = 32.dp,
                    fallbackIcon = Icons.Default.AccountCircle
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        if (isLastInGroup) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateTimeUtils.formatMessageTime(message.timestamp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 36.dp)
            )
        }
    }
}

@Composable
private fun MessageStatusIndicator(message: Message, tint: Color) {
    when (message.status) {
        MessageStatus.SENDING -> Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = tint
        )
        MessageStatus.SENT -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint
        )
        MessageStatus.ERROR -> Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint
        )
    }
}
