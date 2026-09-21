package com.grupo3.freno.data

import com.grupo3.freno.model.FrenoEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Almacén en memoria para el prototipo visual.
 * La capa Room reemplazará este objeto sin cambiar los composables.
 */
object FrenoEventStore {
    private val _events = MutableStateFlow(seedEvents())
    val events = _events.asStateFlow()

    fun trust(id: String) = updateTrust(id, trusted = true)

    fun distrust(id: String) = updateTrust(id, trusted = false)

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
