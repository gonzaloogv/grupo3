package ar.com.freno.server.infrastructure.safe_browsing

import ar.com.freno.server.application.UrlReputationProvider
import ar.com.freno.shared.contract.UrlAssessment
import ar.com.freno.shared.contract.UrlAssessmentProvider
import ar.com.freno.shared.contract.UrlAssessmentStatus
import ar.com.freno.shared.contract.UrlThreatType
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap

class SafeBrowsingUrlReputationProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val timeoutMillis: Long,
    private val clock: Clock = Clock.systemUTC(),
    private val maxUrls: Int = 3,
    private val maxCacheEntries: Int = 100,
) : UrlReputationProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheLock = Any()
    private val positiveCache = LinkedHashMap<String, List<CachedThreat>>()

    init {
        require(apiKey.isNotBlank()) { "SAFE_BROWSING_API_KEY is required" }
        require(timeoutMillis > 0) { "SAFE_BROWSING_TIMEOUT_MS must be positive" }
        require(maxUrls in 1..3) { "SAFE_BROWSING_MAX_URLS must be between 1 and 3" }
        require(maxCacheEntries > 0) { "Safe Browsing cache capacity must be positive" }
    }

    override suspend fun assess(urls: List<String>): UrlAssessment {
        if (urls.isEmpty()) {
            return UrlAssessment(
                status = UrlAssessmentStatus.NO_URL,
                provider = UrlAssessmentProvider.NONE,
                threatTypes = emptyList(),
            )
        }
        require(urls.size <= maxUrls) { "Safe Browsing accepts at most $maxUrls URLs" }
        val requestedUrls = urls.distinct()
        val cachedAtStart = activeCachedByUrl(requestedUrls, clock.instant())
        val urlsToQuery = requestedUrls.filter { url ->
            cachedAtStart[url].isNullOrEmpty()
        }
        if (urlsToQuery.isEmpty()) return matched(threatTypes(cachedAtStart))
        val urlsSkippedBecauseCached = requestedUrls.filterNot(urlsToQuery::contains)

        return try {
            val response = withTimeout(timeoutMillis) {
                client.post("https://safebrowsing.googleapis.com/v4/threatMatches:find") {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody(urlsToQuery).toString())
                }
            }
            require(response.status.value in 200..299) { "Safe Browsing HTTP ${response.status.value}" }
            val responseBody = response.bodyAsText()
            val receivedAt = clock.instant()
            val matches = json.decodeFromString<SafeBrowsingResponse>(responseBody).matches
                .filter { it.threat.url in urlsToQuery }
            val observed = matches.mapNotNull { match ->
                val threatType = match.threatType.toSharedType() ?: return@mapNotNull null
                ObservedThreat(match, threatType)
            }
            val fresh = observed.mapNotNull { observedThreat ->
                val match = observedThreat.match
                val duration = parseDuration(match.cacheDuration) ?: return@mapNotNull null
                CachedThreat(
                    threatType = observedThreat.threatType,
                    expiresAt = receivedAt.plus(duration),
                )
                    .let { match.threat.url to it }
            }
            storeFresh(fresh, receivedAt)
            val cachedAfterResponse = activeCachedByUrl(requestedUrls, clock.instant())
            val skippedCacheExpired = urlsSkippedBecauseCached.any { cachedAfterResponse[it].isNullOrEmpty() }
            val threatTypes = (
                threatTypes(cachedAfterResponse) + observed.map { it.threatType }
                ).distinct()
            if (threatTypes.isEmpty()) {
                if (skippedCacheExpired) unavailable() else noMatch()
            } else {
                matched(threatTypes)
            }
        } catch (error: CancellationException) {
            if (error !is TimeoutCancellationException) throw error
            cachedFallback(requestedUrls)
        } catch (error: Exception) {
            cachedFallback(requestedUrls)
        }
    }

    private fun activeCachedByUrl(
        urls: List<String>,
        now: Instant,
    ): Map<String, List<CachedThreat>> = synchronized(cacheLock) {
        pruneExpired(now)
        urls.mapNotNull { url ->
            positiveCache[cacheKey(url)]?.let { url to it }
        }.toMap()
    }

    private fun pruneExpired(now: Instant) {
        val iterator = positiveCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val active = entry.value.filter { it.expiresAt.isAfter(now) }
            if (active.isEmpty()) iterator.remove() else entry.setValue(active)
        }
    }

    private fun storeFresh(fresh: List<Pair<String, CachedThreat>>, now: Instant) {
        if (fresh.isEmpty()) return
        synchronized(cacheLock) {
            pruneExpired(now)
            fresh.groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .forEach { (url, entries) ->
                    val key = cacheKey(url)
                    val combined = (positiveCache.remove(key).orEmpty() + entries)
                        .groupBy(CachedThreat::threatType)
                        .map { (_, candidates) -> candidates.maxBy(CachedThreat::expiresAt) }
                    positiveCache[key] = combined
                }
            while (positiveCache.size > maxCacheEntries) {
                positiveCache.entries.iterator().run {
                    next()
                    remove()
                }
            }
        }
    }

    private fun cachedFallback(urls: List<String>): UrlAssessment {
        val cachedTypes = threatTypes(activeCachedByUrl(urls, clock.instant()))
        return if (cachedTypes.isEmpty()) unavailable() else matched(cachedTypes)
    }

    private fun threatTypes(cached: Map<String, List<CachedThreat>>) =
        cached.values.flatten().map(CachedThreat::threatType).distinct()

    private fun cacheKey(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun String.toSharedType() = when (this) {
        "SOCIAL_ENGINEERING" -> UrlThreatType.SOCIAL_ENGINEERING
        "MALWARE" -> UrlThreatType.MALWARE
        else -> null
    }

    private fun parseDuration(value: String): Duration? {
        val match = DURATION.matchEntire(value) ?: return null
        val seconds = match.groupValues[1].toLongOrNull() ?: return null
        val fraction = match.groupValues[2].padEnd(9, '0').ifEmpty { "0" }
        val nanos = fraction.toLongOrNull() ?: return null
        return runCatching { Duration.ofSeconds(seconds, nanos) }.getOrNull()
            ?.takeIf { !it.isNegative && !it.isZero }
    }

    private fun matched(threatTypes: List<UrlThreatType>) = UrlAssessment(
        status = UrlAssessmentStatus.MATCH,
        provider = UrlAssessmentProvider.GOOGLE_SAFE_BROWSING,
        threatTypes = threatTypes,
    )

    private fun noMatch() = UrlAssessment(
        status = UrlAssessmentStatus.NO_MATCH,
        provider = UrlAssessmentProvider.GOOGLE_SAFE_BROWSING,
        threatTypes = emptyList(),
    )

    private fun requestBody(urls: List<String>) = buildJsonObject {
        put("client", buildJsonObject {
            put("clientId", "freno")
            put("clientVersion", "1.0")
        })
        put("threatInfo", buildJsonObject {
            put("threatTypes", buildJsonArray {
                add(JsonPrimitive("MALWARE"))
                add(JsonPrimitive("SOCIAL_ENGINEERING"))
            })
            put("platformTypes", buildJsonArray { add(JsonPrimitive("ANY_PLATFORM")) })
            put("threatEntryTypes", buildJsonArray { add(JsonPrimitive("URL")) })
            put("threatEntries", buildJsonArray {
                urls.distinct().forEach { url -> add(buildJsonObject { put("url", url) }) }
            })
        })
    }

    private fun unavailable() = UrlAssessment(
        status = UrlAssessmentStatus.UNAVAILABLE,
        provider = UrlAssessmentProvider.GOOGLE_SAFE_BROWSING,
        threatTypes = emptyList(),
    )

    private companion object {
        val DURATION = Regex("^(\\d+)(?:\\.(\\d{1,9}))?s$")
    }
}

private data class CachedThreat(
    val threatType: UrlThreatType,
    val expiresAt: Instant,
)

private data class ObservedThreat(
    val match: SafeBrowsingMatch,
    val threatType: UrlThreatType,
)

@Serializable
private data class SafeBrowsingResponse(
    val matches: List<SafeBrowsingMatch> = emptyList(),
)

@Serializable
private data class SafeBrowsingMatch(
    val threatType: String,
    val threat: SafeBrowsingThreat,
    val cacheDuration: String,
)

@Serializable
private data class SafeBrowsingThreat(
    val url: String,
)
