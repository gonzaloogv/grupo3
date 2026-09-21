package com.grupo3.freno.data

import com.grupo3.freno.model.FrenoEvent
import com.grupo3.freno.model.AnalysisStatus
import com.grupo3.freno.model.ExplanationSource
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.model.RiskLevel
import com.grupo3.freno.capture.CapturedNotification
import com.grupo3.freno.capture.NotificationPolicy
import com.grupo3.freno.orchestration.AnalysisRequest
import com.grupo3.freno.orchestration.HttpRiskAnalyzer
import com.grupo3.freno.orchestration.RiskAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Coordinador de eventos conectado al backend real mediante HttpRiskAnalyzer.
 */
object FrenoEventStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analyzer: RiskAnalyzer = HttpRiskAnalyzer()
    private val queue = Channel<CapturedNotification>(capacity = 5)
    private val _events = MutableStateFlow<List<FrenoEvent>>(emptyList())
    val events = _events.asStateFlow()
    private val _activeAlert = MutableStateFlow<FrenoEvent?>(null)
    val activeAlert = _activeAlert.asStateFlow()

    init {
        scope.launch {
            for (notification in queue) process(notification)
        }
    }

    fun trust(id: String) = updateTrust(id, trusted = true)

    fun distrust(id: String) = updateTrust(id, trusted = false)

    fun dismissAlert() {
        _activeAlert.value = null
    }

    /** El callback del listener solamente encola; nunca hace red ni bloquea. */
    fun submit(notification: CapturedNotification) {
        if (!NotificationPolicy.shouldAnalyze(notification)) return
        if (queue.trySend(notification).isFailure) {
            addUnavailable(notification, "Freno estaba ocupado y no pudo analizar este mensaje.")
        }
    }

    fun addBlocked(
        source: String,
        sender: String,
        preview: String,
        reason: String,
    ) {
        val event = FrenoEvent(
            id = UUID.randomUUID().toString(),
            source = source,
            sender = sender.ifBlank { "Remitente desconocido" },
            preview = preview,
            reason = reason,
            whenLabel = "Ahora",
        )
        _events.value = listOf(event) + _events.value
    }

    private fun updateTrust(id: String, trusted: Boolean) {
        _events.value = _events.value.map { event ->
            if (event.id == id) event.copy(trusted = trusted) else event
        }
    }

    private suspend fun process(notification: CapturedNotification) {
        val eventId = UUID.randomUUID().toString()
        val preview = NotificationPolicy.redactedPreview(notification.text)
        val analyzing = FrenoEvent(
            id = eventId,
            source = notification.source,
            sender = notification.sender.ifBlank { "Remitente desconocido" },
            preview = preview,
            reason = "Analizando el mensaje recibido.",
            whenLabel = formatWhen(notification.postedAtMillis),
            analysisStatus = AnalysisStatus.ANALYZING,
            risk = RiskLevel.UNKNOWN,
            category = "UNKNOWN",
            reasonCode = "ANALYZING",
            action = RecommendedAction.NONE,
            explanationSource = ExplanationSource.UNAVAILABLE,
            contentIncomplete = notification.contentIncomplete,
        )
        _events.value = listOf(analyzing) + _events.value
        val result = runCatching {
            analyzer.analyze(
                AnalysisRequest(eventId, notification.source, notification.text, notification.contentIncomplete),
            )
        }.getOrElse {
            unavailableResult("No se pudo completar el análisis. Podés revisar el mensaje con cautela.")
        }
        val completed = analyzing.copy(
            reason = result.reasonSimple,
            analysisStatus = if (result.risk == RiskLevel.UNKNOWN && result.explanationSource == ExplanationSource.UNAVAILABLE) {
                AnalysisStatus.FAILED
            } else {
                AnalysisStatus.COMPLETED
            },
            risk = result.risk,
            category = result.category,
            reasonCode = result.reasonCode,
            action = result.action,
            explanationSource = result.explanationSource,
        )
        _events.value = _events.value.map { if (it.id == eventId) completed else it }
        if (completed.risk == RiskLevel.HIGH && _activeAlert.value == null) {
            _activeAlert.value = completed
        }
    }

    private fun addUnavailable(notification: CapturedNotification, reason: String) {
        val event = FrenoEvent(
            id = UUID.randomUUID().toString(),
            source = notification.source,
            sender = notification.sender.ifBlank { "Remitente desconocido" },
            preview = NotificationPolicy.redactedPreview(notification.text),
            reason = reason,
            whenLabel = formatWhen(notification.postedAtMillis),
            analysisStatus = AnalysisStatus.FAILED,
            risk = RiskLevel.UNKNOWN,
            category = "UNKNOWN",
            reasonCode = "ANALYSIS_UNAVAILABLE",
            action = RecommendedAction.NONE,
            explanationSource = ExplanationSource.UNAVAILABLE,
            contentIncomplete = notification.contentIncomplete,
        )
        _events.value = listOf(event) + _events.value
    }

    private fun unavailableResult(reason: String) = com.grupo3.freno.orchestration.AnalysisResult(
        risk = RiskLevel.UNKNOWN,
        category = "UNKNOWN",
        reasonCode = "ANALYSIS_UNAVAILABLE",
        reasonSimple = reason,
        action = RecommendedAction.NONE,
        explanationSource = ExplanationSource.UNAVAILABLE,
    )

    private fun formatWhen(time: Long): String = DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
        Locale("es", "AR"),
    ).format(Date(time))

    private fun seedEvents() = listOf(
        FrenoEvent(
            id = "blocked-bank",
            source = "SMS",
            sender = "Banco Nación",
            preview = "Su cuenta fue bloqueada. Ingrese aquí para verificar sus datos…",
            reason = "Se hizo pasar por un banco y pidió abrir un enlace.",
            whenLabel = "Hoy · 10:32",
        ),
        FrenoEvent(
            id = "blocked-family",
            source = "WhatsApp",
            sender = "Número desconocido",
            preview = "Hijo, cambié de número. Necesito que transfieras a este CBU…",
            reason = "Dijo ser un familiar, cambió de número y pidió dinero urgente.",
            whenLabel = "Ayer · 18:14",
        ),
        FrenoEvent(
            id = "blocked-package",
            source = "SMS",
            sender = "Correo Express",
            preview = "Tu paquete está retenido. Pagá el envío para liberarlo…",
            reason = "Usó una entrega falsa para pedir un pago.",
            whenLabel = "12 sep. · 09:06",
        ),
        FrenoEvent(
            id = "trusted-neighbor",
            source = "WhatsApp",
            sender = "Marta vecina",
            preview = "Rosa, mañana paso a tomar mate a las cinco.",
            reason = "Marcaste esta notificación como confiable.",
            whenLabel = "11 sep. · 16:40",
            trusted = true,
        ),
    )
}
