package cc.ptoe.messenger.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.ptoe.messenger.data.WearChatMessage
import cc.ptoe.messenger.data.WearMessageRole
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/**
 * Binds a [LazyListState] to the watch bezel / crown so the user can scroll
 * lists without swiping. Follows the same pattern as TGwear: focus is grabbed
 * when the screen is active via [requestFocusOnHierarchyActive], then an
 * [onRotaryScrollEvent] handler delegates to [LazyListState.scrollBy].
 */
@OptIn(ExperimentalWearFoundationApi::class)
fun Modifier.verticalRotaryScroll(
    state: LazyListState
): Modifier = composed {
    val scope = rememberCoroutineScope()
    focusable()
        .requestFocusOnHierarchyActive()
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels) }
            true
        }
}

@Composable
fun WearAvatar(
    avatarPath: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val avatarFile = remember(avatarPath) {
        avatarPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    }
    val initial = fallbackText.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (avatarFile != null) {
            AsyncImage(
                model = avatarFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun BannerMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MessageBubble(
    message: WearChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == WearMessageRole.USER
    val background = when {
        isUser -> MaterialTheme.colorScheme.primary
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isUser) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 4.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(shape)
                .background(background)
                .then(
                    if (message.isPending) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            shape = shape
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = when {
                    message.isPending -> "Thinking..."
                    message.content.isBlank() -> "No response."
                    else -> message.content
                },
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DraftInput(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = false,
        maxLines = 3,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = { onSend() }
        ),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = placeholderColor
                    )
                }
                innerTextField()
            }
        }
    )
}
