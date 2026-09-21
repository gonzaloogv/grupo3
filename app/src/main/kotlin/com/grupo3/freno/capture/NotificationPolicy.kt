package com.grupo3.freno.capture

import java.security.MessageDigest

object NotificationPolicy {
    private const val duplicateWindowMillis = 60_000L
    private val recent = mutableMapOf<String, Long>()

    fun shouldAnalyze(notification: CapturedNotification, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val normalized = notification.text.normalized()
        if (normalized.isBlank()) return true
        val duplicateKey = "${notification.packageName}|${notification.notificationKey}|${normalized.sha256()}"
        synchronized(recent) {
            recent.entries.removeAll { nowMillis - it.value > duplicateWindowMillis }
            if (recent[duplicateKey]?.let { nowMillis - it <= duplicateWindowMillis } == true) return false
            recent[duplicateKey] = nowMillis
        }
        return true
    }

    fun redactedPreview(text: String, maxLength: Int = 300): String {
        val redacted = text
            .replace(Regex("(?<!\\d)\\d{6}(?!\\d)"), "[CÓDIGO]")
            .replace(Regex("(?<!\\d)\\d{16,22}(?!\\d)"), "[CUENTA]")
            .replace(Regex("(?<!\\d)(?:\\+?54)?(?:9)?\\d{10,12}(?!\\d)"), "[TELÉFONO]")
            .replace(Regex("https?://([^/\\s?]+)[^\\s]*", RegexOption.IGNORE_CASE)) { match ->
                "[ENLACE:${match.groupValues[1]}]"
            }
            .trim()
        return if (redacted.length <= maxLength) redacted else redacted.take(maxLength).trimEnd() + "…"
    }

    private fun String.normalized() = trim().replace(Regex("\\s+"), " ").lowercase()

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }
}
