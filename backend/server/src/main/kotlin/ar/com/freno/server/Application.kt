package ar.com.freno.server

import ar.com.freno.server.api.configureRoutes
import ar.com.freno.server.application.GeminiRiskAnalyzer
import ar.com.freno.server.application.RiskAnalyzer
import ar.com.freno.server.config.ServerConfig
import ar.com.freno.server.plugins.configureSecurity
import ar.com.freno.server.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.time.ZoneId
import java.util.TimeZone

fun main() {
    val config = ServerConfig.fromDotEnv()
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
            module(config, GeminiRiskAnalyzer(
                client = client,
                apiKey = config.geminiApiKey,
                model = config.geminiModel,
                promptVersion = config.promptVersion,
                timeoutMillis = config.geminiTimeoutMillis,
            ))
        }.start(wait = true)
    } finally {
        client.close()
    }
}

fun Application.module(
    config: ServerConfig,
    riskAnalyzer: RiskAnalyzer,
) {
    configureSerialization()
    configureSecurity(config.demoApiToken)
    configureRoutes(config, riskAnalyzer)
}
