package ar.com.freno.server.data

import ar.com.freno.shared.contract.Action
import ar.com.freno.shared.contract.Category
import ar.com.freno.shared.contract.NotificationSource
import ar.com.freno.shared.contract.ReasonCode
import ar.com.freno.shared.contract.Risk
import ar.com.freno.shared.contract.UrlAssessmentProvider
import ar.com.freno.shared.contract.UrlAssessmentStatus
import ar.com.freno.shared.contract.UrlThreatType
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
