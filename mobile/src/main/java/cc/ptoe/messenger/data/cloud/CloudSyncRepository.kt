package cc.ptoe.messenger.data.cloud

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import cc.ptoe.messenger.data.local.AppPreferences
import cc.ptoe.messenger.data.local.MessengerDatabase
import cc.ptoe.messenger.data.local.entity.AgentEntity
import cc.ptoe.messenger.data.local.entity.ConversationEntity
import cc.ptoe.messenger.data.local.entity.MessageEntity
import cc.ptoe.messenger.data.local.entity.ModelEntity
import cc.ptoe.messenger.data.local.entity.ProviderEntity
import cc.ptoe.messenger.domain.model.Agent
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

const val DEFAULT_CLOUD_SERVER_URL = "https://messenger.ptoe.cc"

private data class CredentialsRequest(val email: String, val password: String)
private data class PasswordChangeRequest(val currentPassword: String, val newPassword: String)
private data class AccountDeleteRequest(val currentPassword: String)
private data class UserResponse(val user: CloudUser)
private data class SuccessResponse(val success: Boolean)

private interface CloudApi {
    @POST suspend fun register(@Url url: String, @Body body: CredentialsRequest): UserResponse
    @POST suspend fun login(@Url url: String, @Body body: CredentialsRequest): UserResponse
    @POST suspend fun logout(@Url url: String): SuccessResponse
    @GET suspend fun me(@Url url: String): UserResponse
    @PUT suspend fun changePassword(@Url url: String, @Body body: PasswordChangeRequest): SuccessResponse
    @HTTP(method = "DELETE", path = "", hasBody = true)
    suspend fun deleteAccount(@Url url: String, @Body body: AccountDeleteRequest): SuccessResponse
    @GET suspend fun sync(@Url url: String, @Query("since") since: Long): CloudSyncResponse

    @GET suspend fun listMarketAgents(
        @Url url: String,
        @Query("query") query: String,
        @Query("cursor") cursor: String? = null
    ): CloudMarketAgentListResponse
    @GET suspend fun getMarketAgent(@Url url: String): CloudMarketAgentResponse
    @POST suspend fun createMarketAgent(@Url url: String, @Body body: CloudMarketAgentRequest): CloudMarketAgentResponse
    @PUT suspend fun updateMarketAgent(@Url url: String, @Body body: CloudMarketAgentRequest): CloudMarketAgentResponse
    @DELETE suspend fun deleteMarketAgent(@Url url: String): SuccessResponse

    @PUT suspend fun putAgent(@Url url: String, @Body body: CloudAgentRequest): CloudUpsertResponse
    @DELETE suspend fun deleteAgent(@Url url: String): CloudUpsertResponse
    @PUT suspend fun putConversation(@Url url: String, @Body body: CloudConversationRequest): CloudUpsertResponse
    @DELETE suspend fun deleteConversation(@Url url: String): CloudUpsertResponse
    @PUT suspend fun putProvider(@Url url: String, @Body body: CloudProviderRequest): CloudUpsertResponse
    @DELETE suspend fun deleteProvider(@Url url: String): CloudUpsertResponse

    @Multipart
    @PUT suspend fun uploadUserAvatar(@Url url: String, @Part file: MultipartBody.Part): CloudAvatarResponse
    @DELETE suspend fun deleteUserAvatar(@Url url: String): CloudAvatarResponse
    @Multipart
    @PUT suspend fun uploadAgentAvatar(@Url url: String, @Part file: MultipartBody.Part): CloudAvatarResponse
    @DELETE suspend fun deleteAgentAvatar(@Url url: String): CloudAvatarResponse
    @Multipart
    @PUT suspend fun uploadMarketAgentAvatar(@Url url: String, @Part file: MultipartBody.Part): CloudAvatarResponse
    @DELETE suspend fun deleteMarketAgentAvatar(@Url url: String): CloudAvatarResponse
}

