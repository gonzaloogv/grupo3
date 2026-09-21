# Freno · Hackathon de 2 programadores

**Freno analiza notificaciones en segundo plano, muestra una advertencia visual y guarda un historial que explica por qué clasificó cada mensaje.** No incluye voz; la alarma sonora y el aviso a un familiar son mejoras opcionales.

Stack propuesto: **Kotlin + Android nativo + Jetpack Compose + Room + Ktor + Gemini 3.5 Flash-Lite + Google Safe Browsing**. P0 combina análisis del texto con reputación de hasta tres URLs, alerta visual e historial persistente con justificaciones. Correo será el primer canal externo si sobra tiempo; Telegram solo después del correo.

Estado: planificación; la aplicación todavía no está implementada. Condiciones: **8 horas, 2 programadores**. Asignar el frente A a quien domina Android/Kotlin.

## Documentos para trabajar

- [Plan de producto y cronograma](docs/PLAN_HACKATHON.md): alcance, experiencia, reparto de las 8 horas y recortes.
- [Arquitectura y contrato de IA](docs/ARQUITECTURA.md): componentes Kotlin, permisos, API, estados y prompt inicial.
- [Interfaz e historial](docs/INTERFAZ_E_HISTORIAL.md): pantalla principal, detalle del motivo, persistencia y alarma opcional.
- [Tareas y validación](docs/TAREAS.md): tarjetas con responsables, dependencias, aceptación y guion de demo.
- [Flujo de Git y entorno Android](docs/ENTORNO_Y_GIT.md): integración en `develop`, promoción a `main` y pruebas con teléfono físico mediante scrcpy.

## Features por programador

- [Programador A · Android, interfaz e historial](feat/programador-a-android.md).
- [Programador B · Backend, Gemini, Safe Browsing y evaluación; correo y Telegram opcionales](feat/programador-b-ia-backend.md).

## Reparto inicial

| Persona | Responsabilidad | Entrega principal |
| --- | --- | --- |
| A | Android, interfaz e historial | Captura, alerta visual, lista/detalle de registros y almacenamiento local |
| B | Backend, Gemini, Safe Browsing y evaluación | API, clasificación, reputación de URLs, justificación, errores y casos de prueba |

Hitos: captura y Gemini por separado a la hora 1:30; Safe Browsing e historial con datos simulados a la hora 3; integración real entre las horas 3 y 5; congelar P0 a la hora 5. Reservar una hora de pruebas, una de ensayo/entrega y una de buffer.

Alcance: una fuente, un teléfono, configuración mínima y 12 casos de evaluación. Al abrir la app se ve el historial; cada detalle muestra el texto redactado, riesgo, justificación y recomendación. La notificación abre la alerta al tocarla. Overlay y alarma sonora quedan opcionales; el historial es obligatorio.

El prototipo **advierte sobre señales de riesgo**. No bloquea transferencias, no verifica identidades y no garantiza detectar todas las estafas.
