package cc.ptoe.messenger.domain.model

data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    val maskedApiKey: String
        get() = maskApiKey(apiKey)

    companion object {
        fun maskApiKey(apiKey: String): String {
            if (apiKey.length <= 8) {
                return "*".repeat(apiKey.length)
            }
            val prefix = apiKey.take(4)
            val suffix = apiKey.takeLast(4)
            return "$prefix****$suffix"
        }
    }
}
