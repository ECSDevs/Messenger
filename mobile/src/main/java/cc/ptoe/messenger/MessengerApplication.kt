package cc.ptoe.messenger

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import cc.ptoe.messenger.data.local.AppPreferences
import cc.ptoe.messenger.data.local.MessengerDatabase
import cc.ptoe.messenger.data.local.ThemePreferences
import cc.ptoe.messenger.data.repository.AgentRepositoryImpl
import cc.ptoe.messenger.data.repository.ApiRepositoryImpl
import cc.ptoe.messenger.data.repository.ChatRepositoryImpl
import cc.ptoe.messenger.data.repository.ConversationRepositoryImpl
import cc.ptoe.messenger.data.repository.CurrentAgentRepositoryImpl
import cc.ptoe.messenger.data.repository.MessageRepositoryImpl
import cc.ptoe.messenger.data.repository.ModelRepositoryImpl
import cc.ptoe.messenger.data.repository.ProviderRepositoryImpl
import cc.ptoe.messenger.data.wear.MobileBluetoothServer
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.ApiRepository
import cc.ptoe.messenger.domain.repository.ChatRepository
import cc.ptoe.messenger.domain.repository.ConversationRepository
import cc.ptoe.messenger.domain.repository.CurrentAgentRepository
import cc.ptoe.messenger.domain.repository.MessageRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import cc.ptoe.messenger.domain.repository.ProviderRepository
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MessengerApplication : Application() {

    lateinit var database: MessengerDatabase
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var providerRepository: ProviderRepository
        private set

    lateinit var modelRepository: ModelRepository
        private set

    lateinit var agentRepository: AgentRepository
        private set

    lateinit var conversationRepository: ConversationRepository
        private set

    lateinit var messageRepository: MessageRepository
        private set

    lateinit var appPreferences: AppPreferences
        private set

    lateinit var themePreferences: ThemePreferences
        private set

    lateinit var apiRepository: ApiRepository
        private set

    lateinit var currentAgentRepository: CurrentAgentRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Tracks whether the user permanently denied BLUETOOTH_CONNECT (i.e. they
     * chose "don't ask again" in the system dialog). The UI watches this to
     * surface a banner with a deep link to app settings.
     */
    private val _bluetoothPermissionPermanentlyDenied = MutableStateFlow(false)
    val bluetoothPermissionPermanentlyDenied: StateFlow<Boolean> =
        _bluetoothPermissionPermanentlyDenied.asStateFlow()

    fun markBluetoothPermissionPermanentlyDenied() {
        _bluetoothPermissionPermanentlyDenied.value = true
    }

    fun markBluetoothPermissionGranted() {
        _bluetoothPermissionPermanentlyDenied.value = false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initDatabase()
        initPreferences()
        initRepositories()
        initDefaultAgent()
        startBluetoothSync()
    }

    private fun startBluetoothSync() {
        // Spin up the Bluetooth RFCOMM server that the watch companion connects
        // to. This intentionally does not depend on GMS for Wear OS so it works
        // on Samsung China-region watches where DataLayer is broken.
        if (!hasBluetoothConnectPermission()) return
        val intent = Intent(this, MobileBluetoothServer::class.java)
        runCatching { startForegroundService(intent) }
    }

    /**
     * Called by the UI layer (e.g. after the user grants BLUETOOTH_CONNECT
     * via the system permission dialog) to kick off the Wear sync service.
     */
    fun ensureBluetoothSyncRunning() {
        if (!hasBluetoothConnectPermission()) return
        val intent = Intent(this, MobileBluetoothServer::class.java)
        runCatching { startForegroundService(intent) }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initDatabase() {
        database = Room.databaseBuilder(
            this,
            MessengerDatabase::class.java,
            "messenger_database"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    private fun initPreferences() {
        appPreferences = AppPreferences(this)
        themePreferences = ThemePreferences(this)
    }

    private fun initRepositories() {
        chatRepository = ChatRepositoryImpl()
        providerRepository = ProviderRepositoryImpl(database.providerDao())
        modelRepository = ModelRepositoryImpl(database.modelDao())
        agentRepository = AgentRepositoryImpl(database.agentDao())
        conversationRepository = ConversationRepositoryImpl(database.conversationDao())
        messageRepository = MessageRepositoryImpl(database.messageDao())
        apiRepository = ApiRepositoryImpl()
        currentAgentRepository = CurrentAgentRepositoryImpl(appPreferences, agentRepository)
    }

    private fun initDefaultAgent() {
        applicationScope.launch {
            createDefaultAgentIfNeeded()
        }
    }

    suspend fun clearAllDataAndReinit() {
        database.clearAllTables()
        appPreferences.setDefaultAgentInitialized(false)
        appPreferences.setCurrentAgentId(null)
        createDefaultAgentIfNeeded()
    }

    private suspend fun createDefaultAgentIfNeeded() {
        val agents = agentRepository.getAll().first()
        val existingDefault = agents.firstOrNull { it.isDefault }
        if (existingDefault == null) {
            // 没有默认 Agent，则创建一个
            val now = System.currentTimeMillis()
            val defaultAgent = Agent(
                id = UUID.randomUUID().toString(),
                name = "默认 Agent",
                systemPrompt = "You are a helpful assistant.",
                defaultModelId = null,
                temperature = 0.7f,
                topP = 1.0f,
                maxTokens = null,
                isDefault = true,
                createdAt = now,
                updatedAt = now
            )
            agentRepository.insert(defaultAgent)
            // 仅在当前没有选中任何 Agent 时切到默认 Agent，避免覆盖用户选择
            val currentId = appPreferences.currentAgentId.first()
            if (currentId == null) {
                currentAgentRepository.setCurrentAgentId(defaultAgent.id)
            }
        }
        appPreferences.setDefaultAgentInitialized(true)
    }

    companion object {
        lateinit var instance: MessengerApplication
            private set
    }
}