private data class CloudAgentRequest(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val systemPrompt: String,
    val defaultModelId: String? = null,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int? = null,
    val isDefault: Boolean,
    val followDefaultSystemPrompt: Boolean,
    val followDefaultModel: Boolean,
    val followDefaultTemperature: Boolean,
    val followDefaultTopP: Boolean,
    val followDefaultMaxTokens: Boolean,
    val marketAgentId: String? = null,
    val marketAgentVersion: Long? = null,
    val marketAgentRole: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

private data class CloudMarketAgentRequest(
    val name: String,
    val systemPrompt: String,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int? = null
)

private data class CloudMessageRequest(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val errorMessage: String?
)

private data class CloudConversationRequest(
    val id: String,
    val title: String,
    val agentId: String,
    val providerId: String,
    val overrideModelId: String?,
    val overrideTemperature: Double?,
    val overrideTopP: Double?,
    val overrideMaxTokens: Int?,
    val messages: List<CloudMessageRequest>,
    val createdAt: Long,
    val updatedAt: Long
)

private data class CloudModelRequest(
    val id: String,
    val modelId: String,
    val displayName: String,
    val isEnabled: Boolean,
    val createdAt: Long
)

private data class CloudProviderRequest(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<CloudModelRequest>,
    val createdAt: Long,
    val updatedAt: Long
)

class CloudSyncRepository(
    private val appPreferences: AppPreferences,
    private val database: MessengerDatabase,
    private val context: Context,
    private val gson: Gson = Gson(),
    private val localDataMutex: Mutex = Mutex()
) {
    @Volatile
    private var localChangesEnabled = false
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val avatarSyncMutex = Mutex()
    private var scheduledSync: Job? = null
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()
    private val api = Retrofit.Builder()
        .baseUrl("https://messenger.ptoe.cc/")
        .client(createClient())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(CloudApi::class.java)

    val user: Flow<CloudUser?> = appPreferences.cloudUser
        .map { value -> if (value.isNullOrBlank()) null else runCatching { gson.fromJson(value, CloudUser::class.java) }.getOrNull() }
        .distinctUntilChanged()

    val serverUrl: Flow<String> = appPreferences.cloudServerUrl
        .map { url -> url ?: DEFAULT_CLOUD_SERVER_URL }
        .distinctUntilChanged()

    suspend fun setServerUrl(url: String) {
        val normalized = normalizeServerUrl(url)
        val previous = serverUrl.first()
        if (previous != normalized) {
            localChangesEnabled = false
            localDataMutex.withLock {
                database.agentDao().clearAllMarketLinks()
            }
            appPreferences.setCloudSession(null)
            appPreferences.setCloudSessionHost(null)
            appPreferences.setCloudUser(null)
        }
        appPreferences.setCloudServerUrl(normalized.takeUnless { it == DEFAULT_CLOUD_SERVER_URL })
    }

    suspend fun login(email: String, password: String, serverUrl: String? = null): CloudLoginOutcome =
        withContext(Dispatchers.IO) {
            val baseUrl = prepareRequestServer(serverUrl)
            val response = request { api.login(endpoint("api/auth/login", baseUrl), CredentialsRequest(email.trim(), password)) }
            completeAuthentication(response.user, baseUrl)
        }

    suspend fun register(email: String, password: String, serverUrl: String? = null): CloudLoginOutcome =
        withContext(Dispatchers.IO) {
            val baseUrl = prepareRequestServer(serverUrl)
            val response = request { api.register(endpoint("api/auth/register", baseUrl), CredentialsRequest(email.trim(), password)) }
            completeAuthentication(response.user, baseUrl)
        }

    suspend fun refreshUser(): CloudUser {
        return refreshUser(updateLocalAvatar = true)
    }

    private suspend fun refreshUser(updateLocalAvatar: Boolean): CloudUser {
        checkSignedIn()
        val response = request { api.me(endpoint("api/auth/me")) }
        val configuredServerUrl = serverUrl.first()
        Log.i(
            TAG,
            "Cloud account id=${response.user.id} email=${response.user.email} " +
            "serverSyncVersion=${response.user.syncVersion}"
        )
        saveUser(response.user)
        if (updateLocalAvatar) {
            val currentAvatar = appPreferences.userAvatar.first()
            val localAvatar = if (response.user.avatarUrl != null) {
                currentAvatar
                    ?.takeUnless(::isCloudCachedAvatar)
                    ?.takeIf(::isUsableLocalAvatar)
                    ?: runCatching {
                        cacheRemoteAvatar(
                            "user",
                            response.user.id,
                            response.user.id,
                            response.user.avatarUrl,
                            response.user.avatarVersion?.toString(),
                            configuredServerUrl
                        )
                    }.getOrNull()
                    ?: currentAvatar?.takeUnless(::isRemoteAvatar)?.takeIf(::isUsableLocalAvatar)
            } else {
                currentAvatar?.takeUnless(::isCloudCachedAvatar)?.takeIf(::isUsableLocalAvatar)
            }
            appPreferences.setUserAvatar(localAvatar)
            if (response.user.avatarUrl == null) {
                deleteCachedAvatars("user", response.user.id, response.user.id)
            }
        }
        return response.user
    }

    suspend fun logout() {
        runCatching { request { api.logout(endpoint("api/auth/logout")) } }
        user.first()?.let { appPreferences.clearCloudSyncVersion(it.id) }
        localChangesEnabled = false
        localDataMutex.withLock {
            database.agentDao().clearAllMarketLinks()
        }
        appPreferences.setCloudSession(null)
        appPreferences.setCloudSessionHost(null)
        appPreferences.setCloudUser(null)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Unit = withContext(Dispatchers.IO) {
        checkSignedIn()
        request {
            api.changePassword(
                endpoint("api/auth/password"),
                PasswordChangeRequest(currentPassword, newPassword)
            )
        }
    }

    suspend fun deleteAccount(currentPassword: String): Unit = withContext(Dispatchers.IO) {
        checkSignedIn()
        val accountId = user.first()?.id
        request {
            api.deleteAccount(
                endpoint("api/auth/account"),
                AccountDeleteRequest(currentPassword)
            )
        }
        cancelPendingLocalSync()
        appPreferences.clearCloudAccount(accountId)
    }

    suspend fun listMarketAgents(
        query: String,
        cursor: String? = null
    ): CloudMarketAgentListResponse = withContext(Dispatchers.IO) {
        checkSignedIn()
        request { api.listMarketAgents(endpoint("api/market/agents"), query.trim(), cursor) }
    }

    suspend fun marketAgent(id: String): CloudMarketAgent = withContext(Dispatchers.IO) {
        checkSignedIn()
        request { api.getMarketAgent(endpoint("api/market/agents/$id")) }.agent
    }

    suspend fun publishMarketAgent(agentId: String): CloudMarketAgent = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val agent = requireAgentForMarket(agentId)
            check(!agent.isDefault) { "The default Agent cannot be published." }
            check(agent.marketAgentId == null) { "This Agent is already linked to the market." }
            val response = request {
                api.createMarketAgent(endpoint("api/market/agents"), agent.toMarketRequest())
            }.agent
            val updated = try {
                updateMarketAvatar(response, agent.avatar)
            } catch (error: Throwable) {
                runCatching { request { api.deleteMarketAgent(endpoint("api/market/agents/${response.id}")) } }
                throw error
            }
            persistMarketLink(agent.id, updated.id, updated.version, "publisher")
            updated
        }
    }

    suspend fun pushMarketAgentUpdate(agentId: String): CloudMarketAgent = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val agent = requireAgentForMarket(agentId)
            val marketId = checkNotNull(agent.marketAgentId) { "Publish this Agent first." }
            check(agent.marketAgentRole == "publisher") { "Only the publisher can update this Agent." }
            val response = runCatching {
                request {
                    api.updateMarketAgent(endpoint("api/market/agents/$marketId"), agent.toMarketRequest())
                }.agent
            }.getOrElse { error ->
                if (isMarketNotFound(error)) {
                    _syncError.value = null
                    persistMarketLink(agent.id, null, null, null)
                    throw IOException("Market entry no longer exists; local Agent marked as unpublished.", error)
                } else {
                    throw error
                }
            }
            val updated = updateMarketAvatar(response, agent.avatar)
            persistMarketLink(agent.id, updated.id, updated.version, "publisher")
            updated
        }
    }

    suspend fun removeMarketAgent(agentId: String) = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val agent = requireAgentForMarket(agentId)
            val marketId = checkNotNull(agent.marketAgentId) { "This Agent is not published." }
            check(agent.marketAgentRole == "publisher") { "Only the publisher can remove this Agent." }
            runCatching { request { api.deleteMarketAgent(endpoint("api/market/agents/$marketId")) } }
                .onFailure { error ->
                    if (!isMarketNotFound(error)) throw error
                    _syncError.value = null
                }
            persistMarketLink(agent.id, null, null, null)
        }
    }

    private fun isMarketNotFound(error: Throwable): Boolean =
        (error.cause as? HttpException)?.code() == 404

    suspend fun importMarketAgent(marketId: String): Agent = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val market = marketAgent(marketId)
            val account = checkNotNull(user.first())
            val avatar = market.avatarUrl?.let { url ->
                cacheRemoteAvatar(
                    scope = "market-agent",
                    accountId = account.id,
                    id = market.id,
                    url = url,
                    version = market.avatarVersion?.toString(),
                    baseUrl = serverUrl.first()
                ).let(::copyMarketAvatarToAgentStorage)
            }
            val now = System.currentTimeMillis()
            val imported = AgentEntity(
                id = UUID.randomUUID().toString(),
                name = market.name,
                avatar = avatar,
                systemPrompt = market.systemPrompt,
                defaultModelId = null,
                temperature = market.temperature.toFloat(),
                topP = market.topP.toFloat(),
                maxTokens = market.maxTokens,
                isDefault = false,
                followDefaultSystemPrompt = false,
                followDefaultModel = false,
                followDefaultTemperature = false,
                followDefaultTopP = false,
                followDefaultMaxTokens = false,
                marketAgentId = market.id,
                marketAgentVersion = market.version,
                marketAgentRole = "importer",
                createdAt = now,
                updatedAt = now
            )
            database.agentDao().insert(imported)
            requestLocalChange("agent", imported.id)
            imported.toDomain()
        }
    }

    suspend fun checkMarketAgentUpdate(agentId: String): CloudMarketAgentUpdate = withContext(Dispatchers.IO) {
        checkSignedIn()
        val agent = requireAgentForMarket(agentId)
        val marketId = checkNotNull(agent.marketAgentId) { "This Agent is not linked to the market." }
        check(agent.marketAgentRole == "importer") { "Published Agents do not receive market updates." }
        val market = marketAgent(marketId)
        CloudMarketAgentUpdate(market, market.version > (agent.marketAgentVersion ?: 0L))
    }

    suspend fun applyMarketAgentUpdate(agentId: String, market: CloudMarketAgent): Agent = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val agent = requireAgentForMarket(agentId)
            check(agent.marketAgentId == market.id && agent.marketAgentRole == "importer") {
                "This Agent is not linked to the selected market entry."
            }
            val account = checkNotNull(user.first())
            val avatar = market.avatarUrl?.let { url ->
                cacheRemoteAvatar(
                    scope = "market-agent",
                    accountId = account.id,
                    id = market.id,
                    url = url,
                    version = market.avatarVersion?.toString(),
                    baseUrl = serverUrl.first()
                ).let(::copyMarketAvatarToAgentStorage)
            }
            deleteLocalAvatarIfReplaced(agent.avatar, avatar)
            val updated = agent.copy(
                name = market.name,
                avatar = avatar,
                systemPrompt = market.systemPrompt,
                temperature = market.temperature.toFloat(),
                topP = market.topP.toFloat(),
                maxTokens = market.maxTokens,
                followDefaultSystemPrompt = false,
                followDefaultModel = false,
                followDefaultTemperature = false,
                followDefaultTopP = false,
                followDefaultMaxTokens = false,
                marketAgentVersion = market.version,
                updatedAt = System.currentTimeMillis()
            )
            database.agentDao().update(updated)
            requestLocalChange("agent", updated.id)
            updated.toDomain()
        }
    }

    /** Pulls all server changes since the account cursor and applies them atomically. */
    suspend fun sync(): CloudSyncResult = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val account = refreshUser()
            syncMutex.withLock {
                syncInternal(expectedServerVersion = account.syncVersion).also { _syncError.value = null }
            }
        }
    }

    /** Rebuilds the local account data from the server's complete entity history. */
    private suspend fun fullSync(): CloudSyncResult = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val account = refreshUser()
            syncMutex.withLock {
                syncInternal(
                    sinceOverride = 0L,
                    replaceLocal = true,
                    expectedServerVersion = account.syncVersion
                ).also { _syncError.value = null }
            }
        }
    }

    /** Pushes the current local snapshot, then pulls the authoritative server delta. */
    suspend fun upload(): CloudSyncResult = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val account = refreshUser()
            syncMutex.withLock {
                pushLocalSnapshot(onlyPending = false)
                syncInternal(expectedServerVersion = account.syncVersion).also { _syncError.value = null }
            }
        }
    }

    suspend fun completeLogin(outcome: CloudLoginOutcome, useLocalData: Boolean): CloudSyncResult {
        return if (useLocalData) replaceCloudWithLocal() else fullSync()
    }

    /** Kept as the settings screen's restore action; incremental sync is non-destructive. */
    suspend fun downloadAndRestore(): CloudSyncResult = fullSync()

    fun requestLocalSync() {
        scheduledSync?.cancel()
        scheduledSync = syncScope.launch {
            delay(750)
            runCatching { pushPendingChanges() }
                .onFailure { _syncError.value = it.message ?: "Cloud synchronization failed" }
        }
    }

    suspend fun cancelPendingLocalSync() {
        syncScope.coroutineContext.cancelChildren()
        syncScope.coroutineContext[Job]?.children?.toList()?.joinAll()
        scheduledSync = null
        syncMutex.withLock {
            localChangesEnabled = false
            _syncError.value = null
        }
    }

    fun requestLocalChange(type: String, id: String, deleted: Boolean = false) {
        syncScope.launch {
            val account = user.first()?.takeIf { localChangesEnabled } ?: return@launch
            if (deleted) {
                appPreferences.addCloudPendingDelete(account.id, type, id)
                appPreferences.removeCloudPendingUpsert(account.id, type, id)
            } else {
                appPreferences.addCloudPendingUpsert(account.id, type, id)
                appPreferences.removeCloudPendingDelete(account.id, type, id)
            }
            requestLocalSync()
        }
    }

    fun requestAgentAvatarChange(previous: Agent?, current: Agent?) {
        if (previous?.avatar == current?.avatar) return
        syncScope.launch {
            if (user.first() == null || !localChangesEnabled) return@launch
            val agentId = current?.id ?: previous?.id ?: return@launch
            runCatching { uploadAgentAvatar(agentId, current?.avatar) }
                .onSuccess { _syncError.value = null }
                .onFailure { error ->
                    Log.e(TAG, "Agent avatar synchronization failed for agent=$agentId", error)
                    _syncError.value = error.message ?: "Avatar synchronization failed"
                }
        }
    }

    suspend fun uploadUserAvatar(path: String?): CloudAvatarResponse =
        avatarSyncMutex.withLock {
            withContext(Dispatchers.IO) {
                checkSignedIn()
                val currentAvatar = appPreferences.userAvatar.first()
                if (path != null && !isRemoteAvatar(path) && currentAvatar != path) {
                    // A concurrent upload already replaced and cleaned up this local file.
                    return@withContext CloudAvatarResponse(currentAvatar, 0L)
                }
                val localFile = path?.takeUnless(::isRemoteAvatar)?.let(::File)
                val response = if (path == null || isRemoteAvatar(path)) {
                    request { api.deleteUserAvatar(endpoint("api/avatars/user")) }
                } else {
                    request {
                        api.uploadUserAvatar(
                            endpoint("api/avatars/user"),
                            avatarPart(File(path))
                        )
                    }
                }
                val configuredServerUrl = serverUrl.first()
                val cachedAvatar = response.url?.let { url ->
                    runCatching {
                        cacheRemoteAvatar(
                            "user",
                            user.first()!!.id,
                            user.first()!!.id,
                            url,
                            response.avatarVersion?.toString(),
                            configuredServerUrl
                        )
                    }
                        .getOrNull()
                }
                val localAvatar = cachedAvatar ?: if (response.url != null) path else null
                appPreferences.setUserAvatar(localAvatar)
                refreshCachedUserAvatar(response.url, response.avatarVersion)
                if (response.url == null) {
                    deleteCachedAvatars("user", user.first()!!.id, user.first()!!.id)
                }
                if (localFile != null && localFile.absolutePath != localAvatar) localFile.delete()
                response
            }
        }

    suspend fun uploadAgentAvatar(agentId: String, path: String?): CloudAvatarResponse =
        avatarSyncMutex.withLock {
            withContext(Dispatchers.IO) {
                checkSignedIn()
                val currentAvatar = database.agentDao().getById(agentId).first()?.avatar
                if (path != null && !isRemoteAvatar(path) && currentAvatar != path) {
                    // A concurrent upload already replaced and cleaned up this local file.
                    return@withContext CloudAvatarResponse(currentAvatar, 0L)
                }
                val localFile = path?.takeUnless(::isRemoteAvatar)?.let(::File)
                val response = if (path == null || isRemoteAvatar(path)) {
                    request { api.deleteAgentAvatar(endpoint("api/avatars/agents/$agentId")) }
                } else {
                    request {
                        api.uploadAgentAvatar(
                            endpoint("api/avatars/agents/$agentId"),
                            avatarPart(localFile!!)
                        )
                    }
                }
                val configuredServerUrl = serverUrl.first()
                val cachedAvatar = response.url?.let { url ->
                    runCatching {
                        cacheRemoteAvatar(
                            "agent",
                            user.first()!!.id,
                            agentId,
                            url,
                            response.avatarVersion?.toString(),
                            configuredServerUrl
                        )
                    }
                        .getOrNull()
                }
                val localAvatar = cachedAvatar ?: if (response.url != null) path else null
                database.agentDao().getById(agentId).first()?.let { agent ->
                    database.agentDao().insert(agent.copy(avatar = localAvatar))
                }
                if (response.url == null) {
                    deleteCachedAvatars("agent", user.first()!!.id, agentId)
                }
                if (localFile != null && localFile.absolutePath != localAvatar) localFile.delete()
                response
            }
        }

    private suspend fun completeAuthentication(value: CloudUser, baseUrl: String): CloudLoginOutcome {
        val previousUser = user.first()
        if (previousUser == null || previousUser.id != value.id) {
            appPreferences.clearCloudSyncVersion(value.id)
        }
        appPreferences.setCloudSessionHost(baseUrl)
        saveUser(value)
        return CloudLoginOutcome(
            user = value,
            hasLocalData = localDataMutex.withLock { hasLocalData() },
            cloudVersion = value.syncVersion
        )
    }

    private suspend fun hasLocalData(): Boolean {
        val agents = database.agentDao().getAllEntities()
        val providers = database.providerDao().getAllEntities()
        val models = database.modelDao().getAllEntities()
        val conversations = database.conversationDao().getAllEntities()
        val messages = database.messageDao().getAllEntities()
        return providers.isNotEmpty() ||
            models.isNotEmpty() ||
            conversations.isNotEmpty() ||
            messages.isNotEmpty() ||
            agents.any { !it.isDefault || !it.isDefaultAgentBaseline() } ||
            appPreferences.userAvatar.first() != null
    }

    private suspend fun replaceCloudWithLocal(): CloudSyncResult = withContext(Dispatchers.IO) {
        localDataMutex.withLock {
            checkSignedIn()
            val account = refreshUser(updateLocalAvatar = false)
            syncMutex.withLock {
                val remote = request { api.sync(endpoint("api/sync"), 0L) }
                mergeLocalDefaultWithCloudDefault(remote)
                val localAgents = database.agentDao().getAllEntities()
                val localProviders = database.providerDao().getAllEntities()
                val localConversations = database.conversationDao().getAllEntities()
                val localAgentIds = localAgents.map { it.id }.toSet()
                val localProviderIds = localProviders.map { it.id }.toSet()
                val localConversationIds = localConversations.map { it.id }.toSet()

                remote.agents.filter { !it.deleted && !it.isDefault && it.id !in localAgentIds }
                    .forEach { request { api.deleteAgent(endpoint("api/agents/${it.id}")) } }
                remote.providers.filter { !it.deleted && it.id !in localProviderIds }
                    .forEach { request { api.deleteProvider(endpoint("api/providers/${it.id}")) } }
                remote.conversations.filter { !it.deleted && it.id !in localConversationIds }
                    .forEach { request { api.deleteConversation(endpoint("api/conversations/${it.id}")) } }

                val pushedVersion = pushLocalSnapshot(onlyPending = false)
                val currentAccount = refreshUser(updateLocalAvatar = false)
                val latestVersion = maxOf(account.syncVersion, currentAccount.syncVersion, pushedVersion)
                appPreferences.setCloudSyncVersion(currentAccount.id, latestVersion)
                localChangesEnabled = true
                CloudSyncResult(
                    latestVersion = latestVersion,
                    agents = localAgents.size,
                    conversations = localConversations.size,
                    providers = localProviders.size
                )
            }
        }
    }

    private suspend fun mergeLocalDefaultWithCloudDefault(remote: CloudSyncResponse) {
        val cloudDefault = remote.agents.firstOrNull { it.isDefault && !it.deleted } ?: return
        val localDefault = database.agentDao().getAllEntities().firstOrNull { it.isDefault } ?: return
        if (localDefault.id == cloudDefault.id) return
        database.withTransaction {
            database.agentDao().insert(localDefault.copy(id = cloudDefault.id))
            database.conversationDao().updateAgentId(localDefault.id, cloudDefault.id)
            database.agentDao().delete(localDefault.id)
        }
        appPreferences.setCurrentAgentId(cloudDefault.id)
    }

    private suspend fun pushPendingChanges() {
        localDataMutex.withLock {
            val account = refreshUser()
            syncMutex.withLock {
                checkSignedIn()
                pushLocalSnapshot(onlyPending = true)
                syncInternal(expectedServerVersion = account.syncVersion).also { _syncError.value = null }
            }
        }
    }

    private suspend fun pushLocalSnapshot(onlyPending: Boolean): Long {
        val account = user.first() ?: error("Please sign in first")
        var latestVersion = pushPendingDeletes(account.id)
        val pending = appPreferences.cloudPendingUpserts(account.id)
        fun shouldPush(type: String, id: String) = !onlyPending || "$type:$id" in pending
        val agents = database.agentDao().getAllEntities()
        val providers = database.providerDao().getAllEntities()
        val conversations = database.conversationDao().getAllEntities()
        val messagesByConversation = database.messageDao().getAllEntities().groupBy { it.conversationId }

        agents.filter { shouldPush("agent", it.id) }.forEach { agent ->
            val response = request {
                api.putAgent(endpoint("api/agents/${agent.id}"), agent.toCloudRequest())
            }
            latestVersion = maxOf(latestVersion, response.version)
            appPreferences.removeCloudPendingUpsert(account.id, "agent", agent.id)
            syncAgentAvatar(agent)
        }
        providers.filter { shouldPush("provider", it.id) }.forEach { provider ->
            val response = request {
                api.putProvider(
                    endpoint("api/providers/${provider.id}"),
                    provider.toCloudRequest(database.modelDao().getByProviderId(provider.id).first())
                )
            }
            latestVersion = maxOf(latestVersion, response.version)
            appPreferences.removeCloudPendingUpsert(account.id, "provider", provider.id)
        }
        conversations.filter { shouldPush("conversation", it.id) }.forEach { conversation ->
            val response = request {
                api.putConversation(
                    endpoint("api/conversations/${conversation.id}"),
                    conversation.toCloudRequest(messagesByConversation[conversation.id].orEmpty())
                )
            }
            latestVersion = maxOf(latestVersion, response.version)
            appPreferences.removeCloudPendingUpsert(account.id, "conversation", conversation.id)
        }
        val userAvatar = appPreferences.userAvatar.first()
        latestVersion = maxOf(
            latestVersion,
            when {
                userAvatar == null -> uploadUserAvatar(null).version
                !isRemoteAvatar(userAvatar) && !isCloudCachedAvatar(userAvatar) -> uploadUserAvatar(userAvatar).version
                else -> 0L
            }
        )
        return latestVersion
    }

    private suspend fun pushPendingDeletes(accountId: String): Long {
        var latestVersion = 0L
        appPreferences.cloudPendingDeletes(accountId).toList().forEach { value ->
            val separator = value.indexOf(':')
            if (separator <= 0) return@forEach
            val type = value.substring(0, separator)
            val id = value.substring(separator + 1)
            val response = runCatching {
                when (type) {
                    "agent" -> request { api.deleteAgent(endpoint("api/agents/$id")) }
                    "conversation" -> request { api.deleteConversation(endpoint("api/conversations/$id")) }
                    "provider" -> request { api.deleteProvider(endpoint("api/providers/$id")) }
                    else -> null
                }
            }.onFailure { error ->
                val httpError = error.cause as? HttpException
                if (httpError?.code() != 404) throw error
            }.getOrNull()
            latestVersion = maxOf(latestVersion, response?.version ?: 0L)
            appPreferences.removeCloudPendingDelete(accountId, type, id)
        }
        return latestVersion
    }

    private suspend fun syncInternal(
        sinceOverride: Long? = null,
        replaceLocal: Boolean = false,
        expectedServerVersion: Long? = null
    ): CloudSyncResult {
        checkSignedIn()
        val account = user.first() ?: error("Please sign in first")
        val since = sinceOverride ?: appPreferences.cloudSyncVersion(account.id)
        val delta = request { api.sync(endpoint("api/sync"), since) }
        Log.i(
            TAG,
            "Cloud sync account=${account.id} since=$since latest=${delta.latestVersion} " +
                "agents=${delta.agents.size} conversations=${delta.conversations.size} providers=${delta.providers.size}"
        )
        check(delta.latestVersion >= since) {
            "Cloud sync cursor moved backwards: since=$since latest=${delta.latestVersion}"
        }
        check(expectedServerVersion == null || delta.latestVersion >= expectedServerVersion) {
            "Cloud sync version mismatch: account=${account.id} /me=${expectedServerVersion} /sync=${delta.latestVersion}"
        }
        database.withTransaction {
            if (replaceLocal) {
                database.clearAllTables()
                appPreferences.setCurrentAgentId(null)
            }
            applyDelta(delta)
        }
        val configuredServerUrl = serverUrl.first()
        cacheAgentAvatars(account.id, delta, configuredServerUrl)
        cacheLegacyAgentAvatars(account.id, configuredServerUrl)
        appPreferences.setCloudSyncVersion(account.id, delta.latestVersion)
        localChangesEnabled = true
        Log.i(
            TAG,
            "Cloud sync applied agents=${database.agentDao().getAllEntities().size} " +
                "conversations=${database.conversationDao().getAllEntities().size} " +
                "providers=${database.providerDao().getAllEntities().size}"
        )
        return CloudSyncResult(delta.latestVersion, delta.agents.size, delta.conversations.size, delta.providers.size)
    }

    private suspend fun syncAgentAvatar(agent: AgentEntity) {
        if (agent.avatar != null && !isRemoteAvatar(agent.avatar) && !isCloudCachedAvatar(agent.avatar)) {
            uploadAgentAvatar(agent.id, agent.avatar)
        }
    }

    private suspend fun applyDelta(delta: CloudSyncResponse) {
        delta.agents.forEach { remote ->
            if (remote.deleted) {
                database.conversationDao().deleteByAgentId(remote.id)
                database.agentDao().delete(remote.id)
            } else {
                val existingAvatar = database.agentDao().getById(remote.id).first()?.avatar
                    ?.takeUnless(::isRemoteAvatar)
                    ?.takeIf(::isUsableLocalAvatar)
                if (remote.isDefault) {
                    database.agentDao().getAllEntities()
                        .filter { it.isDefault && it.id != remote.id }
                        .forEach { database.agentDao().insert(it.copy(isDefault = false)) }
                }
                // Keep an uncached URL only as a retry marker; AgentAvatar never renders it.
                database.agentDao().insert(remote.toEntity(existingAvatar ?: remote.avatarUrl))
            }
        }
        delta.providers.forEach { remote ->
            if (remote.deleted) {
                database.modelDao().deleteByProviderId(remote.id)
                database.providerDao().delete(remote.id)
            } else {
                database.modelDao().deleteByProviderId(remote.id)
                database.providerDao().insert(remote.toEntity())
                database.modelDao().insertAll(remote.models.map { it.toEntity(remote.id) })
            }
        }
        delta.conversations.forEach { remote ->
            if (remote.deleted) {
                database.messageDao().deleteByConversationId(remote.id)
                database.conversationDao().delete(remote.id)
            } else {
                if (database.agentDao().getById(remote.agentId).first() == null) {
                    database.messageDao().deleteByConversationId(remote.id)
                    database.conversationDao().delete(remote.id)
                    return@forEach
                }
                database.messageDao().deleteByConversationId(remote.id)
                database.conversationDao().insert(remote.toEntity())
                remote.messages.map { it.toEntity(remote.id) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { database.messageDao().insertAll(it) }
            }
        }
    }

    private suspend fun cacheAgentAvatars(accountId: String, delta: CloudSyncResponse, configuredServerUrl: String) {
        delta.agents.forEach { remote ->
            if (remote.deleted) {
                deleteCachedAvatars("agent", accountId, remote.id)
                return@forEach
            }

            val agent = database.agentDao().getById(remote.id).first() ?: return@forEach
            val previousAvatar = agent.avatar
            if (remote.avatarUrl == null) {
                val localAvatar = previousAvatar
                    ?.takeUnless(::isCloudCachedAvatar)
                    ?.takeIf(::isUsableLocalAvatar)
                if (localAvatar == null) {
                    database.agentDao().insert(agent.copy(avatar = null))
                    if (isCloudCachedAvatar(previousAvatar)) {
                        deleteLocalAvatarIfReplaced(previousAvatar, null)
                    }
                }
                deleteCachedAvatars("agent", accountId, remote.id)
                return@forEach
            }

            if (previousAvatar != null && !isCloudCachedAvatar(previousAvatar) &&
                isUsableLocalAvatar(previousAvatar)
            ) {
                return@forEach
            }

            val cachedAvatar = runCatching {
                cacheRemoteAvatar(
                    "agent",
                    accountId,
                    remote.id,
                    remote.avatarUrl,
                    remote.avatarVersion?.toString(),
                    configuredServerUrl
                )
            }.getOrNull() ?: return@forEach
            database.agentDao().insert(agent.copy(avatar = cachedAvatar))
            deleteLocalAvatarIfReplaced(previousAvatar, cachedAvatar)
        }
    }

    private suspend fun cacheLegacyAgentAvatars(accountId: String, configuredServerUrl: String) {
        database.agentDao().getAllEntities().forEach { agent ->
            val remoteUrl = agent.avatar?.takeIf(::isRemoteAvatar) ?: return@forEach
            val cachedAvatar = runCatching {
                cacheRemoteAvatar("agent", accountId, agent.id, remoteUrl, baseUrl = configuredServerUrl)
            }.getOrNull() ?: return@forEach
            database.agentDao().insert(agent.copy(avatar = cachedAvatar))
        }
    }

    private suspend fun clearLocalAccountData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        appPreferences.setCurrentAgentId(null)
        appPreferences.setUserAvatar(null)
        File(context.filesDir, "user_avatars").deleteRecursively()
        File(context.filesDir, "agent_avatars").deleteRecursively()
        File(context.filesDir, "cloud_avatars").deleteRecursively()
    }

    private suspend fun requireAgentForMarket(agentId: String): AgentEntity {
        return checkNotNull(database.agentDao().getById(agentId).first()) { "Agent not found." }
    }

    private suspend fun AgentEntity.toMarketRequest(): CloudMarketAgentRequest {
        val defaultAgent = database.agentDao().getAllEntities().firstOrNull { it.isDefault }
        return CloudMarketAgentRequest(
            name = name.trim(),
            systemPrompt = if (followDefaultSystemPrompt) defaultAgent?.systemPrompt ?: systemPrompt else systemPrompt,
            temperature = (if (followDefaultTemperature) defaultAgent?.temperature ?: temperature else temperature).toDouble(),
            topP = (if (followDefaultTopP) defaultAgent?.topP ?: topP else topP).toDouble(),
            maxTokens = if (followDefaultMaxTokens) defaultAgent?.maxTokens ?: maxTokens else maxTokens
        )
    }

    private suspend fun updateMarketAvatar(market: CloudMarketAgent, avatar: String?): CloudMarketAgent {
        val currentAvatar = avatar?.takeIf(::isUsableLocalAvatar)
        val response = when {
            currentAvatar != null -> request {
                api.uploadMarketAgentAvatar(
                    endpoint("api/market/agents/${market.id}/avatar"),
                    avatarPart(File(currentAvatar))
                )
            }
            market.avatarUrl != null -> request {
                api.deleteMarketAgentAvatar(endpoint("api/market/agents/${market.id}/avatar"))
            }
            else -> null
        }
        return response?.let { market.copy(version = it.version, avatarVersion = it.avatarVersion) } ?: market
    }

    private suspend fun persistMarketLink(
        agentId: String,
        marketAgentId: String?,
        marketAgentVersion: Long?,
        marketAgentRole: String?
    ) {
        val existing = requireAgentForMarket(agentId)
        val updated = existing.copy(
            marketAgentId = marketAgentId,
            marketAgentVersion = marketAgentVersion,
            marketAgentRole = marketAgentRole,
            updatedAt = System.currentTimeMillis()
        )
        database.agentDao().update(updated)
        requestLocalChange("agent", agentId)
    }

    private fun copyMarketAvatarToAgentStorage(sourcePath: String): String {
        val source = File(sourcePath)
        if (!source.isFile) return sourcePath
        val directory = File(context.filesDir, "agent_avatars").apply { mkdirs() }
        val target = File(directory, "market_${UUID.randomUUID()}.${source.extension.ifBlank { "jpg" }}")
        source.copyTo(target, overwrite = false)
        return target.absolutePath
    }

    private suspend fun checkSignedIn() {
        check(appPreferences.cloudSession.first() != null) { "Please sign in first" }
    }

    private suspend fun prepareRequestServer(override: String?): String {
        val baseUrl = normalizeServerUrl(override ?: serverUrl.first())
        val currentHost = appPreferences.cloudSessionHost.first()
        if (currentHost != null && currentHost != baseUrl) {
            localChangesEnabled = false
            localDataMutex.withLock {
                database.agentDao().clearAllMarketLinks()
            }
            appPreferences.setCloudSession(null)
            appPreferences.setCloudSessionHost(null)
            appPreferences.setCloudUser(null)
        }
        appPreferences.setCloudServerUrl(baseUrl.takeUnless { it == DEFAULT_CLOUD_SERVER_URL })
        return baseUrl
    }

    private suspend fun saveUser(value: CloudUser) {
        appPreferences.setCloudUser(gson.toJson(value))
    }

    private suspend fun refreshCachedUserAvatar(url: String?, avatarVersion: Long?) {
        val current = user.first() ?: return
        saveUser(current.copy(avatarUrl = url, avatarVersion = avatarVersion))
    }

    /** Stores a server avatar locally so rendering never depends on a network request. */
    private fun cacheRemoteAvatar(
        scope: String,
        accountId: String,
        id: String,
        url: String,
        version: String? = null,
        baseUrl: String
    ): String {
        val directory = File(context.filesDir, "cloud_avatars").apply { mkdirs() }
        val identity = digest("$scope|$accountId|$id")
        val requestUrl = resolveAvatarUrl(url, baseUrl)
        val urlKey = digest("$requestUrl|${version.orEmpty()}")
        val existing = directory.listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith("$identity-$urlKey.") && it.length() > 0L }
        if (existing != null) return existing.absolutePath

        val request = Request.Builder().url(requestUrl).get().build()
        return createAvatarHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Avatar download failed (${response.code})")
            }
            val body = response.body
            val contentLength = body.contentLength()
            check(contentLength <= MAX_AVATAR_BYTES || contentLength == -1L) {
                "Avatar must not exceed 5 MiB"
            }
            val extension = avatarExtension(response.header("Content-Type"), requestUrl)
            val target = File(directory, "$identity-$urlKey.$extension")
            val temporary = File(directory, "$identity-$urlKey.part")
            try {
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        var total = 0L
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= MAX_AVATAR_BYTES) { "Avatar must not exceed 5 MiB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(temporary.length() > 0L) { "Avatar response was empty" }
                if (!temporary.renameTo(target)) {
                    throw IOException("Unable to cache avatar")
                }
            } finally {
                temporary.delete()
            }
            deleteCachedAvatarsExcept(scope, accountId, id, target)
            target.absolutePath
        }
    }

    private fun deleteCachedAvatars(scope: String, accountId: String, id: String) {
        val identity = digest("$scope|$accountId|$id")
        File(context.filesDir, "cloud_avatars").listFiles()
            ?.filter { it.isFile && it.name.startsWith("$identity-") }
            ?.forEach { it.delete() }
    }

    private fun deleteCachedAvatarsExcept(scope: String, accountId: String, id: String, keep: File) {
        val identity = digest("$scope|$accountId|$id")
        File(context.filesDir, "cloud_avatars").listFiles()
            ?.filter { it.isFile && it.name.startsWith("$identity-") && it != keep }
            ?.forEach { it.delete() }
    }

    private fun deleteLocalAvatarIfReplaced(previous: String?, current: String?) {
        if (previous != null && previous != current && !isRemoteAvatar(previous)) {
            File(previous).takeIf { it.exists() }?.delete()
        }
    }

    private fun isUsableLocalAvatar(value: String): Boolean =
        !isRemoteAvatar(value) && File(value).isFile && File(value).length() > 0L

    private fun isCloudCachedAvatar(value: String?): Boolean =
        value != null && File(value).parentFile?.absoluteFile == File(context.filesDir, "cloud_avatars").absoluteFile

    private fun avatarExtension(contentType: String?, url: String): String {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()
        return when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> url.substringBefore('?').substringAfterLast('.', "img")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
                ?: "img"
        }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun avatarPart(file: File): MultipartBody.Part {
        check(file.isFile) { "Avatar file not found" }
        check(file.length() <= 5 * 1024 * 1024) { "Avatar must not exceed 5 MiB" }
        val type = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }.toMediaType()
        return MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(type))
    }

    private fun isRemoteAvatar(value: String): Boolean =
        value.startsWith("http://") || value.startsWith("https://")

    /** The API may build absolute URLs from its internal localhost origin. Use the configured server host. */
    private fun resolveAvatarUrl(url: String, baseUrl: String): String {
        val base = URI(baseUrl)
        val source = URI(url)
        val origin = "${base.scheme}://${base.rawAuthority}"
        val path = source.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = source.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = source.rawFragment?.let { "#$it" }.orEmpty()
        return "$origin$path$query$fragment"
    }

    private suspend fun endpoint(path: String, baseUrl: String? = null): String =
        "${(baseUrl ?: serverUrl.first()).trimEnd('/')}/$path"

    private suspend fun <T> request(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: HttpException) {
            _syncError.value = cloudErrorMessage(error)
            if (error.code() == 401) {
                localChangesEnabled = false
                appPreferences.setCloudSession(null)
                appPreferences.setCloudSessionHost(null)
                appPreferences.setCloudUser(null)
            }
            throw IOException(cloudErrorMessage(error), error)
        }
    }

    private fun cloudErrorMessage(error: HttpException): String {
        val body = error.response()?.errorBody()?.string()
        val message = body?.let {
            runCatching {
                JsonParser.parseString(it).asJsonObject.get("error")?.let { value ->
                    if (value.isJsonPrimitive) value.asString else value.asJsonObject.get("message")?.asString
                }
            }.getOrNull()
        }
        return message ?: "Cloud request failed (${error.code()})"
    }

    private fun normalizeServerUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "Server address must use HTTP or HTTPS"
        }
        return normalized
    }

    fun createAvatarHttpClient(): OkHttpClient = createClient()

    private fun createClient() = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(appPreferences))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private companion object {
        const val TAG = "CloudSyncRepository"
        const val MAX_AVATAR_BYTES = 5L * 1024L * 1024L
    }
}

