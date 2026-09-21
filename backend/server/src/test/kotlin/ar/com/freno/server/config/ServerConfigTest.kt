package ar.com.freno.server.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerConfigTest {
    @Test
    fun `environment configuration requires the demo token`() {
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(emptyMap())
        }
    }

    @Test
    fun `environment configuration uses the regional time zone and supplied port`() {
        val config = ServerConfig.fromEnvironment(
            mapOf(
                "DEMO_API_TOKEN" to "local-secret",
                "GEMINI_API_KEY" to "gemini-test-key",
                "SERVER_PORT" to "9090",
                "APP_TIME_ZONE" to "America/Argentina/Buenos_Aires",
            ),
        )

        assertEquals(9090, config.port)
        assertEquals("America/Argentina/Buenos_Aires", config.timeZone)
        assertEquals("gemini-test-key", config.geminiApiKey)
        assertEquals("gemini-3.5-flash-lite", config.geminiModel)
        assertEquals(20_000L, config.geminiTimeoutMillis)
    }

    @Test
    fun `environment configuration requires a Gemini key`() {
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(mapOf("DEMO_API_TOKEN" to "local-secret"))
        }
    }

    @Test
    fun `local env file supplies keys while process environment has precedence`() {
        val path = Files.createTempFile("freno-config", ".env")
        try {
            Files.writeString(path, """
                # Local development only
                DEMO_API_TOKEN=file-token
                GEMINI_API_KEY=file-gemini-key
                GEMINI_MODEL=gemini-3.5-flash-lite
                GEMINI_TIMEOUT_MS=1800
            """.trimIndent())

            val config = ServerConfig.fromDotEnv(
                path = path,
                environment = mapOf("DEMO_API_TOKEN" to "process-token"),
            )

            assertEquals("process-token", config.demoApiToken)
            assertEquals("file-gemini-key", config.geminiApiKey)
            assertEquals(1800L, config.geminiTimeoutMillis)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
