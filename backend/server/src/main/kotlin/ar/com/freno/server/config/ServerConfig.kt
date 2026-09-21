package ar.com.freno.server.config

import java.time.ZoneId

data class ServerConfig(
    val host: String,
    val port: Int,
    val demoApiToken: String,
    val timeZone: String,
    val promptVersion: String,
) {
    init {
        require(port in 1..65535) { "SERVER_PORT must be between 1 and 65535" }
        require(demoApiToken.isNotBlank()) { "DEMO_API_TOKEN is required" }
        ZoneId.of(timeZone)
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ServerConfig =
            ServerConfig(
                host = environment["SERVER_HOST"] ?: "0.0.0.0",
                port = environment["SERVER_PORT"]?.toIntOrNull() ?: 8080,
                demoApiToken = environment["DEMO_API_TOKEN"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("DEMO_API_TOKEN is required"),
                timeZone = environment["APP_TIME_ZONE"] ?: "America/Argentina/Buenos_Aires",
                promptVersion = environment["PROMPT_VERSION"] ?: "freno-v1",
            )
    }
}
