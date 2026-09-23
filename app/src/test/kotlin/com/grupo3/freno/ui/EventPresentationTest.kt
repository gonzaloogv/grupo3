package com.grupo3.freno.ui

import com.grupo3.freno.model.AnalysisStatus
import com.grupo3.freno.model.FrenoEvent
import com.grupo3.freno.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPresentationTest {
    @Test
    fun `a healthy message shows no clear signals without requiring manual trust`() {
        val message = event(RiskLevel.LOW)

        assertEquals("Sin señales claras", message.analysisPresentation.label)
        assertFalse(message.analysisPresentation.requiresAttention)
        assertFalse(message.trusted)
    }

    @Test
    fun `a dangerous link shows high risk`() {
        val message = event(RiskLevel.HIGH).copy(reasonCode = "URL_LISTED_AS_THREAT")

        assertEquals("Riesgo alto", message.analysisPresentation.label)
        assertTrue(message.analysisPresentation.requiresAttention)
    }

    @Test
    fun `an uncertain result asks for review`() {
        assertEquals("Revisar", event(RiskLevel.REVIEW).analysisPresentation.label)
        assertTrue(event(RiskLevel.REVIEW).analysisPresentation.requiresAttention)
    }

    @Test
    fun `pending analysis never presents a risk verdict`() {
        RiskLevel.entries.forEach { risk ->
            val message = event(risk).copy(analysisStatus = AnalysisStatus.ANALYZING)

            assertEquals("Analizando", message.analysisPresentation.label)
            assertFalse(message.analysisPresentation.requiresAttention)
        }
    }

    @Test
    fun `failed analysis never presents a risk verdict`() {
        RiskLevel.entries.forEach { risk ->
            val message = event(risk).copy(analysisStatus = AnalysisStatus.FAILED)

            assertEquals("Sin analizar", message.analysisPresentation.label)
            assertFalse(message.analysisPresentation.requiresAttention)
        }
    }

    @Test
    fun `unknown risk is not treated as a safe or dangerous result`() {
        assertEquals("Sin analizar", event(RiskLevel.UNKNOWN).analysisPresentation.label)
        assertFalse(event(RiskLevel.UNKNOWN).analysisPresentation.requiresAttention)
    }

    @Test
    fun `manual trust does not replace the analysis verdict`() {
        RiskLevel.entries.forEach { risk ->
            val message = event(risk)

            assertEquals(message.analysisPresentation, message.copy(trusted = true).analysisPresentation)
        }
    }

    @Test
    fun `history includes healthy risky pending failed and manually trusted messages`() {
        val messages = listOf(
            event(RiskLevel.LOW),
            event(RiskLevel.HIGH),
            event(RiskLevel.REVIEW),
            event(RiskLevel.UNKNOWN).copy(analysisStatus = AnalysisStatus.ANALYZING),
            event(RiskLevel.UNKNOWN).copy(analysisStatus = AnalysisStatus.FAILED),
            event(RiskLevel.HIGH).copy(trusted = true),
        )

        assertEquals(messages, messages.filter(EventFilter.HISTORY::accepts))
        assertEquals(listOf(messages.last()), messages.filter(EventFilter.TRUSTED::accepts))
        assertEquals(3, messages.count { it.analysisPresentation.requiresAttention })
    }

    @Test
    fun `removing manual trust keeps a healthy message in history without raising its risk`() {
        val message = event(RiskLevel.LOW).copy(trusted = true).copy(trusted = false)

        assertTrue(EventFilter.HISTORY.accepts(message))
        assertFalse(EventFilter.TRUSTED.accepts(message))
        assertEquals("Sin señales claras", message.analysisPresentation.label)
    }

    private fun event(risk: RiskLevel) = FrenoEvent(
        id = "test-$risk",
        source = "SMS",
        sender = "5550301",
        preview = "Mensaje de prueba",
        reason = "Resultado del análisis",
        whenLabel = "Ahora",
        risk = risk,
    )
}
