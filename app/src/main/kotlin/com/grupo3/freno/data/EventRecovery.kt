package com.grupo3.freno.data

import com.grupo3.freno.model.AnalysisStatus
import com.grupo3.freno.model.ExplanationSource
import com.grupo3.freno.model.FrenoEvent
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.model.RiskLevel

internal fun FrenoEvent.recoverInterruptedAnalysis(): FrenoEvent =
    if (analysisStatus == AnalysisStatus.ANALYZING) {
        copy(
            analysisStatus = AnalysisStatus.FAILED,
            risk = RiskLevel.UNKNOWN,
            category = "UNKNOWN",
            reasonCode = "ANALYSIS_UNAVAILABLE",
            reason = "El análisis se interrumpió al cerrarse la app. Este mensaje quedó sin analizar.",
            action = RecommendedAction.NONE,
            explanationSource = ExplanationSource.UNAVAILABLE,
        )
    } else {
        this
    }
