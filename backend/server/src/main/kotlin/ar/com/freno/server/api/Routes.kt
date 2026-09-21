package ar.com.freno.server.api

import ar.com.freno.server.application.RiskAnalyzer
import ar.com.freno.server.config.ServerConfig
import ar.com.freno.server.plugins.DEMO_AUTH_PROVIDER
import ar.com.freno.shared.contract.AnalysisRequest
import ar.com.freno.shared.contract.HealthResponse
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.configureRoutes(
    config: ServerConfig,
    riskAnalyzer: RiskAnalyzer,
) {
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
                val request = call.receive<AnalysisRequest>()
                call.respond(riskAnalyzer.analyze(request))
            }
        }
    }
}
