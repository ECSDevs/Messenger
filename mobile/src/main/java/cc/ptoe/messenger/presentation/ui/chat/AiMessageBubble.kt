package cc.ptoe.messenger.presentation.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.presentation.utils.DateTimeUtils

@Composable
fun AiMessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isGenerating: Boolean = false,
    onRetryClick: (() -> Unit)? = null
) {
    val bubbleColor = if (message.status == MessageStatus.ERROR) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (message.status == MessageStatus.ERROR) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .padding(start = 8.dp, end = 48.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (message.status == MessageStatus.ERROR) {
                    Text(
                        text = "发送失败",
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (!message.errorMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.errorMessage,
                            color = textColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (onRetryClick != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            IconButton(
                                onClick = onRetryClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "重试",
                                    tint = textColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (message.status == MessageStatus.SENDING && isGenerating) {
                        TypingIndicator()
                    }
                } else {
                    TypingIndicator()
                }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateTimeUtils.formatMessageTime(message.timestamp),
                        color = if (message.status == MessageStatus.ERROR) {
                            textColor.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot1Alpha),
                    shape = RoundedCornerShape(50)
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot2Alpha),
                    shape = RoundedCornerShape(50)
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot3Alpha),
                    shape = RoundedCornerShape(50)
                )
        )
    }
}
