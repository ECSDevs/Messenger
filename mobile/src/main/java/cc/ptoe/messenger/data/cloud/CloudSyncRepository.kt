package cc.ptoe.messenger.data.cloud

import android.os.Build
import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import cc.ptoe.messenger.data.local.AppPreferences
import cc.ptoe.messenger.data.local.MessengerDatabase
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.UUID

const val DEFAULT_CLOUD_SERVER_URL = "https://messenger.ptoe.cc"

private data class CredentialsRequest(val email: String, val password: String)
private data class UserResponse(val user: CloudUser)
private data class ManifestResponse(val manifest: CloudManifest?)
private data class BackupResponse(val manifest: CloudManifest, val payload: CloudBackupPayload)
private data class SuccessResponse(val success: Boolean)

private interface CloudApi {
    @POST suspend fun register(@Url url: String, @Body body: CredentialsRequest): UserResponse
    @POST suspend fun login(@Url url: String, @Body body: CredentialsRequest): UserResponse
    @POST suspend fun logout(@Url url: String): SuccessResponse
    @GET suspend fun me(@Url url: String): UserResponse
    @GET suspend fun manifest(@Url url: String): ManifestResponse
    @PUT suspend fun upload(@Url url: String, @Body payload: CloudBackupPayload): BackupResponse
    @GET suspend fun download(@Url url: String): BackupResponse
}

