package com.grupo3.freno.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.grupo3.freno.model.FrenoEvent
import com.grupo3.freno.model.AnalysisStatus
import com.grupo3.freno.model.ExplanationSource
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.model.RiskLevel
import com.grupo3.freno.capture.CapturedNotification
import com.grupo3.freno.capture.NotificationPolicy
import com.grupo3.freno.MainActivity
import com.grupo3.freno.platform.FrenoNotificationListener
import com.grupo3.freno.platform.FrenoOverlay
import com.grupo3.freno.platform.FrenoAlertNotifier
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
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Coordinador de eventos conectado al backend real mediante HttpRiskAnalyzer.
 */
object FrenoEventStore {
    private const val TAG = "FrenoEventStore"
    private const val MAX_STORED_EVENTS = 100
    private const val RECENT_KEY_WINDOW_MS = 5 * 60 * 1000L
    private val persistenceLock = Any()
    private var preferences: SharedPreferences? = null
    private var applicationContext: Context? = null
    private val recentKeys = mutableMapOf<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analyzer: RiskAnalyzer = HttpRiskAnalyzer()
    private val queue = Channel<CapturedNotification>(capacity = 5)
    private val _events = MutableStateFlow<List<FrenoEvent>>(emptyList())
    val events = _events.asStateFlow()
    private val _activeAlert = MutableStateFlow<FrenoEvent?>(null)
    val activeAlert = _activeAlert.asStateFlow()

    fun initialize(context: Context) {
        synchronized(persistenceLock) {
            if (preferences != null) return
            val storage = context.getSharedPreferences("freno_events", Context.MODE_PRIVATE)
            preferences = storage
            applicationContext = context.applicationContext
            runCatching {
                val savedKeys = JSONArray(storage.getString("recent_keys", "[]"))
                for (index in 0 until savedKeys.length()) {
                    val item = savedKeys.getJSONObject(index)
                    recentKeys[item.getString("key")] = item.getLong("at")
                }
            }.onFailure { Log.w(TAG, "Could not restore recent notification keys", it) }
            if (_events.value.isEmpty()) {
                _events.value = loadEvents(storage)
            }
        }
    }

    init {
        scope.launch {
            for (notification in queue) process(notification)
        }
    }

    fun trust(id: String) = updateTrust(id, trusted = true)

    fun distrust(id: String) = updateTrust(id, trusted = false)

    fun dismissAlert() {
        _activeAlert.value = null
        FrenoOverlay.dismiss()
        applicationContext?.let(FrenoAlertNotifier::dismiss)
    }

    /** El callback del listener solamente encola; nunca hace red ni bloquea. */
    fun submit(notification: CapturedNotification) {
        if (!NotificationPolicy.shouldAnalyze(notification)) return
        if (!claimNotification(notification)) return
        if (queue.trySend(notification).isFailure) {
            Log.w(TAG, "Notification queue full")
            addUnavailable(notification, "Freno estaba ocupado y no pudo analizar este mensaje.")
        } else {
            Log.i(TAG, "Notification queued source=${notification.source}")
        }
    }

    private fun claimNotification(notification: CapturedNotification): Boolean = synchronized(persistenceLock) {
        val now = System.currentTimeMillis()
        recentKeys.entries.removeAll { now - it.value > RECENT_KEY_WINDOW_MS }
        val identity = "${notification.packageName}|${notification.notificationKey}|" +
            "${notification.postedAtMillis}|${notification.text}"
        val key = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (key in recentKeys) return@synchronized false
        recentKeys[key] = now
        val savedKeys = JSONArray()
        recentKeys.forEach { (savedKey, at) ->
            savedKeys.put(JSONObject().put("key", savedKey).put("at", at))
        }
        preferences?.edit()?.putString("recent_keys", savedKeys.toString())?.apply()
        true
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
        persistEvents()
    }

    private fun updateTrust(id: String, trusted: Boolean) {
        _events.value = _events.value.map { event ->
            if (event.id == id) event.copy(trusted = trusted) else event
        }
        persistEvents()
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
        persistEvents()
        Log.i(TAG, "Analysis started eventId=$eventId")
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
        persistEvents()
        Log.i(TAG, "Analysis finished eventId=$eventId status=${completed.analysisStatus} risk=${completed.risk}")
        if (completed.risk == RiskLevel.HIGH) {
            _activeAlert.value = completed
            if (MainActivity.isForeground) {
                FrenoNotificationListener.dismissCapturedNotification(notification.notificationKey)
            } else {
                applicationContext?.let { context ->
                    FrenoAlertNotifier.show(context, completed)
                    FrenoOverlay.show(context, completed) {
                        FrenoNotificationListener.dismissCapturedNotification(notification.notificationKey)
                    }
                }
            }
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
        persistEvents()
    }

    private fun persistEvents() {
        synchronized(persistenceLock) {
            val storage = preferences ?: return
            val records = JSONArray()
            _events.value.take(MAX_STORED_EVENTS).forEach { event ->
                records.put(JSONObject().apply {
                    put("id", event.id)
                    put("source", event.source)
                    put("sender", event.sender)
                    put("preview", event.preview)
                    put("reason", event.reason)
                    put("whenLabel", event.whenLabel)
                    put("trusted", event.trusted)
                    put("analysisStatus", event.analysisStatus.name)
                    put("risk", event.risk.name)
                    put("category", event.category)
                    put("reasonCode", event.reasonCode)
                    put("action", event.action.name)
                    put("explanationSource", event.explanationSource.name)
                    put("contentIncomplete", event.contentIncomplete)
                })
            }
            storage.edit().putString("history", records.toString()).apply()
        }
    }

    private fun loadEvents(storage: SharedPreferences): List<FrenoEvent> = runCatching {
        val records = JSONArray(storage.getString("history", "[]"))
        (0 until records.length()).map { index ->
            val event = records.getJSONObject(index)
            FrenoEvent(
                id = event.getString("id"),
                source = event.getString("source"),
                sender = event.getString("sender"),
                preview = event.getString("preview"),
                reason = event.getString("reason"),
                whenLabel = event.getString("whenLabel"),
                trusted = event.optBoolean("trusted"),
                analysisStatus = enumValueOf(event.getString("analysisStatus")),
                risk = enumValueOf(event.getString("risk")),
                category = event.getString("category"),
                reasonCode = event.getString("reasonCode"),
                action = enumValueOf(event.getString("action")),
                explanationSource = enumValueOf(event.getString("explanationSource")),
                contentIncomplete = event.optBoolean("contentIncomplete"),
            )
        }
    }.getOrElse {
        Log.w(TAG, "Could not restore saved history", it)
        emptyList()
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
        Locale.forLanguageTag("es-AR"),
    ).format(Date(time))
}
