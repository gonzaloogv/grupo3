package ar.com.freno.server.application

import java.net.URI

internal object SensitiveDataRedactor {
    private val url = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\\\"']+")
    private val email = Regex("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b")
    private val alias = Regex("(?i)\\b(alias\\s*(?::|es)?\\s+)([\\p{L}0-9._-]{3,})")
    private val code = Regex("(?i)\\b((?:c[oó]digo|otp|pin)\\s*(?::|es|=)?\\s+)(\\d{4,8})\\b")
    private val bankNumber = Regex("(?i)\\b((?:cbu|cvu)\\s*(?::|es)?\\s+)([\\d\\s-]{8,})")
    private val account = Regex("(?i)\\b(cuenta\\s*(?:n[úu]mero|nro\\.?|n[°º.]?|#)\\s*[:=]?\\s*)([\\d\\s-]{6,})")
    private val phone = Regex("(?<![\\p{L}\\d])\\+?\\d[\\d .()-]{7,}\\d(?![\\p{L}\\d])")

    fun redact(text: String): String {
        var value = url.replace(text) { match ->
            val raw = match.value.trimEnd('.', ',', ';', '!', '?')
            val suffix = match.value.substring(raw.length)
            val normalized = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            val domain = runCatching { URI(normalized).host }.getOrNull()
            if (domain == null) "[ENLACE]$suffix" else "[ENLACE: $domain]$suffix"
        }
        value = email.replace(value, "[CORREO]")
        value = alias.replace(value) { "${it.groupValues[1]}[ALIAS]" }
        value = code.replace(value) { "${it.groupValues[1]}[CÓDIGO]" }
        value = bankNumber.replace(value) { "${it.groupValues[1]}[CUENTA]" }
        value = account.replace(value) { "${it.groupValues[1]}[CUENTA]" }
        return phone.replace(value, "[TELÉFONO]")
    }
}
