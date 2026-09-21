package ar.com.freno.server.application

import ar.com.freno.shared.contract.UrlAssessment

fun interface UrlReputationProvider {
    suspend fun assess(urls: List<String>): UrlAssessment
}
