package cc.ptoe.messenger.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
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

    /**
     * MAC addresses of devices that just failed to connect — we skip them
     * for a while so the polling loop doesn't hammer a non-phone (e.g. a
     * Bluetooth headset) every 3 s. Entries auto-expire after
     * [SKIP_DURATION_MS].
     */
    private val recentlyFailed = mutableMapOf<String, Long>()

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

        val rawPaired = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            _connectionState.value = WearConnectionState.Error("缺少 BLUETOOTH_CONNECT 权限")
            return@withLock false
        }
        // Drop devices we just failed on, so we don't immediately retry the
        // same broken pair.
        val pairedDevices = rawPaired.filterNot { isRecentlyFailed(it) }
        val phoneLike = pairedDevices.filter { isProbablyPhoneOrComputer(it) }
        logPairedDevices(rawPaired, phoneLike)

        // Phase 1: try phone-like paired devices first. Most Galaxy Watch
        // setups fall here because the watch IS paired with the phone at
        // the system Bluetooth level.
        if (phoneLike.isNotEmpty()) {
            for ((index, device) in phoneLike.withIndex()) {
                _connectionState.value = WearConnectionState.Connecting(
                    "尝试 ${index + 1}/${phoneLike.size}: ${device.name ?: device.address}"
                )
                if (tryConnect(device)) {
                    _isConnected.value = true
                    _connectionState.value = WearConnectionState.Connected
                    // Successful connection — drop the skip list so the
                    // next disconnect/reconnect attempt doesn't
                    // accidentally skip a now-healthy device.
                    recentlyFailed.clear()
                    return@withLock true
                }
                markFailed(device)
            }
        }

        // Phase 2: any remaining paired devices (e.g. a Bluetooth headset
        // that also happened to be bonded).
        val otherPaired = pairedDevices - phoneLike.toSet()
        if (otherPaired.isNotEmpty()) {
            for ((index, device) in otherPaired.withIndex()) {
                _connectionState.value = WearConnectionState.Connecting(
                    "尝试 ${index + 1}/${otherPaired.size}: ${device.name ?: device.address} (非手机)"
                )
                if (tryConnect(device)) {
                    _isConnected.value = true
                    _connectionState.value = WearConnectionState.Connected
                    return@withLock true
                }
                markFailed(device)
            }
        }

        // Phase 3: discovery as a last resort — covers the case where the
        // phone wasn't in bondedDevices at all.
        val discovered = discoverNearbyDevices(adapter)
            .filterNot { isRecentlyFailed(it) }
        if (discovered.isNotEmpty()) {
            for ((index, device) in discovered.withIndex()) {
                _connectionState.value = WearConnectionState.Connecting(
                    "扫描 ${index + 1}/${discovered.size}: ${device.name ?: device.address}"
                )
                if (tryConnect(device)) {
                    _isConnected.value = true
                    _connectionState.value = WearConnectionState.Connected
                    return@withLock true
                }
                markFailed(device)
            }
        }

        val reason = when {
            rawPaired.isEmpty() && discovered.isEmpty() ->
                "找不到手机 — 请在手表系统设置 > 蓝牙里把手机和手表互相配对（不是 Samsung Wearable）"
            rawPaired.isNotEmpty() && phoneLike.isEmpty() ->
                "已配对的 ${rawPaired.size} 个设备都不是手机 — 请在手表系统设置 > 蓝牙里把手机和手表配对"
            phoneLike.isNotEmpty() && discovered.isEmpty() ->
                "手机 ${phoneLike.first().name ?: phoneLike.first().address} 连不上 — 检查手机端是否给了 Messenger 「附近设备」权限，并且 Messenger 正在前台运行"
            else ->
                "所有设备都连不上 — 检查手机 Messenger 是否在前台运行"
        }
        Log.w(TAG, reason)
        _connectionState.value = WearConnectionState.Error(reason)
        false
    }

    private fun isRecentlyFailed(device: BluetoothDevice): Boolean {
        val failedAt = recentlyFailed[device.address] ?: return false
        if (System.currentTimeMillis() - failedAt > SKIP_DURATION_MS) {
            recentlyFailed.remove(device.address)
            return false
        }
        return true
    }

    private fun markFailed(device: BluetoothDevice) {
        recentlyFailed[device.address] = System.currentTimeMillis()
    }

    @SuppressLint("MissingPermission")
    private fun isProbablyPhoneOrComputer(device: BluetoothDevice): Boolean {
        val major = try {
            device.bluetoothClass?.majorDeviceClass
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read CoD for ${device.address}", e)
            return nameLooksLikePhone(device)
        }
        return major == BluetoothClass.Device.Major.COMPUTER ||
            major == BluetoothClass.Device.Major.PHONE ||
            (major == null && nameLooksLikePhone(device))
    }

    private fun nameLooksLikePhone(device: BluetoothDevice): Boolean {
        val name = device.name?.lowercase().orEmpty()
        return name.contains("phone") ||
            name.contains("galaxy") ||
            name.contains("iphone") ||
            name.contains("pixel") ||
            name.contains("huawei") ||
            name.contains("xiaomi") ||
            name.contains("oppo") ||
            name.contains("vivo") ||
            name.contains("oneplus")
    }

    @SuppressLint("MissingPermission")
    private fun logPairedDevices(all: Set<BluetoothDevice>, phoneLike: List<BluetoothDevice>) {
        if (all.isEmpty()) {
            Log.d(TAG, "No paired Bluetooth devices at all")
            return
        }
        Log.d(TAG, "Paired devices: ${all.size} total, ${phoneLike.size} phone-like")
        for (device in all) {
            val name = device.name ?: "<unnamed>"
            val klass = try {
                device.bluetoothClass?.majorDeviceClass
            } catch (_: SecurityException) { "?" }
            Log.d(TAG, "  - $name (${device.address}) class=$klass")
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverNearbyDevices(adapter: BluetoothAdapter): List<BluetoothDevice> {
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

    /**
     * Clear the "recently failed" device list. Called when the user taps
     * the "重试连接" button so they don't have to wait out the skip window.
     */
    fun clearSkipList() {
        recentlyFailed.clear()
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
        /**
         * How long a device stays in the "skip" set after a failed connect
         * attempt. Long enough to avoid an infinite flash loop, short enough
         * that we eventually retry.
         */
        private const val SKIP_DURATION_MS = 60_000L
    }
}
