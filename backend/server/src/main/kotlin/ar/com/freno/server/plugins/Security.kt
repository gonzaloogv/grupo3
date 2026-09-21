package ar.com.freno.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

const val DEMO_AUTH_PROVIDER = "demo-bearer"

fun Application.configureSecurity(expectedToken: String) {
    install(Authentication) {
        bearer(DEMO_AUTH_PROVIDER) {
            realm = "Freno demo API"
            authenticate { credential ->
                if (constantTimeEquals(credential.token, expectedToken)) {
                    UserIdPrincipal("demo-device")
                } else {
                    null
                }
            }
        }
    }
}

private fun constantTimeEquals(candidate: String, expected: String): Boolean =
    MessageDigest.isEqual(
        candidate.toByteArray(StandardCharsets.UTF_8),
        expected.toByteArray(StandardCharsets.UTF_8),
    )
