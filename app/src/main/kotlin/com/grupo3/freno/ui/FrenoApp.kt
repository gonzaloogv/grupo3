package com.grupo3.freno.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.grupo3.freno.data.FrenoEventStore
import com.grupo3.freno.model.FrenoEvent
import com.grupo3.freno.model.RecommendedAction
import com.grupo3.freno.ui.theme.Border
import com.grupo3.freno.ui.theme.Canvas
import com.grupo3.freno.ui.theme.Danger
import com.grupo3.freno.ui.theme.DangerSoft
import com.grupo3.freno.ui.theme.Ink
import com.grupo3.freno.platform.FrenoNotificationListener
import com.grupo3.freno.ui.theme.InkMuted
import com.grupo3.freno.ui.theme.Safe
import com.grupo3.freno.ui.theme.SafetyBlue
import kotlinx.coroutines.launch

data class PermissionSnapshot(
    val notificationAccess: Boolean = false,
    val overlayAccess: Boolean = false,
    val alertNotificationAccess: Boolean = false,
) {
    val allGranted: Boolean get() = notificationAccess && overlayAccess && alertNotificationAccess
}

@Composable
fun FrenoApp(
    permissions: PermissionSnapshot,
    onRequestNotificationAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    onRequestAlertNotificationAccess: () -> Unit,
) {
    val events by FrenoEventStore.events.collectAsStateWithLifecycle()
    val listenerConnected by FrenoNotificationListener.connected.collectAsStateWithLifecycle()
    val highRiskAlert by FrenoEventStore.activeAlert.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(EventFilter.HISTORY) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val riskCount = events.count { it.analysisPresentation.requiresAttention }
    val trustedCount = events.count(FrenoEvent::trusted)
    val visibleEvents = events.filter(filter::accepts)

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "app-header") {
                AppHeader(permissions.allGranted && listenerConnected)
            }
            item(key = "summary") { ProtectionSummary(riskCount = riskCount) }
            item(key = "filters") {
                EventTabs(
                    selected = filter,
                    totalCount = events.size,
                    trustedCount = trustedCount,
                    onSelected = { filter = it },
                )
            }

            if (visibleEvents.isEmpty()) {
                item(key = "empty-${filter.name}") { EmptyEvents(filter) }
            } else {
                items(visibleEvents, key = FrenoEvent::id) { event ->
                    NotificationEventCard(
                        event = event,
                        onOpenDetail = { selectedEventId = event.id },
                        onToggleTrust = {
                            if (event.trusted) {
                                FrenoEventStore.distrust(event.id)
                                scope.launch { snackbarHostState.showSnackbar("Se quitó tu confianza del mensaje") }
                            } else {
                                FrenoEventStore.trust(event.id)
                                scope.launch { snackbarHostState.showSnackbar("Mensaje marcado como confiado por vos") }
                            }
                        },
                    )
                }
            }

            item(key = "permissions-title") {
                SectionTitle(
                    title = "Permisos del sistema",
                    supporting = "Freno necesita estos dos accesos para detectar y frenar una alerta automáticamente.",
                )
            }
            item(key = "permissions-card") {
                PermissionsCard(
                    permissions = permissions,
                    listenerConnected = listenerConnected,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onRequestOverlayAccess = onRequestOverlayAccess,
                    onRequestAlertNotificationAccess = onRequestAlertNotificationAccess,
                )
            }
        }
    }

    highRiskAlert?.let { event ->
        CriticalAlert(event = event, onDismiss = FrenoEventStore::dismissAlert)
    }
    events.find { it.id == selectedEventId }?.let { event ->
        EventDetail(event = event, onDismiss = { selectedEventId = null })
    }
}

