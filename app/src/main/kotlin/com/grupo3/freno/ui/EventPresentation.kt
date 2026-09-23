package com.grupo3.freno.ui

import com.grupo3.freno.model.AnalysisStatus
import com.grupo3.freno.model.FrenoEvent
import com.grupo3.freno.model.RiskLevel

internal enum class EventAnalysis(val label: String, val requiresAttention: Boolean = false) {
    ANALYZING("Analizando"),
    UNAVAILABLE("Sin analizar"),
    HIGH("Riesgo alto", requiresAttention = true),
    REVIEW("Revisar", requiresAttention = true),
    LOW("Sin señales claras"),
}

internal val FrenoEvent.analysisPresentation: EventAnalysis
    get() = when (analysisStatus) {
        AnalysisStatus.ANALYZING -> EventAnalysis.ANALYZING
        AnalysisStatus.FAILED -> EventAnalysis.UNAVAILABLE
        AnalysisStatus.COMPLETED -> when (risk) {
            RiskLevel.HIGH -> EventAnalysis.HIGH
            RiskLevel.REVIEW -> EventAnalysis.REVIEW
            RiskLevel.LOW -> EventAnalysis.LOW
            RiskLevel.UNKNOWN -> EventAnalysis.UNAVAILABLE
        }
    }

internal enum class EventFilter {
    HISTORY, TRUSTED;

    fun accepts(event: FrenoEvent): Boolean = this == HISTORY || event.trusted
}
