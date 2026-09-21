package ar.com.freno.server.api

import ar.com.freno.server.application.RiskAnalyzer
import ar.com.freno.server.config.ServerConfig
import ar.com.freno.server.infrastructure.cache.AnalysisCache
import ar.com.freno.server.infrastructure.cache.CacheLookupResult
import ar.com.freno.server.plugins.DEMO_AUTH_PROVIDER
import ar.com.freno.shared.contract.AnalysisRequest
import ar.com.freno.shared.contract.HealthResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

private const val MAX_BODY_BYTES = 8 * 1024 // 8 KB = 8192 bytes
private const val MAX_TEXT_LENGTH = 2000

fun Application.configureRoutes(
    config: ServerConfig,
    riskAnalyzer: RiskAnalyzer,
    analysisCache: AnalysisCache = AnalysisCache(
        maxEntries = config.analysisCacheMaxEntries,
        ttl = Duration.ofMinutes(config.analysisCacheTtlMinutes),
    ),
) {
    val json = Json { ignoreUnknownKeys = false }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    timeZone = config.timeZone,
                ),
            )
        }

        authenticate(DEMO_AUTH_PROVIDER) {
            post("/v1/analyze") {
                val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                    return@post
                }

                val bodyBytes = call.receiveChannel().readRemaining(MAX_BODY_BYTES.toLong() + 1).readByteArray()
                if (bodyBytes.size > MAX_BODY_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                    return@post
                }

                val request = try {
                    json.decodeFromString<AnalysisRequest>(bodyBytes.decodeToString())
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                if (request.text.length > MAX_TEXT_LENGTH) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                if (request.urls.size > 3 || request.urls.any { !it.isValidHttpUrl() }) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                when (val lookup = analysisCache.getOrAnalyze(request, riskAnalyzer)) {
                    is CacheLookupResult.Hit -> {
                        call.respond(lookup.result)
                    }
                    is CacheLookupResult.Conflict -> {
                        call.respond(HttpStatusCode.Conflict)
                    }
                    is CacheLookupResult.Miss -> {
                        error("Atomic cache lookup must resolve a miss")
                    }
                }
            }
        }
    }
}

private fun String.isValidHttpUrl(): Boolean =
    try {
        val parsed = URI(this)
        parsed.scheme?.lowercase() in setOf("http", "https") && !parsed.host.isNullOrBlank()
    } catch (_: URISyntaxException) {
        false
    }
