package ar.com.freno.shared.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalysisContractSerializationTest {
    private val json = Json

    @Test
    fun `request uses the documented wire field names and enum value`() {
        val request = AnalysisRequest(
            eventId = "demo-001",
            source = NotificationSource.SMS,
            text = "Soy tu hijo, cambié de número.",
            contentIncomplete = false,
            urls = listOf("https://example.com/ingresar"),
            locale = "es-AR",
        )

        assertEquals(
            """{"eventId":"demo-001","source":"SMS","text":"Soy tu hijo, cambié de número.","contentIncomplete":false,"urls":["https://example.com/ingresar"],"locale":"es-AR"}""",
            json.encodeToString(request),
        )
    }

    @Test
    fun `documented successful response can be deserialized by consumers`() {
        val result = json.decodeFromString<AnalysisResult>(
            """
            {
              "eventId": "demo-001",
              "risk": "HIGH",
              "category": "FAMILY_IMPERSONATION",
              "reasonCode": "NEW_NUMBER_AND_URGENT_PAYMENT",
              "reasonSimple": "El mensaje dice que tu familiar cambió de número y pide dinero urgente.",
              "action": "VERIFY_KNOWN_CONTACT",
              "analyzer": "GEMINI",
              "model": "gemini-3.5-flash-lite",
              "promptVersion": "freno-v1",
              "explanationSource": "GEMINI",
              "decisionSources": ["GEMINI"],
              "urlAssessment": {"status":"NO_URL","provider":"NONE","threatTypes":[]}
            }
            """.trimIndent(),
        )

        assertEquals("demo-001", result.eventId)
        assertEquals(Risk.HIGH, result.risk)
        assertEquals(Category.FAMILY_IMPERSONATION, result.category)
        assertEquals(ReasonCode.NEW_NUMBER_AND_URGENT_PAYMENT, result.reasonCode)
        assertEquals(Action.VERIFY_KNOWN_CONTACT, result.action)
        assertEquals(Analyzer.GEMINI, result.analyzer)
        assertEquals("gemini-3.5-flash-lite", result.model)
        assertEquals(ExplanationSource.GEMINI, result.explanationSource)
        assertEquals(listOf(DecisionSource.GEMINI), result.decisionSources)
        assertEquals(UrlAssessmentStatus.NO_URL, result.urlAssessment.status)
        assertEquals(UrlAssessmentProvider.NONE, result.urlAssessment.provider)
    }

    @Test
    fun `documented unavailable response keeps unknown separate from low risk`() {
        val result = json.decodeFromString<AnalysisResult>(
            """
            {
              "eventId": "demo-error",
              "risk": "UNKNOWN",
              "category": "UNKNOWN",
              "reasonCode": "ANALYSIS_UNAVAILABLE",
              "reasonSimple": "No se pudo analizar el mensaje en este momento.",
              "action": "NONE",
              "analyzer": "UNAVAILABLE",
              "model": null,
              "promptVersion": "freno-v1",
              "explanationSource": "UNAVAILABLE",
              "decisionSources": ["LOCAL_POLICY"],
              "urlAssessment": {"status":"NO_URL","provider":"NONE","threatTypes":[]}
            }
            """.trimIndent(),
        )

        assertEquals(Risk.UNKNOWN, result.risk)
        assertEquals(Category.UNKNOWN, result.category)
        assertEquals(ReasonCode.ANALYSIS_UNAVAILABLE, result.reasonCode)
        assertEquals(Action.NONE, result.action)
        assertEquals(Analyzer.UNAVAILABLE, result.analyzer)
        assertNull(result.model)
        assertEquals(ExplanationSource.UNAVAILABLE, result.explanationSource)
        assertEquals(listOf(DecisionSource.LOCAL_POLICY), result.decisionSources)
    }

    @Test
    fun `listed URL can be represented with its own category and Google provenance`() {
        val result = json.decodeFromString<AnalysisResult>(
            """
            {
              "eventId": "demo-url",
              "risk": "HIGH",
              "category": "URL_THREAT",
              "reasonCode": "URL_LISTED_AS_THREAT",
              "reasonSimple": "Google reporta este enlace como potencialmente peligroso; no lo abras.",
              "action": "AVOID_LINK_AND_VERIFY",
              "analyzer": "UNAVAILABLE",
              "model": null,
              "promptVersion": "freno-v1",
              "explanationSource": "TEMPLATE",
              "decisionSources": ["GOOGLE_SAFE_BROWSING"],
              "urlAssessment": {
                "status": "MATCH",
                "provider": "GOOGLE_SAFE_BROWSING",
                "threatTypes": ["SOCIAL_ENGINEERING"]
              }
            }
            """.trimIndent(),
        )

        assertEquals(Category.URL_THREAT, result.category)
        assertEquals(ReasonCode.URL_LISTED_AS_THREAT, result.reasonCode)
        assertEquals(UrlAssessmentStatus.MATCH, result.urlAssessment.status)
        assertEquals(listOf(UrlThreatType.SOCIAL_ENGINEERING), result.urlAssessment.threatTypes)
        assertEquals(listOf(DecisionSource.GOOGLE_SAFE_BROWSING), result.decisionSources)
    }
}
