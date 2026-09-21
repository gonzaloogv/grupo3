package ar.com.freno.shared.contract

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val timeZone: String,
)
