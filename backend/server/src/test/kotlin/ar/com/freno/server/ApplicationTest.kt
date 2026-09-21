package ar.com.freno.server

import ar.com.freno.server.application.FakeRiskAnalyzer
import ar.com.freno.server.application.RiskAnalyzer
import ar.com.freno.server.config.ServerConfig
import ar.com.freno.shared.contract.AnalysisResult
import ar.com.freno.shared.contract.Analyzer
import ar.com.freno.shared.contract.ExplanationSource
import ar.com.freno.shared.contract.HealthResponse
import ar.com.freno.shared.contract.Risk
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val demoToken = "test-demo-token"

    @Test
    fun `health is public and does not expose secrets`() = testApplication {
        application { module(testConfig(), FakeRiskAnalyzer()) }

        val response = client.get("/health")
        val responseBody = response.bodyAsText()
        val health = json.decodeFromString<HealthResponse>(responseBody)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", health.status)
        assertEquals("America/Argentina/Buenos_Aires", health.timeZone)
        assertFalse(responseBody.contains(demoToken))
        assertFalse(responseBody.contains("apiKey", ignoreCase = true))
    }

    @Test
    fun `analyze rejects requests without a bearer token`() = testApplication {
        application { module(testConfig(), FakeRiskAnalyzer()) }

        val response = client.post("/v1/analyze") {
            contentType(ContentType.Application.Json)
            setBody(validRequestBody())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `analyze rejects an invalid bearer token`() = testApplication {
        application { module(testConfig(), FakeRiskAnalyzer()) }

        val response = client.post("/v1/analyze") {
            bearerAuth("wrong-token")
            contentType(ContentType.Application.Json)
            setBody(validRequestBody())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `analyze returns the shared contract and preserves event id`() = testApplication {
        application { module(testConfig(), FakeRiskAnalyzer()) }

        val response = client.post("/v1/analyze") {
            bearerAuth(demoToken)
            contentType(ContentType.Application.Json)
            setBody(validRequestBody())
        }
        val result = json.decodeFromString<AnalysisResult>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("demo-001", result.eventId)
        assertEquals(Risk.HIGH, result.risk)
        assertEquals(Analyzer.FAKE, result.analyzer)
        assertNull(result.model)
        assertEquals(ExplanationSource.TEMPLATE, result.explanationSource)
        assertTrue(result.decisionSources.isNotEmpty())
    }

    @Test
    fun `analyze rejects more than three URLs before invoking analyzer`() = testApplication {
        var analyzerCalls = 0
        application {
            module(testConfig(), RiskAnalyzer {
                analyzerCalls++
                FakeRiskAnalyzer().analyze(it)
            })
        }
        val response = client.post("/v1/analyze") {
            bearerAuth(demoToken)
            contentType(ContentType.Application.Json)
            setBody(validRequestBody().replace("\"locale\"", "\"urls\":[\"https://a.example\",\"https://b.example\",\"https://c.example\",\"https://d.example\"],\"locale\""))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, analyzerCalls)
    }

    @Test
    fun `analyze rejects non HTTP URLs`() = testApplication {
        var analyzerCalls = 0
        application {
            module(testConfig(), RiskAnalyzer {
                analyzerCalls++
                FakeRiskAnalyzer().analyze(it)
            })
        }
        val response = client.post("/v1/analyze") {
            bearerAuth(demoToken)
            contentType(ContentType.Application.Json)
            setBody(validRequestBody().replace("\"locale\"", "\"urls\":[\"javascript:alert(1)\"],\"locale\""))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, analyzerCalls)
    }

    private fun testConfig() = ServerConfig(
        host = "127.0.0.1",
        port = 8080,
        demoApiToken = demoToken,
        timeZone = "America/Argentina/Buenos_Aires",
        promptVersion = "freno-v1",
        geminiApiKey = "test-gemini-key",
        geminiModel = "gemini-3.5-flash-lite",
        geminiTimeoutMillis = 5_000,
        safeBrowsingApiKey = "test-safe-browsing-key",
        safeBrowsingTimeoutMillis = 1_500,
        safeBrowsingMaxUrls = 3,
    )

    private fun validRequestBody() =
        """
        {
          "eventId": "demo-001",
          "source": "SMS",
          "text": "Soy tu hijo, cambié de número. Transferime urgente al alias [ALIAS].",
          "contentIncomplete": false,
          "locale": "es-AR"
        }
        """.trimIndent()
}
