package cc.ptoe.messenger.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
 * Low-level Bluetooth RFCOMM client used by [WearBridgeClient] to talk to
 * [MobileBluetoothServer] on the phone.
 *
 * This transport bypasses the Wear OS DataLayer entirely, so it works on
 * Samsung China-region watches where Google Play Services for Wear OS is
 * missing or broken.
 *
 * The watch assumes the phone is already paired at the system Bluetooth
 * level (typically via the Samsung Wearable app). On connect we iterate
 * bondedDevices and try to open a socket to whichever device will accept
 * our service UUID. One socket is held open for the lifetime of the
 * connection; [sendRequest] serializes request/response on a [Mutex].
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

    @SuppressLint("MissingPermission")
    suspend fun connect(): Boolean = mutex.withLock {
        if (_isConnected.value) return@withLock true
        val adapter = adapter ?: return@withLock false
        if (!adapter.isEnabled) return@withLock false

        val pairedDevices = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            return@withLock false
        }

        if (pairedDevices.isEmpty()) {
            Log.w(TAG, "No paired Bluetooth devices — pair your watch with the phone first")
            return@withLock false
        }

        for (device in pairedDevices) {
            if (tryConnect(device)) {
                _isConnected.value = true
                return@withLock true
            }
        }
        false
    }

    @SuppressLint("MissingPermission")
    private fun tryConnect(device: BluetoothDevice): Boolean {
        return try {
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
    }

    companion object {
        private const val TAG = "WearBluetoothBridge"
    }
}
