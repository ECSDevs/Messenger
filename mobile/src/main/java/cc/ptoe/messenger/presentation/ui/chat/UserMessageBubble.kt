package cc.ptoe.messenger.presentation.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ptoe.messenger.domain.model.Message
import cc.ptoe.messenger.domain.model.MessageStatus
import cc.ptoe.messenger.presentation.utils.DateTimeUtils

@Composable
fun UserMessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isError = message.status == MessageStatus.ERROR
    val bubbleColor = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .padding(start = 48.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateTimeUtils.formatMessageTime(message.timestamp),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    when (message.status) {
                        MessageStatus.SENDING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        MessageStatus.SENT -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        MessageStatus.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
