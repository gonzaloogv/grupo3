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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class ConservativeRiskAnalyzer(
    private val textAnalyzer: RiskAnalyzer,
    private val urlReputationProvider: UrlReputationProvider,
    private val promptVersion: String,
) : RiskAnalyzer {
    init {
        require(promptVersion.isNotBlank()) { "PROMPT_VERSION is required" }
    }

    override suspend fun analyze(input: AnalysisRequest): AnalysisResult = supervisorScope {
        val textDeferred = async { analyzeTextSafely(input) }
        val reputationDeferred = if (input.urls.isEmpty()) {
            null
        } else {
            async { assessUrlsSafely(input.urls) }
        }

        val textResult = textDeferred.await()
        val urlAssessment = reputationDeferred?.await() ?: noUrlAssessment()
        fuse(input, textResult, urlAssessment)
    }

    private suspend fun analyzeTextSafely(input: AnalysisRequest): AnalysisResult =
        try {
            textAnalyzer.analyze(input)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            unavailable(input, noUrlAssessment())
        }

    private suspend fun assessUrlsSafely(urls: List<String>): UrlAssessment =
        try {
            urlReputationProvider.assess(urls)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            unavailableAssessment()
        }

    private fun fuse(
        input: AnalysisRequest,
        textResult: AnalysisResult,
        urlAssessment: UrlAssessment,
    ): AnalysisResult {
        if (urlAssessment.status == UrlAssessmentStatus.MATCH && urlAssessment.threatTypes.isNotEmpty()) {
            return urlThreat(input, textResult, urlAssessment)
        }

        val normalizedText = normalizeText(input, textResult, urlAssessment)
        if (normalizedText.risk != Risk.LOW) return normalizedText

        return when {
            urlAssessment.status == UrlAssessmentStatus.UNAVAILABLE ->
                reputationUnavailable(input, normalizedText, urlAssessment)
            input.contentIncomplete -> incompleteContent(input, normalizedText, urlAssessment)
            else -> normalizedText
        }
    }

    private fun normalizeText(
        input: AnalysisRequest,
        textResult: AnalysisResult,
        urlAssessment: UrlAssessment,
    ): AnalysisResult {
        if (!textResult.hasCompatibleFields()) return unavailable(input, urlAssessment)

        val base = textResult.copy(
            eventId = input.eventId,
            urlAssessment = urlAssessment,
        )
        if (base.explanationSource == ExplanationSource.UNAVAILABLE ||
            ExplanationTemplates.isSafe(base.reasonSimple)) {
            return base
        }
        return base.copy(
            reasonSimple = ExplanationTemplates.forReasonCode(base.reasonCode),
            explanationSource = ExplanationSource.TEMPLATE,
            decisionSources = (base.decisionSources + DecisionSource.LOCAL_POLICY).distinct(),
        )
    }

    private fun AnalysisResult.hasCompatibleFields(): Boolean = when (risk) {
        Risk.HIGH -> category !in setOf(Category.NONE, Category.UNKNOWN) &&
            reasonCode !in setOf(ReasonCode.NO_CLEAR_SIGNAL, ReasonCode.INSUFFICIENT_CONTEXT) &&
            action != Action.NONE && categoryReasonAndActionAreCompatible()
        Risk.REVIEW -> category !in setOf(Category.NONE, Category.UNKNOWN) &&
            reasonCode != ReasonCode.NO_CLEAR_SIGNAL && action != Action.NONE &&
            categoryReasonAndActionAreCompatible()
        Risk.LOW -> category == Category.NONE && reasonCode == ReasonCode.NO_CLEAR_SIGNAL && action == Action.NONE
        Risk.UNKNOWN -> category == Category.UNKNOWN &&
            reasonCode in setOf(ReasonCode.INSUFFICIENT_CONTEXT, ReasonCode.ANALYSIS_UNAVAILABLE) &&
            action == Action.NONE
    }

    private fun AnalysisResult.categoryReasonAndActionAreCompatible(): Boolean = when (category) {
        Category.FAMILY_IMPERSONATION -> reasonCode in setOf(
            ReasonCode.NEW_NUMBER_AND_URGENT_PAYMENT,
            ReasonCode.INSUFFICIENT_CONTEXT,
        ) && action == Action.VERIFY_KNOWN_CONTACT
        Category.BANK_PHISHING -> reasonCode in setOf(
            ReasonCode.CREDENTIAL_REQUEST,
            ReasonCode.INSUFFICIENT_CONTEXT,
        ) && action == Action.AVOID_LINK_AND_VERIFY
        Category.CODE_REQUEST -> reasonCode in setOf(
            ReasonCode.CODE_SHARING_REQUEST,
            ReasonCode.INSUFFICIENT_CONTEXT,
        ) && action == Action.DO_NOT_SHARE_CODE
        Category.OTHER -> reasonCode in setOf(
            ReasonCode.OTHER_SIGNAL,
            ReasonCode.INSUFFICIENT_CONTEXT,
        ) && action != Action.NONE
        else -> false
    }

    private fun urlThreat(
        input: AnalysisRequest,
        textResult: AnalysisResult,
        urlAssessment: UrlAssessment,
    ) = AnalysisResult(
        eventId = input.eventId,
        risk = Risk.HIGH,
        category = Category.URL_THREAT,
        reasonCode = ReasonCode.URL_LISTED_AS_THREAT,
        reasonSimple = ExplanationTemplates.URL_THREAT,
        action = Action.AVOID_LINK_AND_VERIFY,
        analyzer = textResult.analyzer,
        model = textResult.model.takeIf { textResult.analyzer == Analyzer.GEMINI },
        promptVersion = textResult.promptVersion.ifBlank { promptVersion },
        explanationSource = ExplanationSource.TEMPLATE,
        decisionSources = listOf(DecisionSource.GOOGLE_SAFE_BROWSING),
        urlAssessment = urlAssessment,
    )

    private fun reputationUnavailable(
        input: AnalysisRequest,
        textResult: AnalysisResult,
        urlAssessment: UrlAssessment,
    ) = AnalysisResult(
        eventId = input.eventId,
        risk = Risk.UNKNOWN,
        category = Category.UNKNOWN,
        reasonCode = ReasonCode.ANALYSIS_UNAVAILABLE,
        reasonSimple = "No se pudo consultar la reputación del enlace en este momento.",
        action = Action.NONE,
        analyzer = textResult.analyzer,
        model = textResult.model.takeIf { textResult.analyzer == Analyzer.GEMINI },
        promptVersion = textResult.promptVersion.ifBlank { promptVersion },
        explanationSource = ExplanationSource.UNAVAILABLE,
        decisionSources = listOf(DecisionSource.LOCAL_POLICY),
        urlAssessment = urlAssessment,
    )

    private fun incompleteContent(
        input: AnalysisRequest,
        textResult: AnalysisResult,
        urlAssessment: UrlAssessment,
    ) = AnalysisResult(
        eventId = input.eventId,
        risk = Risk.UNKNOWN,
        category = Category.UNKNOWN,
        reasonCode = ReasonCode.INSUFFICIENT_CONTEXT,
        reasonSimple = "El contenido recibido está incompleto y no permite una evaluación confiable.",
        action = Action.NONE,
        analyzer = textResult.analyzer,
        model = textResult.model.takeIf { textResult.analyzer == Analyzer.GEMINI },
        promptVersion = textResult.promptVersion.ifBlank { promptVersion },
        explanationSource = ExplanationSource.TEMPLATE,
        decisionSources = listOf(DecisionSource.LOCAL_POLICY),
        urlAssessment = urlAssessment,
    )

    private fun unavailable(
        input: AnalysisRequest,
        urlAssessment: UrlAssessment,
    ) = AnalysisResult(
        eventId = input.eventId,
        risk = Risk.UNKNOWN,
        category = Category.UNKNOWN,
        reasonCode = ReasonCode.ANALYSIS_UNAVAILABLE,
        reasonSimple = "No se pudo analizar el mensaje en este momento.",
        action = Action.NONE,
        analyzer = Analyzer.UNAVAILABLE,
        model = null,
        promptVersion = promptVersion,
        explanationSource = ExplanationSource.UNAVAILABLE,
        decisionSources = listOf(DecisionSource.LOCAL_POLICY),
        urlAssessment = urlAssessment,
    )

    private fun noUrlAssessment() = UrlAssessment(
        status = UrlAssessmentStatus.NO_URL,
        provider = UrlAssessmentProvider.NONE,
        threatTypes = emptyList(),
    )

    private fun unavailableAssessment() = UrlAssessment(
        status = UrlAssessmentStatus.UNAVAILABLE,
        provider = UrlAssessmentProvider.GOOGLE_SAFE_BROWSING,
        threatTypes = emptyList(),
    )
}
