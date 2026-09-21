package ar.com.freno.server.config

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
                "SERVER_PORT" to "9090",
                "APP_TIME_ZONE" to "America/Argentina/Buenos_Aires",
            ),
        )

        assertEquals(9090, config.port)
        assertEquals("America/Argentina/Buenos_Aires", config.timeZone)
    }
}