class CloudSyncRepository(
    private val appPreferences: AppPreferences,
    private val database: MessengerDatabase,
    private val context: Context,
    private val gson: Gson = Gson()
) {
    private val api = Retrofit.Builder()
        .baseUrl("https://messenger.ptoe.cc/")
        .client(createClient())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(CloudApi::class.java)

    val user: Flow<CloudUser?> = appPreferences.cloudUser
        .mapToUser()
        .distinctUntilChanged()

    val serverUrl: Flow<String> = appPreferences.cloudServerUrl
        .map { url -> url ?: DEFAULT_CLOUD_SERVER_URL }
        .distinctUntilChanged()

    suspend fun setServerUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "服务器地址必须使用 HTTP 或 HTTPS"
        }
        appPreferences.setCloudServerUrl(normalized.takeUnless { it == DEFAULT_CLOUD_SERVER_URL })
    }

    suspend fun login(email: String, password: String, serverUrl: String? = null): CloudUser =
        api.login(endpoint("api/auth/login", serverUrl), CredentialsRequest(email.trim(), password)).user.also { saveUser(it) }

    suspend fun register(email: String, password: String, serverUrl: String? = null): CloudUser =
        api.register(endpoint("api/auth/register", serverUrl), CredentialsRequest(email.trim(), password)).user.also { saveUser(it) }

    suspend fun logout() {
        runCatching { api.logout(endpoint("api/auth/logout")) }
        appPreferences.setCloudSession(null)
        appPreferences.setCloudUser(null)
    }

    suspend fun upload(): CloudManifest {
        checkSignedIn()
        return api.upload(endpoint("api/backups/latest"), createPayload()).manifest
    }

    suspend fun downloadAndRestore(): CloudManifest {
        checkSignedIn()
        val response = api.download(endpoint("api/backups/latest"))
        restorePayload(response.payload)
        return response.manifest
    }

    suspend fun getManifest(): CloudManifest? {
        checkSignedIn()
        return api.manifest(endpoint("api/backups/manifest")).manifest
    }

    private suspend fun createPayload() = CloudBackupPayload(
        exportedAt = System.currentTimeMillis(),
        device = CloudDevice(deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"),
        avatars = createAvatarAssets(),
        userAvatarKey = appPreferences.userAvatar.first(),
        providers = database.providerDao().getAllEntities(),
        models = database.modelDao().getAllEntities(),
        agents = database.agentDao().getAllEntities(),
        conversations = database.conversationDao().getAllEntities(),
        messages = database.messageDao().getAllEntities().map { message ->
            CloudMessage(
                id = message.id,
                conversationId = message.conversationId,
                role = message.role.lowercase(),
                content = message.content,
                timestamp = message.timestamp,
                status = message.status.uppercase(),
                errorMessage = message.errorMessage
            )
        }
    )

    private suspend fun restorePayload(payload: CloudBackupPayload) {
        val restoredAvatars = restoreAvatarAssets(payload.avatars)
        val restoredUserAvatar = payload.userAvatarKey?.let { restoredAvatars[it] }
        database.withTransaction {
            database.messageDao().deleteAll()
            database.conversationDao().deleteAll()
            database.agentDao().deleteAll()
            database.modelDao().deleteAll()
            database.providerDao().deleteAll()
            payload.providers.forEach { database.providerDao().insert(it) }
            payload.models.orEmpty().forEach { database.modelDao().insert(it) }
            payload.agents.forEach { agent ->
                database.agentDao().insert(agent.copy(avatar = agent.avatar?.let { restoredAvatars[it] ?: it }))
            }
            payload.conversations.forEach { database.conversationDao().insert(it) }
            payload.messages.forEach { message ->
                database.messageDao().insert(
                    cc.ptoe.messenger.data.local.entity.MessageEntity(
                        id = message.id,
                        conversationId = message.conversationId,
                        role = message.role.lowercase(),
                        content = message.content,
                        timestamp = message.timestamp,
                        status = message.status.lowercase(),
                        errorMessage = message.errorMessage
                    )
                )
            }
        }
        appPreferences.setUserAvatar(restoredUserAvatar)
    }

    private suspend fun createAvatarAssets(): List<CloudAvatarAsset> {
        val paths = buildList {
            appPreferences.userAvatar.first()?.let(::add)
            database.agentDao().getAllEntities().mapNotNull { it.avatar }.forEach(::add)
        }.distinct()
        return paths.mapNotNull { path ->
            val file = File(path)
            if (!file.isFile) return@mapNotNull null
            CloudAvatarAsset(
                key = path,
                data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
                extension = file.extension.ifBlank { "jpg" }
            )
        }
    }

    private fun restoreAvatarAssets(assets: List<CloudAvatarAsset>): Map<String, String> {
        if (assets.isEmpty()) return emptyMap()
        val directory = File(context.filesDir, "cloud_avatars").apply { mkdirs() }
        return assets.mapNotNull { asset ->
            runCatching {
                val file = File(directory, "${UUID.randomUUID()}.${asset.extension}")
                file.writeBytes(Base64.decode(asset.data, Base64.DEFAULT))
                asset.key to file.absolutePath
            }.getOrNull()
        }.toMap()
    }

    private suspend fun checkSignedIn() {
        check(appPreferences.cloudSession.first() != null) { "请先登录账户" }
    }

    private suspend fun saveUser(value: CloudUser) {
        appPreferences.setCloudUser(gson.toJson(value))
    }

    private suspend fun endpoint(path: String, overrideServerUrl: String? = null): String {
        val baseUrl = overrideServerUrl?.trim()?.trimEnd('/') ?: serverUrl.first()
        return "$baseUrl/$path"
    }

    private fun createClient() = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(appPreferences))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()
}

private fun Flow<String?>.mapToUser(): Flow<CloudUser?> = map { value: String? ->
    if (value.isNullOrBlank()) null else runCatching { Gson().fromJson(value, CloudUser::class.java) }.getOrNull()
}

private class PersistentCookieJar(private val preferences: AppPreferences) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.firstOrNull { it.name == "messenger_session" }?.let { cookie ->
            kotlinx.coroutines.runBlocking { preferences.setCloudSession(cookie.toString()) }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val value = kotlinx.coroutines.runBlocking { preferences.cloudSession.first() } ?: return emptyList()
        return Cookie.parse(url, value)?.let(::listOf) ?: emptyList()
    }
}
