package com.grupo3.freno.data

import com.grupo3.freno.model.AnalysisStatus
import com.grupo3.freno.model.ExplanationSource
import com.grupo3.freno.model.FrenoEvent
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecoveryTest {
    @Test
    fun `interrupted analysis is restored honestly without losing message or manual review`() {
        val pending = event().copy(analysisStatus = AnalysisStatus.ANALYZING, trusted = true)

        val restored = pending.recoverInterruptedAnalysis()

        assertEquals(AnalysisStatus.FAILED, restored.analysisStatus)
        assertEquals(RiskLevel.UNKNOWN, restored.risk)
        assertEquals("UNKNOWN", restored.category)
        assertEquals("ANALYSIS_UNAVAILABLE", restored.reasonCode)
        assertEquals(RecommendedAction.NONE, restored.action)
        assertEquals(ExplanationSource.UNAVAILABLE, restored.explanationSource)
        assertTrue(restored.reason.contains("sin analizar"))
        assertEquals(pending.id, restored.id)
        assertEquals(pending.sender, restored.sender)
        assertEquals(pending.preview, restored.preview)
        assertEquals(pending.whenLabel, restored.whenLabel)
        assertTrue(restored.trusted)
    }

    @Test
    fun `completed verdicts survive restart without reclassification`() {
        RiskLevel.entries.forEach { risk ->
            val completed = event().copy(risk = risk)
            assertSame(completed, completed.recoverInterruptedAnalysis())
        }
    }

    @Test
    fun `existing failure keeps its original explanation and recovery is idempotent`() {
        val failed = event().copy(analysisStatus = AnalysisStatus.FAILED, reason = "Servidor sin conexión")
        assertSame(failed, failed.recoverInterruptedAnalysis())

        val recovered = event().copy(analysisStatus = AnalysisStatus.ANALYZING).recoverInterruptedAnalysis()
        assertSame(recovered, recovered.recoverInterruptedAnalysis())
    }

    private fun event() = FrenoEvent(
        id = "saved-event",
        source = "SMS",
        sender = "Contacto de prueba",
        preview = "Mensaje de prueba",
        reason = "Resultado anterior",
        whenLabel = "23/9/26",
    )
}
