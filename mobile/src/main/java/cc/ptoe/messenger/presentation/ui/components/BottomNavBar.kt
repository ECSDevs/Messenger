package cc.ptoe.messenger.presentation.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.ptoe.messenger.presentation.navigation.Screen

sealed class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Conversations : BottomNavItem(
        screen = Screen.Conversations,
        label = "对话",
        icon = Icons.Filled.ChatBubble
    )

    data object Agents : BottomNavItem(
        screen = Screen.Agents,
        label = "Agent",
        icon = Icons.Filled.SmartToy
    )

    data object Settings : BottomNavItem(
        screen = Screen.Settings,
        label = "设置",
        icon = Icons.Filled.Settings
    )

    companion object {
        val items = listOf(Conversations, Agents, Settings)
    }
}

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onItemClick: (BottomNavItem) -> Unit
) {
    NavigationBar {
        BottomNavItem.items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = { onItemClick(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(text = item.label)
                }
            )
        }
    }
}
