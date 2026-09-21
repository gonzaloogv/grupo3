package com.grupo3.freno.capture

/**
 * Datos mínimos extraídos por el listener. El texto original vive solo mientras
 * se clasifica y no se guarda en el historial.
 */
data class CapturedNotification(
    val notificationKey: String,
    val packageName: String,
    val source: String,
    val sender: String,
    val text: String,
    val postedAtMillis: Long,
    val contentIncomplete: Boolean,
)
