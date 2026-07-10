package cc.ptoe.messenger

import android.app.Application
import cc.ptoe.messenger.data.WearBridgeClient
import cc.ptoe.messenger.data.WearChatPreferences
import cc.ptoe.messenger.data.WearChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WearMessengerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var wearChatRepository: WearChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        wearChatRepository = WearChatRepository(
            preferences = WearChatPreferences(this),
            bridgeClient = WearBridgeClient(this, applicationScope),
            scope = applicationScope
        )
    }

    companion object {
        lateinit var instance: WearMessengerApplication
            private set
    }
}
