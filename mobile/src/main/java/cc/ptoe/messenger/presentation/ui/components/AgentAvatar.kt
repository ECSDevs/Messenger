package cc.ptoe.messenger.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun AgentAvatar(
    avatar: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val avatarFile = remember(avatar) { avatar?.takeIf { it.isNotBlank() }?.let { File(it) } }
    val isValid = avatarFile != null && avatarFile.exists()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (isValid) {
            AsyncImage(
                model = avatarFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(size * 0.6f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
