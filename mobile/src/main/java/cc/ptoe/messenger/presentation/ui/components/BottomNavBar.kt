package cc.ptoe.messenger.presentation.ui.components

import androidx.annotation.StringRes
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
import cc.ptoe.messenger.R
import cc.ptoe.messenger.presentation.navigation.Screen

sealed class BottomNavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Conversations : BottomNavItem(
        screen = Screen.Conversations,
        labelRes = R.string.bottom_nav_conversations,
        icon = Icons.Filled.ChatBubble
    )

    data object Agents : BottomNavItem(
        screen = Screen.Agents,
        labelRes = R.string.bottom_nav_agents,
        icon = Icons.Filled.SmartToy
    )

    data object Settings : BottomNavItem(
        screen = Screen.Settings,
        labelRes = R.string.bottom_nav_settings,
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
                        contentDescription = stringResource(item.labelRes)
                    )
                },
                label = {
                    Text(text = stringResource(item.labelRes))
                }
            )
        }
    }
}
