package cc.ptoe.messenger.data.cloud

import android.content.Context
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
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
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
    val createdAt: Long,
    val updatedAt: Long
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
        Log.i(
            TAG,
            "Cloud account id=${response.user.id} email=${response.user.email} " +
            "serverSyncVersion=${response.user.syncVersion}"
        )
        saveUser(response.user)
        if (updateLocalAvatar) {
            appPreferences.setUserAvatar(response.user.avatarUrl)
        }
        return response.user
    }

    suspend fun logout() {
        runCatching { request { api.logout(endpoint("api/auth/logout")) } }
        user.first()?.let { appPreferences.clearCloudSyncVersion(it.id) }
        localChangesEnabled = false
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
                .onFailure { _syncError.value = it.message ?: "Avatar synchronization failed" }
        }
    }

    suspend fun uploadUserAvatar(path: String?): CloudAvatarResponse = withContext(Dispatchers.IO) {
        checkSignedIn()
        val localFile = path?.takeUnless(::isRemoteAvatar)?.let(::File)
        val response = if (path == null || isRemoteAvatar(path)) {
            request { api.deleteUserAvatar(endpoint("api/avatars/user")) }
        } else {
            request { api.uploadUserAvatar(endpoint("api/avatars/user"), avatarPart(File(path))) }
        }
        appPreferences.setUserAvatar(response.url)
        refreshCachedUserAvatar(response.url)
        localFile?.delete()
        response
    }

    suspend fun uploadAgentAvatar(agentId: String, path: String?): CloudAvatarResponse = withContext(Dispatchers.IO) {
        checkSignedIn()
        val localFile = path?.takeUnless(::isRemoteAvatar)?.let(::File)
        val response = if (path == null || isRemoteAvatar(path)) {
            request { api.deleteAgentAvatar(endpoint("api/avatars/agents/$agentId")) }
        } else {
            request { api.uploadAgentAvatar(endpoint("api/avatars/agents/$agentId"), avatarPart(localFile!!)) }
        }
        database.agentDao().getById(agentId).first()?.let { agent ->
            database.agentDao().insert(agent.copy(avatar = response.url))
        }
        localFile?.delete()
        response
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
                !isRemoteAvatar(userAvatar) -> uploadUserAvatar(userAvatar).version
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
        if (agent.avatar != null && !isRemoteAvatar(agent.avatar)) {
            uploadAgentAvatar(agent.id, agent.avatar)
        }
    }

    private suspend fun applyDelta(delta: CloudSyncResponse) {
        delta.agents.forEach { remote ->
            if (remote.deleted) {
                database.conversationDao().deleteByAgentId(remote.id)
                database.agentDao().delete(remote.id)
            } else {
                if (remote.isDefault) {
                    database.agentDao().getAllEntities()
                        .filter { it.isDefault && it.id != remote.id }
                        .forEach { database.agentDao().insert(it.copy(isDefault = false)) }
                }
                database.agentDao().insert(remote.toEntity())
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

    private suspend fun clearLocalAccountData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        appPreferences.setCurrentAgentId(null)
        appPreferences.setUserAvatar(null)
        File(context.filesDir, "user_avatars").deleteRecursively()
        File(context.filesDir, "agent_avatars").deleteRecursively()
        File(context.filesDir, "cloud_avatars").deleteRecursively()
    }

    private suspend fun checkSignedIn() {
        check(appPreferences.cloudSession.first() != null) { "Please sign in first" }
    }

    private suspend fun prepareRequestServer(override: String?): String {
        val baseUrl = normalizeServerUrl(override ?: serverUrl.first())
        val currentHost = appPreferences.cloudSessionHost.first()
        if (currentHost != null && currentHost != baseUrl) {
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

    private suspend fun refreshCachedUserAvatar(url: String?) {
        val current = user.first() ?: return
        saveUser(current.copy(avatarUrl = url))
    }

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

    private fun createClient() = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(appPreferences))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private companion object {
        const val TAG = "CloudSyncRepository"
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

private fun CloudAgentDocument.toEntity() = AgentEntity(
    id = id,
    name = name,
    avatar = avatarUrl,
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
