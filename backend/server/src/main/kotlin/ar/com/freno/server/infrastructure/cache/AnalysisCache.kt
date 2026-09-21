package ar.com.freno.server.infrastructure.cache

import ar.com.freno.shared.contract.AnalysisResult
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

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(!ttl.isNegative && !ttl.isZero) { "ttl must be positive" }
    }

    fun get(eventId: String, text: String): CacheLookupResult = synchronized(lock) {
        pruneExpired(clock.instant())
        val entry = cache[eventId] ?: return CacheLookupResult.Miss
        return if (entry.text == text) {
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
            text = text,
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
        val text: String,
        val result: AnalysisResult,
        val expiresAt: Instant,
    )
}
