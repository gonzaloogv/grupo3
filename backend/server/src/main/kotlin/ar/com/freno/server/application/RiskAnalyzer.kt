package ar.com.freno.server.application

import ar.com.freno.shared.contract.AnalysisRequest
import ar.com.freno.shared.contract.AnalysisResult

fun interface RiskAnalyzer {
    suspend fun analyze(input: AnalysisRequest): AnalysisResult
}
