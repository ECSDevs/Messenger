package cc.ptoe.messenger.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Snapshot of the bridge's current connection state, surfaced to the UI so
 * the chat list can show a "waiting for phone" banner with the underlying
 * reason.
 */
sealed interface WearConnectionState {
    data object Disconnected : WearConnectionState
    data class Connecting(val detail: String) : WearConnectionState
    data object Connected : WearConnectionState
    data class Error(val message: String) : WearConnectionState
}

/**
 * Low-level Bluetooth RFCOMM client used by [WearBridgeClient] to talk to
 * [MobileBluetoothServer] on the phone.
 *
 * This transport bypasses the Wear OS DataLayer entirely, so it works on
 * Samsung China-region watches where Google Play Services for Wear OS is
 * missing or broken.
 *
 * The watch prefers paired devices from the system Bluetooth stack, but
 * falls back to active discovery when no paired device responds to our
 * service UUID — Samsung Galaxy Watches paired through the Samsung Wearable
 * app sometimes don't expose the phone through `bondedDevices`. One socket
 * is held open for the lifetime of the connection; [withRequest] serialises
 * request/response on a [Mutex].
 */
class WearBluetoothBridge(
    context: Context,
    @Suppress("unused") private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val mutex = Mutex()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var input: DataInputStream? = null
    @Volatile private var output: DataOutputStream? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow<WearConnectionState>(
        WearConnectionState.Disconnected
    )
    val connectionState: StateFlow<WearConnectionState> = _connectionState.asStateFlow()

    @SuppressLint("MissingPermission")
    suspend fun connect(): Boolean = mutex.withLock {
        if (_isConnected.value) return@withLock true
        val adapter = adapter ?: run {
            _connectionState.value = WearConnectionState.Error("此设备没有蓝牙模块")
            return@withLock false
        }
        if (!adapter.isEnabled) {
            _connectionState.value = WearConnectionState.Error("请先在手表设置里打开蓝牙")
            return@withLock false
        }

        val pairedDevices = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            _connectionState.value = WearConnectionState.Error("缺少 BLUETOOTH_CONNECT 权限")
            return@withLock false
        }

        // Phase 1: try already-paired devices.
        if (pairedDevices.isNotEmpty()) {
            _connectionState.value = WearConnectionState.Connecting(
                "尝试 ${pairedDevices.size} 个已配对设备…"
            )
            for (device in pairedDevices) {
                if (tryConnect(device)) {
                    _isConnected.value = true
                    _connectionState.value = WearConnectionState.Connected
                    return@withLock true
                }
            }
        } else {
            Log.w(TAG, "No paired Bluetooth devices")
        }

        // Phase 2: fall back to active discovery — Samsung Galaxy Watches
        // paired only through the Galaxy Wearable app often don't list the
        // phone in `bondedDevices`, but the phone IS discoverable over
        // standard Bluetooth.
        val discovered = discoverNearbyPhones(adapter)
        if (discovered.isNotEmpty()) {
            _connectionState.value = WearConnectionState.Connecting(
                "尝试 ${discovered.size} 个扫描到的设备…"
            )
            for (device in discovered) {
                if (tryConnect(device)) {
                    _isConnected.value = true
                    _connectionState.value = WearConnectionState.Connected
                    return@withLock true
                }
            }
        }

        val reason = when {
            pairedDevices.isEmpty() && discovered.isEmpty() ->
                "找不到手机 — 请先在系统蓝牙设置里把手表和手机配对"
            pairedDevices.isEmpty() ->
                "已配对但连不上 — 检查手机 Messenger 是否在前台运行"
            else ->
                "找不到 Messenger Wear 同步服务 — 检查手机端是否给了「附近设备」权限"
        }
        Log.w(TAG, reason)
        _connectionState.value = WearConnectionState.Error(reason)
        false
    }

    @SuppressLint("MissingPermission")
    private fun discoverNearbyPhones(adapter: BluetoothAdapter): List<BluetoothDevice> {
        val receiver = DiscoveryReceiver()
        return try {
            val filter = android.content.IntentFilter(BluetoothDevice.ACTION_FOUND)
            appContext.registerReceiver(receiver, filter)
            try {
                if (!adapter.startDiscovery()) {
                    Log.w(TAG, "startDiscovery() returned false")
                    return emptyList()
                }
                // Discovery is async; wait up to ~10s for any hits.
                val deadline = System.currentTimeMillis() + 10_000L
                while (System.currentTimeMillis() < deadline && receiver.found.isEmpty()) {
                    Thread.sleep(200L)
                }
                receiver.found.toList()
            } finally {
                runCatching { adapter.cancelDiscovery() }
                runCatching { appContext.unregisterReceiver(receiver) }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN permission for discovery", e)
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Discovery failed", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryConnect(device: BluetoothDevice): Boolean {
        return try {
            // cancelDiscovery() must be called before connect() per the AOSP docs
            // — otherwise connect() can block on the discovery socket.
            runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }
            val sock = device.createRfcommSocketToServiceRecord(WearSyncProtocol.SERVICE_UUID)
            sock.connect()
            socket = sock
            input = DataInputStream(sock.inputStream)
            output = DataOutputStream(sock.outputStream)
            Log.d(TAG, "Connected to ${device.address} (${device.name ?: "<unnamed>"})")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to connect to ${device.address}", e)
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            input = null
            output = null
            false
        }
    }

    suspend fun requestSync(): JSONObject? = withRequest {
        JSONObject().apply { put("type", "sync") }
    }

    suspend fun requestChat(
        requestId: String,
        conversationId: String,
        text: String
    ): JSONObject? = withRequest {
        JSONObject().apply {
            put("type", "chat")
            put("requestId", requestId)
            put("conversationId", conversationId)
            put("text", text)
        }
    }

    suspend fun requestNewConversation(
        requestId: String,
        agentId: String?
    ): JSONObject? = withRequest {
        JSONObject().apply {
            put("type", "new_conversation")
            put("requestId", requestId)
            if (!agentId.isNullOrBlank()) put("agentId", agentId)
        }
    }

    private suspend fun withRequest(buildRequest: () -> JSONObject): JSONObject? = mutex.withLock {
        if (!_isConnected.value) return@withLock null
        val out = output
        val inp = input
        if (out == null || inp == null) {
            closeInternal()
            return@withLock null
        }
        try {
            out.writeBytes(buildRequest().toString() + "\n")
            out.flush()
            val line = inp.readLine() ?: run {
                closeInternal()
                return@withLock null
            }
            JSONObject(line)
        } catch (e: Exception) {
            Log.w(TAG, "Request failed", e)
            closeInternal()
            null
        }
    }

    private fun closeInternal() {
        _isConnected.value = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
        _connectionState.value = WearConnectionState.Disconnected
    }

    /** Public force-close hook used by the polling loop on reconnect. */
    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
        _isConnected.value = false
        _connectionState.value = WearConnectionState.Disconnected
    }

    private class DiscoveryReceiver : android.content.BroadcastReceiver() {
        val found = mutableListOf<BluetoothDevice>()
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_FOUND) return
            val device = intent.getParcelableExtra<BluetoothDevice>(
                BluetoothDevice.EXTRA_DEVICE
            ) ?: return
            // Filter to plausible phones / computers — skip obvious headsets.
            val klass = intent.getIntExtra(BluetoothDevice.EXTRA_CLASS, -1)
            val name = device.name?.lowercase().orEmpty()
            val looksLikeHeadset = klass == 0x0404 || // CoD Major=4 Minor=4 (Headphones)
                name.contains("headset") ||
                name.contains("airpods") ||
                name.contains("buds")
            if (!looksLikeHeadset) {
                synchronized(found) { found.add(device) }
            }
        }
    }

    companion object {
        private const val TAG = "WearBluetoothBridge"
    }
}
