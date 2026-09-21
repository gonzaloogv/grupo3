package ar.com.freno.server.infrastructure.cache

import ar.com.freno.shared.contract.Action
import ar.com.freno.shared.contract.AnalysisResult
import ar.com.freno.shared.contract.Analyzer
import ar.com.freno.shared.contract.Category
import ar.com.freno.shared.contract.DecisionSource
import ar.com.freno.shared.contract.ExplanationSource
import ar.com.freno.shared.contract.ReasonCode
import ar.com.freno.shared.contract.Risk
import ar.com.freno.shared.contract.UrlAssessment
import ar.com.freno.shared.contract.UrlAssessmentProvider
import ar.com.freno.shared.contract.UrlAssessmentStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AnalysisCacheTest {
    private val fixedInstant = Instant.parse("2026-09-21T15:00:00Z")

    @Test
    fun `same eventId with same text returns cached hit`() {
        val cache = AnalysisCache(maxEntries = 10, ttl = Duration.ofMinutes(15))
        val result = sampleResult("event-1")

        cache.put("event-1", "Texto original", result)

        val lookup = cache.get("event-1", "Texto original")
        assertIs<CacheLookupResult.Hit>(lookup)
        assertEquals(result, lookup.result)
    }

    @Test
    fun `same eventId with different text returns conflict`() {
        val cache = AnalysisCache(maxEntries = 10, ttl = Duration.ofMinutes(15))
        val result = sampleResult("event-1")

        cache.put("event-1", "Texto original", result)

        val lookup = cache.get("event-1", "Texto completamente distinto")
        assertIs<CacheLookupResult.Conflict>(lookup)
    }

    @Test
    fun `missing eventId returns miss`() {
        val cache = AnalysisCache(maxEntries = 10, ttl = Duration.ofMinutes(15))

        val lookup = cache.get("non-existent", "Cualquier texto")
        assertIs<CacheLookupResult.Miss>(lookup)
    }

    @Test
    fun `cache evicts expired entries after ttl`() {
        var currentInstant = fixedInstant
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = currentInstant
        }
        val cache = AnalysisCache(maxEntries = 10, ttl = Duration.ofMinutes(15), clock = clock)
        val result = sampleResult("event-1")

        cache.put("event-1", "Texto", result)

        // 14 minutes later: still active
        currentInstant = fixedInstant.plus(Duration.ofMinutes(14))
        assertIs<CacheLookupResult.Hit>(cache.get("event-1", "Texto"))

        // 16 minutes later: expired
        currentInstant = fixedInstant.plus(Duration.ofMinutes(16))
        assertIs<CacheLookupResult.Miss>(cache.get("event-1", "Texto"))
    }

    @Test
    fun `cache evicts oldest entries when exceeding maxEntries`() {
        val cache = AnalysisCache(maxEntries = 3, ttl = Duration.ofMinutes(15))

        cache.put("event-1", "Texto 1", sampleResult("event-1"))
        cache.put("event-2", "Texto 2", sampleResult("event-2"))
        cache.put("event-3", "Texto 3", sampleResult("event-3"))

        // Adding 4th entry should evict event-1 (oldest)
        cache.put("event-4", "Texto 4", sampleResult("event-4"))

        assertIs<CacheLookupResult.Miss>(cache.get("event-1", "Texto 1"))
        assertIs<CacheLookupResult.Hit>(cache.get("event-2", "Texto 2"))
        assertIs<CacheLookupResult.Hit>(cache.get("event-3", "Texto 3"))
        assertIs<CacheLookupResult.Hit>(cache.get("event-4", "Texto 4"))
    }

    @Test
    fun `Google match must be reassessed but event identity still conflicts`() {
        val cache = AnalysisCache()
        val result = sampleResult("event-1").copy(urlAssessment = UrlAssessment(
            UrlAssessmentStatus.MATCH, UrlAssessmentProvider.GOOGLE_SAFE_BROWSING,
            listOf(ar.com.freno.shared.contract.UrlThreatType.SOCIAL_ENGINEERING),
        ))
        cache.put("event-1", "Texto", result)
        assertIs<CacheLookupResult.Miss>(cache.get("event-1", "Texto"))
        assertIs<CacheLookupResult.Conflict>(cache.get("event-1", "Otro texto"))
    }

    private fun sampleResult(eventId: String) = AnalysisResult(
        eventId = eventId,
        risk = Risk.LOW,
        category = Category.NONE,
        reasonCode = ReasonCode.NO_CLEAR_SIGNAL,
        reasonSimple = "Mensaje sin señales de riesgo.",
        action = Action.NONE,
        analyzer = Analyzer.GEMINI,
        model = "gemini-3.5-flash-lite",
        promptVersion = "freno-v1",
        explanationSource = ExplanationSource.GEMINI,
        decisionSources = listOf(DecisionSource.GEMINI),
        urlAssessment = UrlAssessment(UrlAssessmentStatus.NO_URL, UrlAssessmentProvider.NONE, emptyList()),
    )
}
