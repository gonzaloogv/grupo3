package ar.com.freno.server.application

import ar.com.freno.shared.contract.Action
import ar.com.freno.shared.contract.AnalysisRequest
import ar.com.freno.shared.contract.AnalysisResult
import ar.com.freno.shared.contract.Analyzer
import ar.com.freno.shared.contract.Category
import ar.com.freno.shared.contract.DecisionSource
import ar.com.freno.shared.contract.ExplanationSource
import ar.com.freno.shared.contract.NotificationSource
import ar.com.freno.shared.contract.ReasonCode
import ar.com.freno.shared.contract.Risk
import ar.com.freno.shared.contract.UrlAssessment
import ar.com.freno.shared.contract.UrlAssessmentProvider
import ar.com.freno.shared.contract.UrlAssessmentStatus
import ar.com.freno.shared.contract.UrlThreatType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ConservativeRiskAnalyzerTest {
    @Test
    fun `Gemini and Safe Browsing start in parallel and a match wins when Gemini fails`() = runBlocking {
        val geminiStarted = CompletableDeferred<Unit>()
        val reputationStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val analyzer = ConservativeRiskAnalyzer(
            textAnalyzer = RiskAnalyzer {
                geminiStarted.complete(Unit)
                release.await()
                unavailableResult(it)
            },
            urlReputationProvider = UrlReputationProvider {
                reputationStarted.complete(Unit)
                release.await()
                assessment(UrlAssessmentStatus.MATCH, listOf(UrlThreatType.SOCIAL_ENGINEERING))
            },
            promptVersion = "freno-v1",
        )

        val result = coroutineScope {
            val pending = async { analyzer.analyze(request(urls = listOf("https://phishing.example/"))) }
            geminiStarted.await()
            reputationStarted.await()
            release.complete(Unit)
            pending.await()
        }

        assertEquals(Risk.HIGH, result.risk)
        assertEquals(Category.URL_THREAT, result.category)
        assertEquals(ReasonCode.URL_LISTED_AS_THREAT, result.reasonCode)
        assertEquals(Action.AVOID_LINK_AND_VERIFY, result.action)
        assertEquals(ExplanationSource.TEMPLATE, result.explanationSource)
        assertEquals(listOf(DecisionSource.GOOGLE_SAFE_BROWSING), result.decisionSources)
        assertEquals(Analyzer.UNAVAILABLE, result.analyzer)
        assertNull(result.model)
        assertEquals(UrlAssessmentStatus.MATCH, result.urlAssessment.status)
    }

    @Test
    fun `no match never lowers Gemini review`() = runBlocking {
        val analyzer = coordinator(
            gemini = validResult(risk = Risk.REVIEW),
            reputation = assessment(UrlAssessmentStatus.NO_MATCH),
        )

        val result = analyzer.analyze(request(urls = listOf("https://ordinary.example/")))

        assertEquals(Risk.REVIEW, result.risk)
        assertEquals(Category.FAMILY_IMPERSONATION, result.category)
        assertEquals(listOf(DecisionSource.GEMINI), result.decisionSources)
        assertEquals(UrlAssessmentStatus.NO_MATCH, result.urlAssessment.status)
    }

    @Test
    fun `unavailable reputation turns Gemini low into unknown`() = runBlocking {
        val analyzer = coordinator(
            gemini = lowResult(),
            reputation = assessment(UrlAssessmentStatus.UNAVAILABLE),
        )

        val result = analyzer.analyze(request(urls = listOf("https://unknown.example/")))

        assertEquals(Risk.UNKNOWN, result.risk)
        assertEquals(Category.UNKNOWN, result.category)
        assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE, result.reasonCode)
        assertEquals(Action.NONE, result.action)
        assertEquals(ExplanationSource.UNAVAILABLE, result.explanationSource)
        assertEquals(listOf(DecisionSource.LOCAL_POLICY), result.decisionSources)
        assertEquals(UrlAssessmentStatus.UNAVAILABLE, result.urlAssessment.status)
    }

    @Test
    fun `unavailable reputation preserves Gemini high`() = runBlocking {
        val analyzer = coordinator(
            gemini = validResult(risk = Risk.HIGH),
            reputation = assessment(UrlAssessmentStatus.UNAVAILABLE),
        )

        val result = analyzer.analyze(request(urls = listOf("https://unknown.example/")))

        assertEquals(Risk.HIGH, result.risk)
        assertEquals(Category.FAMILY_IMPERSONATION, result.category)
        assertEquals(listOf(DecisionSource.GEMINI), result.decisionSources)
        assertEquals(UrlAssessmentStatus.UNAVAILABLE, result.urlAssessment.status)
    }

    @Test
    fun `incomplete content turns Gemini low into local unknown without calling reputation`() = runBlocking {
        var reputationCalls = 0
        val analyzer = ConservativeRiskAnalyzer(
            textAnalyzer = RiskAnalyzer { lowResult() },
            urlReputationProvider = UrlReputationProvider {
                reputationCalls++
                error("Safe Browsing must not be called without URLs")
            },
            promptVersion = "freno-v1",
        )

        val result = analyzer.analyze(request(contentIncomplete = true))

        assertEquals(0, reputationCalls)
        assertEquals(Risk.UNKNOWN, result.risk)
        assertEquals(ReasonCode.INSUFFICIENT_CONTEXT, result.reasonCode)
        assertEquals(ExplanationSource.TEMPLATE, result.explanationSource)
        assertEquals(listOf(DecisionSource.LOCAL_POLICY), result.decisionSources)
        assertEquals(UrlAssessmentStatus.NO_URL, result.urlAssessment.status)
    }

    @Test
    fun `unsafe Gemini explanation is replaced by a local template`() = runBlocking {
        val unsafe = validResult(risk = Risk.HIGH).copy(
            reasonSimple = "Revisá https://malicious.example porque esta explicación deliberadamente supera el máximo de veinticinco palabras y no debe mostrarse nunca como texto confiable generado por el modelo.",
        )
        val analyzer = coordinator(
            gemini = unsafe,
            reputation = assessment(UrlAssessmentStatus.NO_MATCH),
        )

        val result = analyzer.analyze(request(urls = listOf("https://ordinary.example/")))

        assertEquals(Risk.HIGH, result.risk)
        assertEquals(ExplanationSource.TEMPLATE, result.explanationSource)
        assertEquals(listOf(DecisionSource.GEMINI, DecisionSource.LOCAL_POLICY), result.decisionSources)
        assertFalse(result.reasonSimple.contains("http"))
    }

    @Test
    fun `incompatible Gemini high with no action degrades without intervention`() = runBlocking {
        val invalid = validResult(risk = Risk.HIGH).copy(action = Action.NONE)
        val analyzer = coordinator(
            gemini = invalid,
            reputation = assessment(UrlAssessmentStatus.NO_MATCH),
        )

        val result = analyzer.analyze(request(urls = listOf("https://ordinary.example/")))

        assertEquals(Risk.UNKNOWN, result.risk)
        assertEquals(Category.UNKNOWN, result.category)
        assertEquals(Action.NONE, result.action)
        assertEquals(Analyzer.UNAVAILABLE, result.analyzer)
        assertEquals(ExplanationSource.UNAVAILABLE, result.explanationSource)
    }

    @Test
    fun `incompatible Gemini category and reason degrade without intervention`() = runBlocking {
        val invalid = validResult(risk = Risk.HIGH).copy(
            category = Category.BANK_PHISHING,
            reasonCode = ReasonCode.NEW_NUMBER_AND_URGENT_PAYMENT,
            action = Action.AVOID_LINK_AND_VERIFY,
        )
        val analyzer = coordinator(
            gemini = invalid,
            reputation = assessment(UrlAssessmentStatus.NO_MATCH),
        )

        val result = analyzer.analyze(request(urls = listOf("https://ordinary.example/")))

        assertEquals(Risk.UNKNOWN, result.risk)
        assertEquals(Action.NONE, result.action)
        assertEquals(Analyzer.UNAVAILABLE, result.analyzer)
    }

    @Test
    fun `unexpected reputation failure is unavailable and never low`() = runBlocking {
        val analyzer = ConservativeRiskAnalyzer(
            textAnalyzer = RiskAnalyzer { lowResult() },
            urlReputationProvider = UrlReputationProvider { error("provider failure") },
            promptVersion = "freno-v1",
        )

        val result = analyzer.analyze(request(urls = listOf("https://unknown.example/")))

        assertEquals(Risk.UNKNOWN, result.risk)
        assertEquals(UrlAssessmentStatus.UNAVAILABLE, result.urlAssessment.status)
        assertEquals(UrlAssessmentProvider.GOOGLE_SAFE_BROWSING, result.urlAssessment.provider)
    }

    private fun coordinator(
        gemini: AnalysisResult,
        reputation: UrlAssessment,
    ) = ConservativeRiskAnalyzer(
        textAnalyzer = RiskAnalyzer { gemini },
        urlReputationProvider = UrlReputationProvider { reputation },
        promptVersion = "freno-v1",
    )

    private fun request(
        urls: List<String> = emptyList(),
        contentIncomplete: Boolean = false,
    ) = AnalysisRequest(
        eventId = "event-123",
        source = NotificationSource.SMS,
        text = "Soy tu hijo, cambié de número. Transferime urgente.",
        contentIncomplete = contentIncomplete,
        urls = urls,
        locale = "es-AR",
    )

    private fun validResult(risk: Risk) = AnalysisResult(
        eventId = "event-123",
        risk = risk,
        category = Category.FAMILY_IMPERSONATION,
        reasonCode = if (risk == Risk.REVIEW) ReasonCode.INSUFFICIENT_CONTEXT else ReasonCode.NEW_NUMBER_AND_URGENT_PAYMENT,
        reasonSimple = "El mensaje combina un cambio de número con un pedido urgente de dinero.",
        action = Action.VERIFY_KNOWN_CONTACT,
        analyzer = Analyzer.GEMINI,
        model = "gemini-3.5-flash-lite",
        promptVersion = "freno-v1",
        explanationSource = ExplanationSource.GEMINI,
        decisionSources = listOf(DecisionSource.GEMINI),
        urlAssessment = assessment(UrlAssessmentStatus.NO_URL),
    )

    private fun lowResult() = AnalysisResult(
        eventId = "event-123",
        risk = Risk.LOW,
        category = Category.NONE,
        reasonCode = ReasonCode.NO_CLEAR_SIGNAL,
        reasonSimple = "No se observan señales claras en el texto disponible.",
        action = Action.NONE,
        analyzer = Analyzer.GEMINI,
        model = "gemini-3.5-flash-lite",
        promptVersion = "freno-v1",
        explanationSource = ExplanationSource.GEMINI,
        decisionSources = listOf(DecisionSource.GEMINI),
        urlAssessment = assessment(UrlAssessmentStatus.NO_URL),
    )

    private fun unavailableResult(input: AnalysisRequest) = AnalysisResult(
        eventId = input.eventId,
        risk = Risk.UNKNOWN,
        category = Category.UNKNOWN,
        reasonCode = ReasonCode.ANALYSIS_UNAVAILABLE,
        reasonSimple = "No se pudo analizar el mensaje en este momento.",
        action = Action.NONE,
        analyzer = Analyzer.UNAVAILABLE,
        model = null,
        promptVersion = "freno-v1",
        explanationSource = ExplanationSource.UNAVAILABLE,
        decisionSources = listOf(DecisionSource.LOCAL_POLICY),
        urlAssessment = assessment(UrlAssessmentStatus.NO_URL),
    )

    private fun assessment(
        status: UrlAssessmentStatus,
        threatTypes: List<UrlThreatType> = emptyList(),
    ) = UrlAssessment(
        status = status,
        provider = if (status == UrlAssessmentStatus.NO_URL) {
            UrlAssessmentProvider.NONE
        } else {
            UrlAssessmentProvider.GOOGLE_SAFE_BROWSING
        },
        threatTypes = threatTypes,
    )
}
