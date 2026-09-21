package ar.com.freno.server.config

import java.time.ZoneId
import java.nio.file.Files
import java.nio.file.Path

data class ServerConfig(
    val host: String,
    val port: Int,
    val demoApiToken: String,
    val timeZone: String,
    val promptVersion: String,
    val geminiApiKey: String,
    val geminiModel: String,
    val geminiTimeoutMillis: Long,
    val safeBrowsingApiKey: String,
    val safeBrowsingTimeoutMillis: Long,
    val safeBrowsingMaxUrls: Int,
    val safeBrowsingCacheMaxEntries: Int,
) {
    init {
        require(port in 1..65535) { "SERVER_PORT must be between 1 and 65535" }
        require(demoApiToken.isNotBlank()) { "DEMO_API_TOKEN is required" }
        ZoneId.of(timeZone)
        require(geminiApiKey.isNotBlank()) { "GEMINI_API_KEY is required" }
        require(geminiTimeoutMillis > 0) { "GEMINI_TIMEOUT_MS must be positive" }
        require(safeBrowsingApiKey.isNotBlank()) { "SAFE_BROWSING_API_KEY is required" }
        require(safeBrowsingTimeoutMillis > 0) { "SAFE_BROWSING_TIMEOUT_MS must be positive" }
        require(safeBrowsingMaxUrls in 1..3) { "SAFE_BROWSING_MAX_URLS must be between 1 and 3" }
        require(safeBrowsingCacheMaxEntries > 0) {
            "SAFE_BROWSING_CACHE_MAX_ENTRIES must be positive"
        }
    }

    companion object {
        fun fromDotEnv(
            path: Path = Path.of(".env"),
            environment: Map<String, String> = System.getenv(),
        ): ServerConfig {
            val fileValues = if (Files.isRegularFile(path)) {
                Files.readAllLines(path).mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith('#')) return@mapNotNull null
                    val separator = trimmed.indexOf('=')
                    if (separator < 1) return@mapNotNull null
                    val key = trimmed.substring(0, separator).trim()
                    if (!key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return@mapNotNull null
                    val rawValue = trimmed.substring(separator + 1).trim()
                    val value = if (rawValue.length >= 2 &&
                        (rawValue.first() == '"' && rawValue.last() == '"' ||
                            rawValue.first() == '\'' && rawValue.last() == '\'')) {
                        rawValue.substring(1, rawValue.length - 1)
                    } else rawValue
                    key to value
                }.toMap()
            } else emptyMap()
            return fromEnvironment(fileValues + environment)
        }

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ServerConfig =
            ServerConfig(
                host = environment["SERVER_HOST"] ?: "0.0.0.0",
                port = environment["SERVER_PORT"]?.toIntOrNull() ?: 8080,
                demoApiToken = environment["DEMO_API_TOKEN"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("DEMO_API_TOKEN is required"),
                timeZone = environment["APP_TIME_ZONE"] ?: "America/Argentina/Buenos_Aires",
                promptVersion = environment["PROMPT_VERSION"] ?: "freno-v1",
                geminiApiKey = environment["GEMINI_API_KEY"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("GEMINI_API_KEY is required"),
                geminiModel = environment["GEMINI_MODEL"] ?: "gemini-3.5-flash-lite",
                geminiTimeoutMillis = environment["GEMINI_TIMEOUT_MS"]?.toLongOrNull() ?: 20_000,
                safeBrowsingApiKey = environment["SAFE_BROWSING_API_KEY"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("SAFE_BROWSING_API_KEY is required"),
                safeBrowsingTimeoutMillis = environment["SAFE_BROWSING_TIMEOUT_MS"]?.toLongOrNull() ?: 1_500,
                safeBrowsingMaxUrls = environment["SAFE_BROWSING_MAX_URLS"]?.toIntOrNull() ?: 3,
                safeBrowsingCacheMaxEntries =
                    environment["SAFE_BROWSING_CACHE_MAX_ENTRIES"]?.toIntOrNull() ?: 100,
            )
    }
}
