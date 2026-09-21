package com.grupo3.freno.orchestration

import com.grupo3.freno.model.ExplanationSource
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.model.RiskLevel

data class AnalysisRequest(
    val eventId: String,
    val source: String,
    val text: String,
    val contentIncomplete: Boolean,
)

data class AnalysisResult(
    val risk: RiskLevel,
    val category: String,
    val reasonCode: String,
    val reasonSimple: String,
    val action: RecommendedAction,
    val explanationSource: ExplanationSource,
)

interface RiskAnalyzer {
    suspend fun analyze(request: AnalysisRequest): AnalysisResult
}
