package cc.ptoe.messenger

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        initDatabase()
        initPreferences()
        initRepositories()
        initDefaultAgent()
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
