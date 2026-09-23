package com.grupo3.freno.platform

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.grupo3.freno.BuildConfig
import com.grupo3.freno.capture.CapturedNotification
import com.grupo3.freno.data.FrenoEventStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recibe únicamente SMS y WhatsApp. El callback extrae el dato mínimo y retorna:
 * la cola de Freno hace el análisis fuera del hilo que Android asigna al servicio.
 */
class FrenoNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        currentListener = this
        _connected.value = true
        FrenoEventStore.initialize(applicationContext)
        Log.i(TAG, "Listener connected")
        val cutoff = System.currentTimeMillis() - RECENT_NOTIFICATION_WINDOW_MS
        getActiveNotifications()?.forEach { notification ->
            if (notification.postTime >= cutoff) onNotificationPosted(notification)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (currentListener === this) currentListener = null
        _connected.value = false
        Log.w(TAG, "Listener disconnected; requesting rebind")
        requestRebind(android.content.ComponentName(this, FrenoNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val source = sourceFor(sbn.packageName) ?: return
        FrenoEventStore.initialize(applicationContext)
        Log.i(TAG, "Notification received source=$source")
        val notification = sbn.notification
        val extras = notification.extras ?: return
        val text = extractText(extras)
        val incomplete = text.isBlank() || notification.visibility == Notification.VISIBILITY_SECRET
        FrenoEventStore.submit(
            CapturedNotification(
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                source = source,
                sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = text,
                postedAtMillis = sbn.postTime,
                contentIncomplete = incomplete,
            ),
        )
    }

    private fun sourceFor(packageName: String): String? = when (packageName) {
        "com.whatsapp" -> "WhatsApp"
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms" -> "SMS"
        "com.android.shell" -> if (BuildConfig.DEBUG) "SMS" else null
        else -> null
    }

    private fun extractText(extras: android.os.Bundle): String {
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ")
            .orEmpty()
        val candidates = listOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            textLines,
        )
        return candidates
            .firstOrNull { !it.isNullOrBlank() }
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    companion object {
        const val TAG = "FrenoListener"
        const val RECENT_NOTIFICATION_WINDOW_MS = 5 * 60 * 1000L
        private val _connected = MutableStateFlow(false)
        val connected = _connected.asStateFlow()
        @Volatile private var currentListener: FrenoNotificationListener? = null

        fun dismissCapturedNotification(key: String) {
            runCatching { currentListener?.cancelNotification(key) }
                .onFailure { Log.w(TAG, "Could not dismiss high-risk notification", it) }
        }
    }
}
