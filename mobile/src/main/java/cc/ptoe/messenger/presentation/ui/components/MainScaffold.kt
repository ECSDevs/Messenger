package cc.ptoe.messenger.presentation.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.presentation.navigation.BottomLevelRoutes
import cc.ptoe.messenger.presentation.navigation.NavGraph
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MainScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val permanentlyDeniedFlow: StateFlow<Boolean> =
        MessengerApplication.instance.bluetoothPermissionPermanentlyDenied
    val permanentlyDenied: Boolean = permanentlyDeniedFlow.collectAsState().value

    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        destination.route in BottomLevelRoutes.routes
    } == true

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentDestination?.route,
                    onItemClick = { item ->
                        navController.navigate(item.screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(
                bottom = innerPadding.calculateBottomPadding()
            )
        ) {
            if (permanentlyDenied) {
                BluetoothPermissionBanner(
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                NavGraph(navController = navController)
            }
        }
    }
}

@Composable
private fun BluetoothPermissionBanner(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Wear 同步已关闭",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "需要「附近设备」权限才能连手表。请到系统设置里打开。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "去设置",
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
