package com.grupo3.freno.platform

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.grupo3.freno.capture.CapturedNotification
import com.grupo3.freno.data.FrenoEventStore

/**
 * Recibe únicamente SMS y WhatsApp. El callback extrae el dato mínimo y retorna:
 * la cola de Freno hace el análisis fuera del hilo que Android asigna al servicio.
 */
class FrenoNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val source = sourceFor(sbn.packageName) ?: return
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
}
