package ar.com.freno.server.application

import ar.com.freno.shared.contract.ReasonCode

internal object ExplanationTemplates {
    const val URL_THREAT =
        "Google reporta este enlace como potencialmente peligroso; no lo abras y verificá por un canal conocido."

    fun isSafe(reason: String): Boolean {
        val normalized = reason.trim()
        return normalized.isNotEmpty() &&
            normalized.split(Regex("\\s+")).size <= 25 &&
            !Regex("(?i)https?://|www\\.").containsMatchIn(normalized)
    }

    fun forReasonCode(reasonCode: ReasonCode): String = when (reasonCode) {
        ReasonCode.NEW_NUMBER_AND_URGENT_PAYMENT ->
            "El mensaje combina un cambio de número con un pedido urgente de dinero."
        ReasonCode.CREDENTIAL_REQUEST ->
            "El mensaje solicita credenciales; verificá el pedido por un canal conocido."
        ReasonCode.CODE_SHARING_REQUEST ->
            "El mensaje solicita compartir un código que debe mantenerse privado."
        ReasonCode.INSUFFICIENT_CONTEXT ->
            "Hay una señal preocupante, pero falta contexto para confirmar el riesgo."
        ReasonCode.NO_CLEAR_SIGNAL ->
            "No se observan señales claras en el texto disponible; esto no garantiza seguridad."
        ReasonCode.OTHER_SIGNAL ->
            "El mensaje contiene una señal que conviene verificar por un canal conocido."
        ReasonCode.URL_LISTED_AS_THREAT -> URL_THREAT
        ReasonCode.ANALYSIS_UNAVAILABLE -> "No se pudo analizar el mensaje en este momento."
    }
}
