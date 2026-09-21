package ar.com.freno.server

import ar.com.freno.server.api.configureRoutes
import ar.com.freno.server.application.ConservativeRiskAnalyzer
import ar.com.freno.server.application.GeminiRiskAnalyzer
import ar.com.freno.server.application.RiskAnalyzer
import ar.com.freno.server.config.ServerConfig
import ar.com.freno.server.infrastructure.cache.AnalysisCache
import ar.com.freno.server.infrastructure.safe_browsing.SafeBrowsingUrlReputationProvider
import ar.com.freno.server.plugins.configureSecurity
import ar.com.freno.server.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.time.Duration
import java.time.ZoneId
import java.util.TimeZone

fun main(args: Array<String>) {
    val config = ServerConfig.fromDotEnv(java.nio.file.Path.of(args.firstOrNull() ?: ".env"))
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(config.timeZone)))

    val client = HttpClient(CIO) {
        engine {
            requestTimeout = config.geminiTimeoutMillis
        }
    }
    try {
        embeddedServer(
            factory = Netty,
            host = config.host,
            port = config.port,
        ) {
            module(
                config,
                ConservativeRiskAnalyzer(
                    textAnalyzer = GeminiRiskAnalyzer(
                        client = client,
                        apiKey = config.geminiApiKey,
                        model = config.geminiModel,
                        promptVersion = config.promptVersion,
                        timeoutMillis = config.geminiTimeoutMillis,
                    ),
                    urlReputationProvider = SafeBrowsingUrlReputationProvider(
                        client = client,
                        apiKey = config.safeBrowsingApiKey,
                        timeoutMillis = config.safeBrowsingTimeoutMillis,
                        maxUrls = config.safeBrowsingMaxUrls,
                        maxCacheEntries = config.safeBrowsingCacheMaxEntries,
                    ),
                    promptVersion = config.promptVersion,
                ),
            )
        }.start(wait = true)
    } finally {
        client.close()
    }
}

fun Application.module(
    config: ServerConfig,
    riskAnalyzer: RiskAnalyzer,
    analysisCache: AnalysisCache = AnalysisCache(
        maxEntries = config.analysisCacheMaxEntries,
        ttl = Duration.ofMinutes(config.analysisCacheTtlMinutes),
    ),
) {
    configureSerialization()
    configureSecurity(config.demoApiToken)
    configureRoutes(config, riskAnalyzer, analysisCache)
}
