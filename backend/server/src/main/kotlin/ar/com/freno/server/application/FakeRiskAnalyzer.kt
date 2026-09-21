package ar.com.freno.server.application

import ar.com.freno.shared.contract.Action
import ar.com.freno.shared.contract.AnalysisRequest
import ar.com.freno.shared.contract.AnalysisResult
import ar.com.freno.shared.contract.Analyzer
import ar.com.freno.shared.contract.Category
import ar.com.freno.shared.contract.DecisionSource
import ar.com.freno.shared.contract.ExplanationSource
import ar.com.freno.shared.contract.ReasonCode
import ar.com.freno.shared.contract.Risk
import ar.com.freno.shared.contract.UrlAssessment
import ar.com.freno.shared.contract.UrlAssessmentProvider
import ar.com.freno.shared.contract.UrlAssessmentStatus

class FakeRiskAnalyzer(
    private val promptVersion: String = "freno-v1",
) : RiskAnalyzer {
    override suspend fun analyze(input: AnalysisRequest): AnalysisResult =
        AnalysisResult(
            eventId = input.eventId,
            risk = Risk.HIGH,
            category = Category.FAMILY_IMPERSONATION,
            reasonCode = ReasonCode.NEW_NUMBER_AND_URGENT_PAYMENT,
            reasonSimple = "El mensaje dice que tu familiar cambió de número y pide dinero urgente.",
            action = Action.VERIFY_KNOWN_CONTACT,
            analyzer = Analyzer.FAKE,
            model = null,
            promptVersion = promptVersion,
            explanationSource = ExplanationSource.TEMPLATE,
            decisionSources = listOf(DecisionSource.LOCAL_POLICY),
            urlAssessment = UrlAssessment(
                status = if (input.urls.isEmpty()) UrlAssessmentStatus.NO_URL else UrlAssessmentStatus.UNAVAILABLE,
                provider = UrlAssessmentProvider.NONE,
                threatTypes = emptyList(),
            ),
        )
}
