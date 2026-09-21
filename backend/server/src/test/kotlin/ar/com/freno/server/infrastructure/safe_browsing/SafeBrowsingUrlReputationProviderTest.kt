package ar.com.freno.server.infrastructure.safe_browsing

import ar.com.freno.shared.contract.UrlAssessmentProvider
import ar.com.freno.shared.contract.UrlAssessmentStatus
import ar.com.freno.shared.contract.UrlThreatType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafeBrowsingUrlReputationProviderTest {
    private val json = Json

    @Test
    fun `empty URL list skips Google and returns no URL`() = runBlocking {
        val client = HttpClient(MockEngine { error("Safe Browsing must not be called") })
        try {
            val result = provider(client).assess(emptyList())

            assertEquals(UrlAssessmentStatus.NO_URL, result.status)
            assertEquals(UrlAssessmentProvider.NONE, result.provider)
            assertTrue(result.threatTypes.isEmpty())
        } finally {
            client.close()
        }
    }

    @Test
    fun `empty Google response means URLs are not reported`() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals(
                "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=test-safe-browsing-key",
                request.url.toString(),
            )
            val payload = json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val threatInfo = payload.getValue("threatInfo").jsonObject
            assertEquals(
                setOf("MALWARE", "SOCIAL_ENGINEERING"),
                threatInfo.getValue("threatTypes").jsonArray.map { it.jsonPrimitive.content }.toSet(),
            )
            assertEquals(
                listOf("https://one.example/path", "https://two.example/"),
                threatInfo.getValue("threatEntries").jsonArray.map {
                    it.jsonObject.getValue("url").jsonPrimitive.content
                },
            )
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = provider(client).assess(
                listOf("https://one.example/path", "https://two.example/"),
            )

            assertEquals(1, requests)
            assertEquals(UrlAssessmentStatus.NO_MATCH, result.status)
            assertEquals(UrlAssessmentProvider.GOOGLE_SAFE_BROWSING, result.provider)
            assertTrue(result.threatTypes.isEmpty())
        } finally {
            client.close()
        }
    }

    @Test
    fun `Google matches map only supported threat types`() = runBlocking {
        val responseBody = """
            {
              "matches": [
                {
                  "threatType": "SOCIAL_ENGINEERING",
                  "platformType": "ANY_PLATFORM",
                  "threatEntryType": "URL",
                  "threat": {"url": "https://phishing.example/"},
                  "cacheDuration": "300.000s"
                },
                {
                  "threatType": "MALWARE",
                  "platformType": "ANY_PLATFORM",
                  "threatEntryType": "URL",
                  "threat": {"url": "https://malware.example/"},
                  "cacheDuration": "60s"
                },
                {
                  "threatType": "UNWANTED_SOFTWARE",
                  "platformType": "ANY_PLATFORM",
                  "threatEntryType": "URL",
                  "threat": {"url": "https://ignored.example/"},
                  "cacheDuration": "60s"
                }
              ]
            }
        """.trimIndent()
        val client = HttpClient(MockEngine {
            respond(responseBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = provider(client).assess(
                listOf("https://phishing.example/", "https://malware.example/", "https://ignored.example/"),
            )

            assertEquals(UrlAssessmentStatus.MATCH, result.status)
            assertEquals(
                setOf(UrlThreatType.SOCIAL_ENGINEERING, UrlThreatType.MALWARE),
                result.threatTypes.toSet(),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `positive match is reused for the provider cache duration`() = runBlocking {
        var requests = 0
        val responseBody = """
            {
              "matches": [{
                "threatType": "SOCIAL_ENGINEERING",
                "platformType": "ANY_PLATFORM",
                "threatEntryType": "URL",
                "threat": {"url": "https://phishing.example/"},
                "cacheDuration": "300.000s"
              }]
            }
        """.trimIndent()
        val client = HttpClient(MockEngine {
            requests++
            respond(responseBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val provider = provider(client)

            val first = provider.assess(listOf("https://phishing.example/"))
            val second = provider.assess(listOf("https://phishing.example/"))

            assertEquals(1, requests)
            assertEquals(UrlAssessmentStatus.MATCH, first.status)
            assertEquals(first, second)
        } finally {
            client.close()
        }
    }

    @Test
    fun `expired positive match is refreshed`() = runBlocking {
        var requests = 0
        val clock = MutableClock(Instant.parse("2026-09-21T12:00:00Z"))
        val matched = """
            {"matches":[{
              "threatType":"MALWARE",
              "platformType":"ANY_PLATFORM",
              "threatEntryType":"URL",
              "threat":{"url":"https://malware.example/"},
              "cacheDuration":"1s"
            }]}
        """.trimIndent()
        val client = HttpClient(MockEngine {
            requests++
            val body = if (requests == 1) matched else "{}"
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val provider = provider(client, clock = clock)
            assertEquals(
                UrlAssessmentStatus.MATCH,
                provider.assess(listOf("https://malware.example/")).status,
            )

            clock.advanceSeconds(2)
            val refreshed = provider.assess(listOf("https://malware.example/"))

            assertEquals(2, requests)
            assertEquals(UrlAssessmentStatus.NO_MATCH, refreshed.status)
        } finally {
            client.close()
        }
    }

    @Test
    fun `cache duration starts when the Google response is received`() = runBlocking {
        var requests = 0
        val clock = MutableClock(Instant.parse("2026-09-21T12:00:00Z"))
        val matched = """
            {"matches":[{
              "threatType":"MALWARE",
              "platformType":"ANY_PLATFORM",
              "threatEntryType":"URL",
              "threat":{"url":"https://malware.example/"},
              "cacheDuration":"3s"
            }]}
        """.trimIndent()
        val client = HttpClient(MockEngine {
            requests++
            clock.advanceSeconds(2)
            respond(matched, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val provider = provider(client, clock = clock)
            provider.assess(listOf("https://malware.example/"))

            clock.advanceSeconds(2)
            val cached = provider.assess(listOf("https://malware.example/"))

            assertEquals(1, requests)
            assertEquals(UrlAssessmentStatus.MATCH, cached.status)
        } finally {
            client.close()
        }
    }

    @Test
    fun `cached match that expires during a mixed request is not reused`() = runBlocking {
        var requests = 0
        val clock = MutableClock(Instant.parse("2026-09-21T12:00:00Z"))
        val matched = """
            {"matches":[{
              "threatType":"MALWARE",
              "platformType":"ANY_PLATFORM",
              "threatEntryType":"URL",
              "threat":{"url":"https://cached.example/"},
              "cacheDuration":"1s"
            }]}
        """.trimIndent()
        val client = HttpClient(MockEngine {
            requests++
            if (requests == 1) {
                respond(matched, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                clock.advanceSeconds(2)
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
        try {
            val provider = provider(client, clock = clock)
            provider.assess(listOf("https://cached.example/"))

            val result = provider.assess(
                listOf("https://cached.example/", "https://uncached.example/"),
            )

            assertEquals(2, requests)
            assertEquals(UrlAssessmentStatus.UNAVAILABLE, result.status)
        } finally {
            client.close()
        }
    }

    @Test
    fun `concurrent responses preserve all active threat evidence`() = runBlocking {
        val requests = AtomicInteger()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        fun response(threatType: String) = """
            {"matches":[{
              "threatType":"$threatType",
              "platformType":"ANY_PLATFORM",
              "threatEntryType":"URL",
              "threat":{"url":"https://race.example/"},
              "cacheDuration":"300s"
            }]}
        """.trimIndent()
        val client = HttpClient(MockEngine {
            if (requests.incrementAndGet() == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                respond(
                    response("SOCIAL_ENGINEERING"),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                releaseFirst.complete(Unit)
                respond(
                    response("MALWARE"),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        })
        try {
            val provider = provider(client)
            coroutineScope {
                val first = async { provider.assess(listOf("https://race.example/")) }
                firstStarted.await()
                val second = async { provider.assess(listOf("https://race.example/")) }
                second.await()
                first.await()
            }

            val cached = provider.assess(listOf("https://race.example/"))

            assertEquals(2, requests.get())
            assertEquals(
                setOf(UrlThreatType.SOCIAL_ENGINEERING, UrlThreatType.MALWARE),
                cached.threatTypes.toSet(),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `positive cache evicts its oldest URL at the configured capacity`() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            val payload = json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val url = payload.getValue("threatInfo").jsonObject
                .getValue("threatEntries").jsonArray.single().jsonObject
                .getValue("url").jsonPrimitive.content
            respond(
                """
                    {"matches":[{
                      "threatType":"MALWARE",
                      "platformType":"ANY_PLATFORM",
                      "threatEntryType":"URL",
                      "threat":{"url":"$url"},
                      "cacheDuration":"300s"
                    }]}
                """.trimIndent(),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        try {
            val provider = provider(client, maxCacheEntries = 2)
            provider.assess(listOf("https://one.example/"))
            provider.assess(listOf("https://two.example/"))
            provider.assess(listOf("https://three.example/"))
            provider.assess(listOf("https://one.example/"))

            assertEquals(4, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `match with unusable cache duration is reported but not cached`() = runBlocking {
        var requests = 0
        val matched = """
            {"matches":[{
              "threatType":"MALWARE",
              "platformType":"ANY_PLATFORM",
              "threatEntryType":"URL",
              "threat":{"url":"https://malware.example/"},
              "cacheDuration":"invalid"
            }]}
        """.trimIndent()
        val client = HttpClient(MockEngine {
            requests++
            respond(
                if (requests == 1) matched else "{}",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        try {
            val provider = provider(client)
            val first = provider.assess(listOf("https://malware.example/"))
            val second = provider.assess(listOf("https://malware.example/"))

            assertEquals(UrlAssessmentStatus.MATCH, first.status)
            assertEquals(UrlAssessmentStatus.NO_MATCH, second.status)
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `timeout returns unavailable instead of throwing or reporting no match`() = runBlocking {
        val client = HttpClient(MockEngine {
            delay(100)
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = provider(client, timeoutMillis = 10)
                .assess(listOf("https://slow.example/"))

            assertEquals(UrlAssessmentStatus.UNAVAILABLE, result.status)
            assertEquals(UrlAssessmentProvider.GOOGLE_SAFE_BROWSING, result.provider)
            assertTrue(result.threatTypes.isEmpty())
        } finally {
            client.close()
        }
    }

    @Test
    fun `rate limit returns unavailable instead of reporting no match`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond("{}", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = provider(client).assess(listOf("https://limited.example/"))

            assertEquals(UrlAssessmentStatus.UNAVAILABLE, result.status)
            assertEquals(UrlAssessmentProvider.GOOGLE_SAFE_BROWSING, result.provider)
            assertTrue(result.threatTypes.isEmpty())
        } finally {
            client.close()
        }
    }

    @Test
    fun `configured URL limit is enforced before calling Google`() {
        val client = HttpClient(MockEngine { error("Safe Browsing must not be called") })
        try {
            runBlocking {
                assertFailsWith<IllegalArgumentException> {
                    provider(client, maxUrls = 2).assess(
                        listOf("https://one.example/", "https://two.example/", "https://three.example/"),
                    )
                }
            }
        } finally {
            client.close()
        }
    }

    private fun provider(
        client: HttpClient,
        timeoutMillis: Long = 1_500,
        clock: Clock = Clock.systemUTC(),
        maxUrls: Int = 3,
        maxCacheEntries: Int = 100,
    ) = SafeBrowsingUrlReputationProvider(
        client = client,
        apiKey = "test-safe-browsing-key",
        timeoutMillis = timeoutMillis,
        clock = clock,
        maxUrls = maxUrls,
        maxCacheEntries = maxCacheEntries,
    )
}

private class MutableClock(
    private var instant: Instant,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    override fun instant(): Instant = instant

    fun advanceSeconds(seconds: Long) {
        instant = instant.plusSeconds(seconds)
    }
}
