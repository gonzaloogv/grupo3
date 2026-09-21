package ar.com.freno.server.data

import ar.com.freno.server.application.ConservativeRiskAnalyzer
import ar.com.freno.server.application.RiskAnalyzer
import ar.com.freno.server.application.UrlReputationProvider
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorpusFixtureTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val dataRoot: Path = Path.of(System.getProperty("user.dir"), "..", "data").normalize()

    @Test
    fun `development and reserved partitions contain the planned scenario balance`() {
        val development = corpus("development")
        val reserved = corpus("reserved")

        assertPartitionBalance(development)
        assertPartitionBalance(reserved)
        assertEquals(12, (development + reserved).map(CorpusCase::id).toSet().size)
        assertEquals(
            mapOf(Scenario.DECEPTIVE to 6, Scenario.ROUTINE to 4, Scenario.AMBIGUOUS to 2),
            (development + reserved).groupingBy(CorpusCase::scenario).eachCount(),
        )
        assertEquals(
            mapOf(ThreatFamily.BANK to 2, ThreatFamily.FAMILY to 2, ThreatFamily.CODE to 2),
            (development + reserved).filter { it.scenario == Scenario.DECEPTIVE }
                .groupingBy(CorpusCase::family).eachCount(),
        )
    }

    @Test
    fun `all corpus links are synthetic example domains and at least two messages contain them`() {
        val cases = corpus("development") + corpus("reserved")
        val linked = cases.filter { it.urls.isNotEmpty() }

        assertTrue(linked.size >= 2)
        linked.forEach { case ->
            case.urls.forEach { url ->
                val host = URI(url).host.orEmpty()
                assertTrue(host == "example" || host.endsWith(".example"), "${case.id} uses $host")
                assertTrue(case.text.contains(url), "${case.id} must include its synthetic link in the text")
            }
        }
        assertFalse(cases.any { it.text.contains(Regex("\\b\\d{6,}\\b")) })
    }

    @Test
    fun `reputation fixtures cover all four provider outcomes consistently`() {
        val fixtures = read<List<ReputationFixture>>(dataRoot.resolve("fixtures/url-reputation.json"))

        assertEquals(
            setOf(
                UrlAssessmentStatus.MATCH,
                UrlAssessmentStatus.NO_MATCH,
                UrlAssessmentStatus.UNAVAILABLE,
                UrlAssessmentStatus.NO_URL,
            ),
            fixtures.map { it.status }.toSet(),
        )
        assertEquals(fixtures.size, fixtures.map(ReputationFixture::id).toSet().size)
        fixtures.forEach { fixture ->
            if (fixture.status == UrlAssessmentStatus.NO_URL) {
                assertTrue(fixture.inputUrls.isEmpty())
                assertEquals(UrlAssessmentProvider.NONE, fixture.provider)
            } else {
                assertTrue(fixture.inputUrls.isNotEmpty())
                assertEquals(UrlAssessmentProvider.GOOGLE_SAFE_BROWSING, fixture.provider)
            }
            assertEquals(fixture.status == UrlAssessmentStatus.MATCH, fixture.threatTypes.isNotEmpty())
        }

        val fixtureIds = fixtures.map(ReputationFixture::id).toSet()
        (corpus("development") + corpus("reserved")).forEach { case ->
            assertTrue(case.reputationFixture in fixtureIds, "${case.id} references a missing fixture")
        }
    }

    @Test
    fun `all corpus cases execute through coordinator with simulated reputation provider producing expected results`() = runBlocking {
        val fixtures = read<List<ReputationFixture>>(dataRoot.resolve("fixtures/url-reputation.json"))
            .associateBy(ReputationFixture::id)
        val cases = corpus("development") + corpus("reserved")
        val executedStatuses = mutableSetOf<UrlAssessmentStatus>()

        cases.forEach { case ->
            val fixture = fixtures[case.reputationFixture]
                ?: error("Missing fixture ${case.reputationFixture} for case ${case.id}")

            executedStatuses.add(fixture.status)

            val textResult = baselineTextResult(case)

            val coordinator = ConservativeRiskAnalyzer(
                textAnalyzer = RiskAnalyzer { textResult },
                urlReputationProvider = UrlReputationProvider { urls ->
                    assertEquals(case.urls, urls, "Provider should receive the exact URLs from the request")
                    UrlAssessment(
                        status = fixture.status,
                        provider = fixture.provider,
                        threatTypes = fixture.threatTypes,
                    )
                },
                promptVersion = "freno-v1",
            )

            val request = AnalysisRequest(
                eventId = case.id,
                source = case.source,
                text = case.text,
                contentIncomplete = case.contentIncomplete,
                urls = case.urls,
                locale = case.locale,
            )

            val result = coordinator.analyze(request)

            assertEquals(case.expectedRisk, result.risk, "${case.id}: risk mismatch")
            assertEquals(case.expectedCategory, result.category, "${case.id}: category mismatch")
            assertEquals(case.expectedReasonCode, result.reasonCode, "${case.id}: reasonCode mismatch")
            assertEquals(case.expectedAction, result.action, "${case.id}: action mismatch")
        }

        assertEquals(
            setOf(
                UrlAssessmentStatus.MATCH,
                UrlAssessmentStatus.NO_MATCH,
                UrlAssessmentStatus.UNAVAILABLE,
                UrlAssessmentStatus.NO_URL,
            ),
            executedStatuses,
            "Corpus execution must exercise all four reputation outcomes through the provider",
        )
    }

    private data class BaselineExpectation(
        val risk: Risk,
        val category: Category,
        val reasonCode: ReasonCode,
        val action: Action,
    )

    private fun baselineTextResult(case: CorpusCase): AnalysisResult {
        val expectation = when {
            case.contentIncomplete -> BaselineExpectation(Risk.LOW, Category.NONE, ReasonCode.NO_CLEAR_SIGNAL, Action.NONE)
            case.scenario == Scenario.DECEPTIVE -> when (case.family) {
                ThreatFamily.BANK -> BaselineExpectation(Risk.HIGH, Category.BANK_PHISHING, ReasonCode.CREDENTIAL_REQUEST, Action.AVOID_LINK_AND_VERIFY)
                ThreatFamily.FAMILY -> BaselineExpectation(Risk.HIGH, Category.FAMILY_IMPERSONATION, ReasonCode.NEW_NUMBER_AND_URGENT_PAYMENT, Action.VERIFY_KNOWN_CONTACT)
                ThreatFamily.CODE -> BaselineExpectation(Risk.HIGH, Category.CODE_REQUEST, ReasonCode.CODE_SHARING_REQUEST, Action.DO_NOT_SHARE_CODE)
                ThreatFamily.NONE -> BaselineExpectation(Risk.HIGH, Category.OTHER, ReasonCode.OTHER_SIGNAL, Action.AVOID_LINK_AND_VERIFY)
            }
            case.scenario == Scenario.ROUTINE -> BaselineExpectation(Risk.LOW, Category.NONE, ReasonCode.NO_CLEAR_SIGNAL, Action.NONE)
            case.scenario == Scenario.AMBIGUOUS -> when {
                case.urls.isNotEmpty() -> BaselineExpectation(Risk.LOW, Category.NONE, ReasonCode.NO_CLEAR_SIGNAL, Action.NONE)
                else -> BaselineExpectation(Risk.REVIEW, Category.OTHER, ReasonCode.OTHER_SIGNAL, Action.AVOID_LINK_AND_VERIFY)
            }
            else -> error("Unhandled scenario for ${case.id}")
        }
        return AnalysisResult(
            eventId = case.id,
            risk = expectation.risk,
            category = expectation.category,
            reasonCode = expectation.reasonCode,
            reasonSimple = "Explicacion simulada sin URLs ni palabras prohibidas.",
            action = expectation.action,
            analyzer = Analyzer.GEMINI,
            model = "gemini-3.5-flash-lite",
            promptVersion = "freno-v1",
            explanationSource = ExplanationSource.GEMINI,
            decisionSources = listOf(DecisionSource.GEMINI),
            urlAssessment = UrlAssessment(UrlAssessmentStatus.NO_URL, UrlAssessmentProvider.NONE, emptyList()),
        )
    }

    private fun assertPartitionBalance(cases: List<CorpusCase>) {
        assertEquals(6, cases.size)
        assertEquals(
            mapOf(Scenario.DECEPTIVE to 3, Scenario.ROUTINE to 2, Scenario.AMBIGUOUS to 1),
            cases.groupingBy(CorpusCase::scenario).eachCount(),
        )
        assertEquals(
            setOf(ThreatFamily.BANK, ThreatFamily.FAMILY, ThreatFamily.CODE),
            cases.filter { it.scenario == Scenario.DECEPTIVE }.map(CorpusCase::family).toSet(),
        )
    }

    private fun corpus(partition: String): List<CorpusCase> =
        read(dataRoot.resolve("corpus/$partition/cases.json"))

    private inline fun <reified T> read(path: Path): T {
        assertTrue(Files.isRegularFile(path), "Missing fixture: $path")
        return json.decodeFromString(Files.readString(path))
    }
}

@Serializable
private data class CorpusCase(
    val id: String,
    val scenario: Scenario,
    val family: ThreatFamily,
    val source: NotificationSource,
    val text: String,
    val contentIncomplete: Boolean,
    val urls: List<String>,
    val locale: String,
    val expectedRisk: Risk,
    val expectedCategory: Category,
    val expectedReasonCode: ReasonCode,
    val expectedAction: Action,
    val reputationFixture: String,
)

@Serializable
private enum class Scenario { DECEPTIVE, ROUTINE, AMBIGUOUS }

@Serializable
private enum class ThreatFamily { BANK, FAMILY, CODE, NONE }

@Serializable
private data class ReputationFixture(
    val id: String,
    val inputUrls: List<String>,
    val status: UrlAssessmentStatus,
    val provider: UrlAssessmentProvider,
    val threatTypes: List<UrlThreatType>,
)
