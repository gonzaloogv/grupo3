package com.grupo3.freno.orchestration

import com.grupo3.freno.model.ExplanationSource
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.model.RiskLevel

/** Temporal: mantiene el mismo contrato que el cliente HTTP de A-06. */
class FakeRiskAnalyzer : RiskAnalyzer {
    override suspend fun analyze(request: AnalysisRequest): AnalysisResult {
        val text = request.text.lowercase()
        if (request.contentIncomplete || text.length < 8) {
            return unknown("Freno no recibió texto suficiente para evaluar este mensaje.")
        }
        if (listOf("código", "codigo", "clave", "token").any(text::contains) &&
            listOf("pasame", "envia", "enviá", "compart").any(text::contains)
        ) {
            return AnalysisResult(
                risk = RiskLevel.HIGH,
                category = "CODE_REQUEST",
                reasonCode = "CODE_SHARING_REQUEST",
                reasonSimple = "Pidió que compartieras un código o clave de acceso.",
                action = RecommendedAction.DO_NOT_SHARE_CODE,
                explanationSource = ExplanationSource.TEMPLATE,
            )
        }
        if (listOf("cambié de número", "cambie de numero", "soy tu hijo", "transfer").any(text::contains) &&
            listOf("urgente", "transfer", "plata", "dinero").any(text::contains)
        ) {
            return AnalysisResult(
                risk = RiskLevel.HIGH,
                category = "FAMILY_IMPERSONATION",
                reasonCode = "NEW_NUMBER_AND_URGENT_PAYMENT",
                reasonSimple = "Dice ser un familiar, cambió de número y pide dinero urgente.",
                action = RecommendedAction.VERIFY_KNOWN_CONTACT,
                explanationSource = ExplanationSource.TEMPLATE,
            )
        }
        if (listOf("banco", "cuenta", "verificar", "suspendida").any(text::contains) &&
            listOf("http", "link", "enlace", "clave").any(text::contains)
        ) {
            return AnalysisResult(
                risk = RiskLevel.HIGH,
                category = "BANK_PHISHING",
                reasonCode = "CREDENTIAL_REQUEST",
                reasonSimple = "Usa una cuenta o banco para pedir datos mediante un enlace.",
                action = RecommendedAction.AVOID_LINK_AND_VERIFY,
                explanationSource = ExplanationSource.TEMPLATE,
            )
        }
        return AnalysisResult(
            risk = RiskLevel.LOW,
            category = "NONE",
            reasonCode = "NO_CLEAR_SIGNAL",
            reasonSimple = "No se detectaron señales claras en el texto disponible.",
            action = RecommendedAction.NONE,
            explanationSource = ExplanationSource.TEMPLATE,
        )
    }

    private fun unknown(reason: String) = AnalysisResult(
        risk = RiskLevel.UNKNOWN,
        category = "UNKNOWN",
        reasonCode = "INSUFFICIENT_CONTEXT",
        reasonSimple = reason,
        action = RecommendedAction.NONE,
        explanationSource = ExplanationSource.UNAVAILABLE,
    )
}