private class PersistentCookieJar(private val preferences: AppPreferences) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.firstOrNull { it.name == "messenger_session" }?.let { cookie ->
            kotlinx.coroutines.runBlocking {
                preferences.setCloudSession(cookie.toString())
                preferences.setCloudSessionHost(url.scheme + "://" + url.host + if (url.port != defaultPort(url.scheme)) ":${url.port}" else "")
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val session = kotlinx.coroutines.runBlocking { preferences.cloudSession.first() } ?: return emptyList()
        val host = kotlinx.coroutines.runBlocking { preferences.cloudSessionHost.first() } ?: return emptyList()
        val requestHost = url.scheme + "://" + url.host + if (url.port != defaultPort(url.scheme)) ":${url.port}" else ""
        return if (host == requestHost) Cookie.parse(url, session)?.let(::listOf).orEmpty() else emptyList()
    }

    private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80
}

private fun AgentEntity.toCloudRequest() = CloudAgentRequest(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    defaultModelId = defaultModelId,
    temperature = temperature.toDouble(),
    topP = topP.toDouble(),
    maxTokens = maxTokens,
    isDefault = isDefault,
    followDefaultSystemPrompt = followDefaultSystemPrompt,
    followDefaultModel = followDefaultModel,
    followDefaultTemperature = followDefaultTemperature,
    followDefaultTopP = followDefaultTopP,
    followDefaultMaxTokens = followDefaultMaxTokens,
    marketAgentId = marketAgentId,
    marketAgentVersion = marketAgentVersion,
    marketAgentRole = marketAgentRole,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun AgentEntity.isDefaultAgentBaseline(): Boolean =
    isDefault &&
        name == "默认 Agent" &&
        avatar == null &&
        systemPrompt == "You are a helpful assistant." &&
        defaultModelId == null &&
        temperature == 0.7f &&
        topP == 1.0f &&
        maxTokens == null &&
        !followDefaultSystemPrompt &&
        !followDefaultModel &&
        !followDefaultTemperature &&
        !followDefaultTopP &&
        !followDefaultMaxTokens

private fun ProviderEntity.toCloudRequest(models: List<ModelEntity>) = CloudProviderRequest(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    models = models.map { CloudModelRequest(it.id, it.modelId, it.displayName, it.isEnabled, it.createdAt) },
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ConversationEntity.toCloudRequest(messages: List<MessageEntity>) = CloudConversationRequest(
    id = id,
    title = title,
    agentId = agentId,
    providerId = providerId,
    overrideModelId = overrideModelId,
    overrideTemperature = overrideTemperature?.toDouble(),
    overrideTopP = overrideTopP?.toDouble(),
    overrideMaxTokens = overrideMaxTokens,
    messages = messages.map { CloudMessageRequest(it.id, it.role.lowercase(), it.content, it.timestamp, it.status.uppercase(), it.errorMessage) },
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun CloudAgentDocument.toEntity(avatar: String?) = AgentEntity(
    id = id,
    name = name,
    avatar = avatar,
    systemPrompt = systemPrompt,
    defaultModelId = defaultModelId,
    temperature = temperature.toFloat(),
    topP = topP.toFloat(),
    maxTokens = maxTokens,
    isDefault = isDefault,
    followDefaultSystemPrompt = followDefaultSystemPrompt,
    followDefaultModel = followDefaultModel,
    followDefaultTemperature = followDefaultTemperature,
    followDefaultTopP = followDefaultTopP,
    followDefaultMaxTokens = followDefaultMaxTokens,
    marketAgentId = marketAgentId,
    marketAgentVersion = marketAgentVersion,
    marketAgentRole = marketAgentRole,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun AgentEntity.toDomain() = Agent(
    id = id,
    name = name,
    avatar = avatar,
    systemPrompt = systemPrompt,
    defaultModelId = defaultModelId,
    temperature = temperature,
    topP = topP,
    maxTokens = maxTokens,
    isDefault = isDefault,
    followDefaultSystemPrompt = followDefaultSystemPrompt,
    followDefaultModel = followDefaultModel,
    followDefaultTemperature = followDefaultTemperature,
    followDefaultTopP = followDefaultTopP,
    followDefaultMaxTokens = followDefaultMaxTokens,
    marketAgentId = marketAgentId,
    marketAgentVersion = marketAgentVersion,
    marketAgentRole = marketAgentRole,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun CloudProviderDocument.toEntity() = ProviderEntity(id, name, baseUrl, apiKey, createdAt, updatedAt)

private fun CloudModelDocument.toEntity(providerId: String) =
    ModelEntity(id, providerId, modelId, displayName, isEnabled, createdAt)

private fun CloudConversationDocument.toEntity() = ConversationEntity(
    id = id,
    title = title,
    providerId = providerId,
    agentId = agentId,
    overrideModelId = overrideModelId,
    overrideTemperature = overrideTemperature?.toFloat(),
    overrideTopP = overrideTopP?.toFloat(),
    overrideMaxTokens = overrideMaxTokens,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessage = messages.lastOrNull()?.content
)

private fun CloudMessageDocument.toEntity(conversationId: String) = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.lowercase(),
    content = content,
    timestamp = timestamp,
    status = status.lowercase(),
    errorMessage = errorMessage
)