@Composable
private fun CriticalAlert(event: FrenoEvent, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Danger,
            contentColor = Color.White,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(30.dp),
                        tint = Color.White,
                    )
                }
                Text(
                    text = "Pausa. Puede ser una estafa",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 20.dp).semantics { heading() },
                )
                Text(
                    text = "${event.source} · ${event.sender}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.84f),
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = event.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = "Qué conviene hacer",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = recommendationFor(event.action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Danger,
                    ),
                ) {
                    Text("ENTENDIDO", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun recommendationFor(action: RecommendedAction): String = when (action) {
    RecommendedAction.VERIFY_KNOWN_CONTACT -> "Antes de transferir, llamá al número que ya conocés."
    RecommendedAction.AVOID_LINK_AND_VERIFY -> "No abras el enlace. Consultá a la entidad desde su canal oficial."
    RecommendedAction.DO_NOT_SHARE_CODE -> "No compartas códigos ni claves con nadie."
    RecommendedAction.NONE -> "Revisá el mensaje con cautela antes de realizar cualquier acción."
}

@Composable
private fun EventDetail(event: FrenoEvent, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Detalle del análisis", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "${event.source} · ${event.whenLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = event.analysisPresentation.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(event.reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                Text(
                    text = "Señal detectada: ${reasonLabel(event.reasonCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = recommendationFor(event.action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = explanationLabel(event.explanationSource),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 18.dp),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                    Text("CERRAR")
                }
            }
        }
    }
}

private fun reasonLabel(code: String): String = when (code) {
    "NEW_NUMBER_AND_URGENT_PAYMENT" -> "Cambio de número y pedido urgente de dinero"
    "CREDENTIAL_REQUEST" -> "Pedido de credenciales mediante un enlace"
    "CODE_SHARING_REQUEST" -> "Pedido de compartir un código"
    "INSUFFICIENT_CONTEXT" -> "Contenido insuficiente"
    "ANALYSIS_UNAVAILABLE" -> "Análisis no disponible"
    "NO_CLEAR_SIGNAL" -> "Sin señales claras"
    else -> "Señal a revisar"
}

private fun explanationLabel(source: com.grupo3.freno.model.ExplanationSource): String = when (source) {
    com.grupo3.freno.model.ExplanationSource.GEMINI -> "Análisis de Gemini"
    com.grupo3.freno.model.ExplanationSource.TEMPLATE -> "Texto de respaldo de Freno"
    com.grupo3.freno.model.ExplanationSource.UNAVAILABLE -> "Análisis no disponible"
}

@Composable
private fun AppHeader(allPermissionsGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = Danger,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Freno", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (allPermissionsGranted) "Protección activa" else "Configuración incompleta",
                color = if (allPermissionsGranted) Safe else Danger,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Icon(
            imageVector = if (allPermissionsGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = if (allPermissionsGranted) "Protección activa" else "Faltan permisos",
            tint = if (allPermissionsGranted) Safe else Danger,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ProtectionSummary(riskCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Ink,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = riskCount.toString(),
                color = Color.White,
                fontSize = 48.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (riskCount == 1) "mensaje con señales de riesgo" else "mensajes con señales de riesgo",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Incluye los mensajes de riesgo alto y los que requieren revisión.",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EventTabs(
    selected: EventFilter,
    totalCount: Int,
    trustedCount: Int,
    onSelected: (EventFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EventTab(
            label = "Historial ($totalCount)",
            selected = selected == EventFilter.HISTORY,
            onClick = { onSelected(EventFilter.HISTORY) },
            modifier = Modifier.weight(1f),
        )
        EventTab(
            label = "Confiadas ($trustedCount)",
            selected = selected == EventFilter.TRUSTED,
            onClick = { onSelected(EventFilter.TRUSTED) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EventTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) Ink else Color.Transparent,
        animationSpec = tween(160),
        label = "tab-color",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        color = color,
        contentColor = if (selected) Color.White else InkMuted,
        shape = RoundedCornerShape(11.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NotificationEventCard(
    event: FrenoEvent,
    onOpenDetail: () -> Unit,
    onToggleTrust: () -> Unit,
) {
    val analysis = event.analysisPresentation
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${analysis.label}, notificación de ${event.sender}, ${event.whenLabel}" +
                    if (event.trusted) ", confiada por vos" else ""
            },
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            StatusBadge(analysis = analysis)
            if (event.trusted) {
                Text(
                    "Confiada por vos",
                    color = InkMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                event.whenLabel,
                color = InkMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = event.sender,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = event.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Border)
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = analysis.color,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        text = "Resultado del análisis",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                    )
                    Text(
                        text = event.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            TextButton(onClick = onOpenDetail, modifier = Modifier.padding(top = 8.dp)) {
                Text("Ver detalle")
            }
            TrustActionButton(
                trusted = event.trusted,
                onClick = onToggleTrust,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = if (event.trusted) {
                    "Tu confianza es manual y no cambia el resultado del análisis."
                } else {
                    "Confiar marca solo este evento; no habilita futuros mensajes."
                },
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusBadge(analysis: EventAnalysis) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = analysis.color.copy(alpha = 0.1f),
        contentColor = analysis.color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (analysis) {
                    EventAnalysis.HIGH, EventAnalysis.REVIEW -> Icons.Rounded.Warning
                    EventAnalysis.LOW -> Icons.Rounded.CheckCircle
                    EventAnalysis.ANALYZING -> Icons.Rounded.History
                    EventAnalysis.UNAVAILABLE -> Icons.Rounded.Info
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = analysis.label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

private val EventAnalysis.color: Color
    get() = when (this) {
        EventAnalysis.HIGH -> Danger
        EventAnalysis.REVIEW -> Color(0xFF8A4B08)
        EventAnalysis.LOW -> Safe
        EventAnalysis.ANALYZING -> SafetyBlue
        EventAnalysis.UNAVAILABLE -> InkMuted
    }

@Composable
private fun TrustActionButton(trusted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(140, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "trust-button-scale",
    )
    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (trusted) Danger else SafetyBlue),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (trusted) Danger else SafetyBlue,
        ),
    ) {
        Icon(
            imageVector = if (trusted) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = if (trusted) "Quitar confianza" else "Confiar",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SectionTitle(title: String, supporting: String) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(
            supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionSnapshot,
    listenerConnected: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    onRequestAlertNotificationAccess: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = "Leer notificaciones",
                description = "Detecta mensajes peligrosos cuando llegan.",
                granted = permissions.notificationAccess,
                onRequest = onRequestNotificationAccess,
            )
            if (permissions.notificationAccess && !listenerConnected) {
                Text(
                    "El servicio de notificaciones se está conectando. Si no cambia, cerrá y abrí Freno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger,
                )
            }
            HorizontalDivider(color = Border)
            PermissionRow(
                icon = Icons.Rounded.Security,
                title = "Mostrar alertas invasivas",
                description = "Abre la advertencia roja encima de otras apps.",
                granted = permissions.overlayAccess,
                onRequest = onRequestOverlayAccess,
            )
            HorizontalDivider(color = Border)
            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = "Avisos de riesgo",
                description = "Muestra un aviso si el teléfono está bloqueado.",
                granted = permissions.alertNotificationAccess,
                onRequest = onRequestAlertNotificationAccess,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = if (granted) Safe.copy(alpha = 0.1f) else DangerSoft,
            contentColor = if (granted) Safe else Danger,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(25.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
        if (granted) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Permitido",
                tint = Safe,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Text("Permitir")
            }
        }
    }
}

@Composable
private fun EmptyEvents(filter: EventFilter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = if (filter == EventFilter.HISTORY) {
                    "Todavía no hay notificaciones en el historial"
                } else {
                    "Todavía no confiaste ninguna notificación"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Preview(
    name = "Inicio - teléfono",
    showBackground = true,
    backgroundColor = 0xFFF6F8FB,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun FrenoHomePreview() {
    com.grupo3.freno.ui.theme.FrenoTheme {
        FrenoApp(
            permissions = PermissionSnapshot(
                notificationAccess = true,
                overlayAccess = true,
                alertNotificationAccess = true,
            ),
            onRequestNotificationAccess = {},
            onRequestOverlayAccess = {},
            onRequestAlertNotificationAccess = {},
        )
    }
}
