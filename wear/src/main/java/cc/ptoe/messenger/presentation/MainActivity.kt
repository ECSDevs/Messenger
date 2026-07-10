package cc.ptoe.messenger.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import cc.ptoe.messenger.WearMessengerApplication
import cc.ptoe.messenger.presentation.theme.MessengerTheme
import cc.ptoe.messenger.presentation.ui.chat.ChatScreen
import cc.ptoe.messenger.presentation.ui.chatlist.ChatListScreen
import cc.ptoe.messenger.presentation.viewmodel.WearChatViewModel
import cc.ptoe.messenger.presentation.viewmodel.WearScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) return@registerForActivityResult
            // If the user has chosen "don't ask again" there's nothing we can
            // do here — the chat list will surface the connection error with
            // the underlying reason.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
            ) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        requestBluetoothPermissionIfNeeded()

        val repository = (application as WearMessengerApplication).wearChatRepository

        setContent {
            val wearChatViewModel: WearChatViewModel = viewModel(
                factory = WearChatViewModel.provideFactory(repository)
            )
            MessengerTheme {
                WearChatApp(wearChatViewModel)
            }
        }
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val permission = Manifest.permission.BLUETOOTH_CONNECT
        if (ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothPermissionLauncher.launch(permission)
    }
}

@Composable
private fun WearChatApp(
    viewModel: WearChatViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.bannerMessage) {
        if (uiState.bannerMessage != null) {
            delay(3500)
            viewModel.dismissBanner()
        }
    }

    BackHandler(enabled = uiState.screen == WearScreen.Chat) {
        viewModel.navigateBackToList()
    }

    AppScaffold {
        when (uiState.screen) {
            WearScreen.ChatList -> {
                ChatListScreen(
                    uiState = uiState,
                    onChatOpen = viewModel::openChat,
                    onNewChat = { viewModel.createChat() },
                    onReconnect = { viewModel.requestReconnect() }
                )
            }
            WearScreen.Chat -> {
                ChatScreen(
                    uiState = uiState,
                    onBack = viewModel::navigateBackToList,
                    onSendMessage = viewModel::sendMessage
                )
            }
        }
    }
}
