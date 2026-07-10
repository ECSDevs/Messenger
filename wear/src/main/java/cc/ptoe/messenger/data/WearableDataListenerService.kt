package cc.ptoe.messenger.data

import android.util.Log
import cc.ptoe.messenger.WearMessengerApplication
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Background entry point for DataLayer events coming from the phone.
 *
 * The programmatic `DataClient.OnDataChangedListener` registered in [WearBridgeClient]
 * only works while the wear app process is alive, but Wear OS aggressively kills
 * background processes. By declaring this service in the manifest, the system
 * will restart the process and call [onDataChanged] / [onMessageReceived] as soon
 * as a new sync state arrives — even if the user has not opened the app.
 */
class WearableDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            val app = application as WearMessengerApplication
            app.wearChatRepository.handleDataEvents(dataEvents)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle data events", e)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            val app = application as WearMessengerApplication
            app.wearChatRepository.handleMessage(messageEvent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle message event", e)
        }
    }

    companion object {
        private const val TAG = "WearableDataListener"
    }
}
