package ar.com.freno.server.infrastructure.cache

import ar.com.freno.shared.contract.AnalysisResult
import ar.com.freno.shared.contract.AnalysisRequest
import ar.com.freno.server.application.RiskAnalyzer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap

sealed interface CacheLookupResult {
    data class Hit(val result: AnalysisResult) : CacheLookupResult
    data object Conflict : CacheLookupResult
    data object Miss : CacheLookupResult
}

class AnalysisCache(
    private val maxEntries: Int = 100,
    private val ttl: Duration = Duration.ofMinutes(15),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Any()
    private val cache = LinkedHashMap<String, CachedEntry>()
    // Bounded lock storage; duplicate events share a lock even while analysis is pending.
    private val stripes = Array(128) { Mutex() }

    suspend fun getOrAnalyze(request: AnalysisRequest, analyzer: RiskAnalyzer): CacheLookupResult =
        stripes[(request.eventId.hashCode() and Int.MAX_VALUE) % stripes.size].withLock {
            val content = Json.encodeToString(request)
            when (val existing = get(request.eventId, content)) {
                CacheLookupResult.Miss -> {
                    val result = analyzer.analyze(request)
                    put(request.eventId, content, result)
                    CacheLookupResult.Hit(result)
                }
                else -> existing
            }
        }

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(!ttl.isNegative && !ttl.isZero) { "ttl must be positive" }
    }

    fun get(eventId: String, text: String): CacheLookupResult = synchronized(lock) {
        pruneExpired(clock.instant())
        val entry = cache[eventId] ?: return CacheLookupResult.Miss
        return if (entry.fingerprint == fingerprint(text)) {
            CacheLookupResult.Hit(entry.result)
        } else {
            CacheLookupResult.Conflict
        }
    }

    fun put(eventId: String, text: String, result: AnalysisResult): Unit = synchronized(lock) {
        val now = clock.instant()
        pruneExpired(now)
        cache.remove(eventId)
        cache[eventId] = CachedEntry(
            fingerprint = fingerprint(text),
            result = result,
            expiresAt = now.plus(ttl),
        )
        while (cache.size > maxEntries) {
            val oldest = cache.entries.iterator().next().key
            cache.remove(oldest)
        }
    }

    private fun pruneExpired(now: Instant) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.value.expiresAt.isAfter(now)) {
                iterator.remove()
            }
        }
    }

    private data class CachedEntry(
        val fingerprint: String,
        val result: AnalysisResult,
        val expiresAt: Instant,
    )

    private fun fingerprint(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
