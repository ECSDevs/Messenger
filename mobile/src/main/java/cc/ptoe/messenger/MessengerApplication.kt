/*
 * Copyright 2026 ECSDevs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.ptoe.messenger

import android.app.Application
import android.content.Intent
import androidx.room.Room
import cc.ptoe.messenger.data.cloud.CloudSyncRepository
import cc.ptoe.messenger.data.local.AppPreferences
import cc.ptoe.messenger.data.local.ChatImageStore
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
import cc.ptoe.messenger.data.wear.MobileHttpServer
import cc.ptoe.messenger.domain.model.Agent
import cc.ptoe.messenger.domain.repository.AgentRepository
import cc.ptoe.messenger.domain.repository.ApiRepository
import cc.ptoe.messenger.domain.repository.ChatRepository
import cc.ptoe.messenger.domain.repository.ConversationRepository
import cc.ptoe.messenger.domain.repository.CurrentAgentRepository
import cc.ptoe.messenger.domain.repository.MessageRepository
import cc.ptoe.messenger.domain.repository.ModelRepository
import cc.ptoe.messenger.domain.repository.ProviderRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MessengerApplication : Application(), ImageLoaderFactory {

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

    lateinit var cloudSyncRepository: CloudSyncRepository
        private set

    lateinit var chatImageStore: ChatImageStore
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localDataMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        instance = this
        initDatabase()
        initPreferences()
        initRepositories()
        initImageStore()
        initializeLocalAndCloudData()
        startWearSync()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { cloudSyncRepository.createAvatarHttpClient() }
        .build()

    /**
     * Spin up the WebSocket server that the watch companion connects to.
     * Discovery is via Android NSD (mDNS) so no pairing or runtime
     * permissions are required — Wear OS watches tether their network to
     * the phone (Bluetooth PAN), so the watch and phone are always on the
     * same L2 network.
     */
    private fun startWearSync() {
        val intent = Intent(this, MobileHttpServer::class.java)
        runCatching { startForegroundService(intent) }
    }

    private fun initImageStore() {
        chatImageStore = ChatImageStore(this)
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
        cloudSyncRepository = CloudSyncRepository(
            appPreferences = appPreferences,
            database = database,
            context = applicationContext,
            localDataMutex = localDataMutex
        )
        providerRepository = ProviderRepositoryImpl(database.providerDao()) { id, deleted ->
            cloudSyncRepository.requestLocalChange("provider", id, deleted)
        }
        modelRepository = ModelRepositoryImpl(database.modelDao()) { providerId, _ ->
            cloudSyncRepository.requestLocalChange("provider", providerId)
        }
        agentRepository = AgentRepositoryImpl(
            agentDao = database.agentDao(),
            onChanged = { previous, current ->
                cloudSyncRepository.requestAgentAvatarChange(previous, current)
                current?.let { cloudSyncRepository.requestLocalChange("agent", it.id) }
                    ?: previous?.let { cloudSyncRepository.requestLocalChange("agent", it.id, deleted = true) }
            },
            avatarDirectory = File(applicationContext.filesDir, "agent_avatars")
        )
        conversationRepository = ConversationRepositoryImpl(database.conversationDao()) { id, deleted ->
            cloudSyncRepository.requestLocalChange("conversation", id, deleted)
        }
        messageRepository = MessageRepositoryImpl(database.messageDao()) { conversationId ->
            cloudSyncRepository.requestLocalChange("conversation", conversationId)
        }
        apiRepository = ApiRepositoryImpl()
        currentAgentRepository = CurrentAgentRepositoryImpl(appPreferences, agentRepository)
    }

    private fun initializeLocalAndCloudData() {
        applicationScope.launch {
            if (appPreferences.cloudSession.first() != null) {
                runCatching {
                    cloudSyncRepository.refreshUser()
                    cloudSyncRepository.sync()
                    cloudSyncRepository.requestLocalSync()
                }
            }
            createDefaultAgentIfNeeded()
        }
    }

    suspend fun clearAllDataAndReinit() = withContext(Dispatchers.IO) {
        cloudSyncRepository.cancelPendingLocalSync()
        localDataMutex.withLock {
            appPreferences.userAvatar.first()?.let { avatarPath ->
                File(avatarPath).takeIf { it.exists() }?.delete()
            }
            database.clearAllTables()
            appPreferences.clearAll()
            filesDir.deleteRecursively()
            cacheDir.deleteRecursively()
            noBackupFilesDir.deleteRecursively()
            externalCacheDir?.deleteRecursively()
            createDefaultAgentIfNeededLocked()
        }
    }

    private suspend fun createDefaultAgentIfNeeded() {
        localDataMutex.withLock {
            createDefaultAgentIfNeededLocked()
        }
    }

    private suspend fun createDefaultAgentIfNeededLocked() {
        if (appPreferences.cloudSession.first() != null) return
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
