package ar.com.freno.server

import ar.com.freno.server.api.configureRoutes
import ar.com.freno.server.application.FakeRiskAnalyzer
import ar.com.freno.server.application.RiskAnalyzer
import ar.com.freno.server.config.ServerConfig
import ar.com.freno.server.plugins.configureSecurity
import ar.com.freno.server.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.time.ZoneId
import java.util.TimeZone

fun main() {
    val config = ServerConfig.fromEnvironment()
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(config.timeZone)))

    embeddedServer(
        factory = Netty,
        host = config.host,
        port = config.port,
    ) {
        module(config, FakeRiskAnalyzer(config.promptVersion))
    }.start(wait = true)
}

fun Application.module(
    config: ServerConfig,
    riskAnalyzer: RiskAnalyzer,
) {
    configureSerialization()
    configureSecurity(config.demoApiToken)
    configureRoutes(config, riskAnalyzer)
}
