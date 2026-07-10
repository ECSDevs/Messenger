package cc.ptoe.messenger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import cc.ptoe.messenger.presentation.theme.MessengerTheme
import cc.ptoe.messenger.presentation.theme.ThemeMode
import cc.ptoe.messenger.presentation.ui.components.MainScaffold

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                MessengerApplication.instance.markBluetoothPermissionGranted()
                MessengerApplication.instance.ensureBluetoothSyncRunning()
                return@registerForActivityResult
            }
            // If the system still allows the rationale to be shown, just
            // re-request (the user hasn't picked "don't ask again" yet).
            // Otherwise the permission is permanently denied and we surface
            // a banner with a deep link to system app settings.
            val permission = Manifest.permission.BLUETOOTH_CONNECT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                shouldShowRequestPermissionRationale(permission)
            ) {
                bluetoothPermissionLauncher.launch(permission)
            } else {
                MessengerApplication.instance.markBluetoothPermissionPermanentlyDenied()
            }
        }
        requestBluetoothPermissionIfNeeded()
        setContent {
            val themeMode by MessengerApplication.instance.themePreferences.themeMode
                .collectAsState(initial = ThemeMode.SYSTEM)

            MessengerTheme(themeMode = themeMode) {
                MainScaffold()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have toggled the permission in system settings and come
        // back. Re-check and start the service if it's now granted.
        if (hasBluetoothConnectPermission()) {
            MessengerApplication.instance.markBluetoothPermissionGranted()
            MessengerApplication.instance.ensureBluetoothSyncRunning()
        }
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            MessengerApplication.instance.ensureBluetoothSyncRunning()
            return
        }
        val permission = Manifest.permission.BLUETOOTH_CONNECT
        if (ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            MessengerApplication.instance.markBluetoothPermissionGranted()
            MessengerApplication.instance.ensureBluetoothSyncRunning()
            return
        }
        bluetoothPermissionLauncher.launch(permission)
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
