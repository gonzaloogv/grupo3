# Tareas y validación · 2 programadores · 8 horas

Todas las tareas están **pendientes**. A lleva Android, interfaz e historial; B lleva backend, Gemini, Google Safe Browsing, justificaciones y evaluación. P0 incluye alerta visual e historial persistente. La voz queda descartada; alarma sonora y overlay son opcionales. Correo es el primer canal externo; Telegram va después y solo si sobra tiempo.

## 1. Arranque compartido · 30 minutos

**F-00:** A crea la app, prepara el teléfono con ADB/scrcpy y elige una fuente real. B prepara Ktor y los DTO. Ambos acuerdan estados y respuesta de ejemplo. Aceptación: app abre, health responde y el contrato está compartido.

Cada integrante dispone de **270 minutos de implementación** más arranque, pruebas, entrega y buffer: 480 minutos por persona, 16 horas-persona en total.

## 2. A · Android, interfaz e historial · 270 minutos

| ID | Feature | Tiempo | Depende de | Terminado cuando |
| --- | --- | --- | --- | --- |
| A-01 | Captura real de una fuente | 30 min | F-00 | Llega texto disponible con la app fuera del primer plano |
| A-02 | Alerta visual y acceso al motivo | 40 min | F-00 | Notificación abre advertencia y explicación; cierre sin abrir enlaces |
| A-03 | Historial persistente y detalle | 75 min | DTO | Room, lista y justificación sobreviven a reabrir; consulta offline y borrado |
| A-04 | Estado y ajustes mínimos | 20 min | A-01 | Consentimiento, permisos y pausa desde el historial |
| A-05 | Extracción, duplicados y coordinación | 40 min | A-01, A-02, contrato A-03 | Crear/actualizar registro; duplicados y contenido insuficiente controlados |
| A-06 | Backend real y conservación del resultado | 40 min | A-05, B-01, B-02, B-03 | Respuesta, procedencia y estado de reputación se persisten en el eventId correcto |
| A-07 | Fallos, persistencia y accesibilidad | 25 min | A-02, A-03, A-04, A-06 | Permisos/red no rompen UI; historial persiste y se borra; fuente al 200 % |

Orden recomendado: **A-01 → A-02 → A-03 → A-05 → A-06 → A-04 → A-07**. A controla todo `app/`, Gradle y Manifest. Consultar [features de A](../feat/programador-a-android.md) y [diseño de interfaz e historial](INTERFAZ_E_HISTORIAL.md).

## 3. B · Backend, Gemini, Safe Browsing y evaluación · 270 minutos

| ID | Feature | Tiempo | Depende de | Terminado cuando |
| --- | --- | --- | --- | --- |
| B-01 | API, contrato y token de demo | 20 min | F-00 | A deserializa urlAssessment; sin token se rechaza |
| B-02 | Gemini real, clasificación y justificación | 40 min | B-01 | Devuelve riesgo y motivo validado con procedencia de explicación |
| B-03 | Safe Browsing y reputación de URLs | 30 min | B-01 | NO_URL, MATCH, NO_MATCH y UNAVAILABLE funcionan con proveedor simulado y una prueba real |
| B-04 | Fusión conservadora y errores | 45 min | B-02, B-03 | MATCH fuerza HIGH; fallos y respuestas inválidas nunca producen seguridad falsa |
| B-05 | Corpus sintético y fixtures de reputación | 30 min | F-00 | 12 mensajes y fixtures MATCH/NO_MATCH/UNAVAILABLE, mitad reservada |
| B-06 | Integración Android, caché y límites | 45 min | B-01 a B-04; A-06 para ensayo | Resultado completo queda en el registro; repetir no vuelve a consultar proveedores |
| B-07 | Evaluación y ejecución reproducible | 60 min | B-04, B-05, B-06 | Resultados, prompt, fusión y entorno documentados sin secretos |

B puede avanzar con JSON de ejemplo mientras A implementa la app. B controla `server/` y `shared/`. Detalle: [features de B](../feat/programador-b-ia-backend.md).

## 4. Hitos de integración

| Hora | Resultado verificable | Responsables |
| --- | --- | --- |
| 0:30 | App, health y contrato inicial | A + B |
| 1:30 | Captura real comprobada; llamada real de Gemini independiente | A + B |
| 3:00 | Historial persistente; Safe Browsing y fusión probados con fixtures | A + B |
| 4:00 | Pipeline de captura e historial listo; integración real en curso | A + B |
| 5:00 | Notificación → Gemini/Safe Browsing → alerta visual → historial con motivo; congelar P0 | A + B |
| 6:00 | Pruebas reservadas y de dispositivo registradas | A + B |
| 7:00 | APK, dos ensayos y video de respaldo | A + B |
| 8:00 | Fin del buffer y entrega | A + B |

Usar dos ramas desde `develop`, por ejemplo `android` y `ai-backend`, e integrar por PR a `develop`. Promover a `main` tras validar en el teléfono con scrcpy. No cambiar el contrato sin coordinar. Ver [entorno y Git](ENTORNO_Y_GIT.md).

## 5. Validación compartida · 1 hora

B prepara 12 mensajes sintéticos: 6 con señales de engaño (2 bancarios, 2 de familiar, 2 de códigos), 4 cotidianos y 2 ambiguos. Revisar etiquetas entre ambos. Usar 6 en desarrollo y reservar 6 (3 engaños, 2 cotidianos y 1 ambiguo) sin ajustar el prompt a sus resultados.

| Ejemplo | Resultado esperado |
| --- | --- |
| «Soy tu hijo, cambié de número. Transferime urgente y no me llames.» | HIGH |
| «Tu cuenta será suspendida. Ingresá tu clave en https://banco-validacion.example.» | HIGH |
| «Soy soporte. Pasame el código que recibiste para evitar el bloqueo.» | HIGH |
| «Abu, mañana paso a tomar mate.» | LOW |
| «Nunca compartas códigos con quien dice ser del banco.» | LOW |
| «Necesito ayuda con un pago, llamame cuando puedas.» | REVIEW |

Son expectativas humanas para una muestra pequeña, no verificación de remitentes reales. Registrar aciertos, falsos positivos y falsos negativos por caso; cualquier fallo de los casos del guion debe corregirse o explicitarse. No publicar un porcentaje de precisión general a partir de este conjunto.

| Prueba | Resultado requerido | Responsable |
| --- | --- | --- |
| Notificación repetida y luego actualizada | No duplicar el mismo texto; texto distinto puede analizarse | A |
| Permisos retirados | Sin crash; estado degradado; fallback si el sistema permite notificar | A |
| Sin internet, 429 o timeout | UNKNOWN; sin garantía falsa de seguridad | B + A |
| JSON inválido o campos incompatibles | Rechazo/fallback controlado | B |
| Instrucción dentro del mensaje para devolver LOW | Se trata como dato; no cambia la política de acciones | B |
| URL con MATCH simulado | HIGH con motivo URL_LISTED_AS_THREAT, acción de no abrir y atribución a Google | B + A |
| URL con NO_MATCH | No se muestra «seguro» y no se reduce la clasificación de Gemini | B + A |
| URL con timeout o 429 | Estado UNAVAILABLE; LOW de Gemini se convierte en UNKNOWN | B |
| Más de tres URLs | `contentIncomplete=true`; si Gemini solo daría LOW, el resultado final es UNKNOWN | A + B |
| URL con MATCH y Gemini caído | HIGH basado en Google; analyzer UNAVAILABLE y explicación de plantilla | B |
| Mensaje sin URL | No se llama a Safe Browsing; Gemini conserva su flujo normal | B |
| Texto vacío, oculto o truncado | No fingir evaluación completa | A |
| Fuente al 200 % | Alerta e historial legibles y controles accesibles | A |
| Cierre durante error de red | Alerta cerrable; historial conserva el fallo con causa | A |
| Cerrar/reabrir y consultar offline | Registros y justificaciones persisten sin volver a llamar a Gemini | A + B |
| Borrar historial con análisis en curso | El registro eliminado no reaparece al llegar la respuesta | A |
| Justificación de un HIGH | Describe señales del texto; no inventa identidad verificada ni hechos externos | B |
| Alarma, solo si se implementa | Respeta ajustes; no se repite al abrir un registro | A |
| Correo fallido/incierto, solo si se implementó el extra | FAILED/UNKNOWN_DELIVERY; sin SENT falso ni reintento ciego | B + A |

Medir desde evento hasta publicación de la advertencia, separando tiempo de backend/IA. Si la pantalla requiere un toque, registrar esa modalidad y no atribuir la espera del usuario al modelo. Objetivo de experiencia: mediana menor a 3 segundos y ninguna corrida superior a 7 segundos para publicar el aviso, sobre 10 corridas. Reportar condiciones y valores medidos, sin presentarlos como garantía.

Pruebas automáticas útiles: deduplicación, validación de respuesta y actualización por eventId. Permisos, presentación, legibilidad, persistencia y borrado se verifican en el teléfono físico. No añadir una suite extensa durante la hackathon.

## 6. Ensayo y entrega · 1 hora, más 1 hora de buffer

A prepara la APK y opera el teléfono; B prepara el relato, anota tiempos y graba. Hacer dos ensayos completos antes de cerrar.

Guion de 3 minutos:

1. **0:00–0:25:** problema y teléfono configurado, Freno fuera del primer plano.
2. **0:25–0:50:** mensaje cotidiano que no interrumpe.
3. **0:50–1:25:** mensaje sintético de estafa → aviso → alerta visual. Mostrar el toque que abre la advertencia.
4. **1:25–2:10:** ENTENDIDO cierra; abrir historial y detalle para mostrar si la señal vino de Gemini, Safe Browsing o ambos.
5. **2:10–2:35:** cerrar/reabrir la app y conservar el registro; mostrar correo solo si el extra funciona y el tiempo alcanza.
6. **2:35–3:00:** mostrar alcance validado y siguientes pasos.

Entregar APK, servidor ejecutable, variables de ejemplo sin secretos, prompt versionado, 12 casos con resultados, prueba de Safe Browsing documentada, pasos reproducibles y video de 60–90 segundos. Anotar fuente, teléfono/Android, modalidad de presentación y limitaciones. Distinguir demostración simulada de flujo real.

## 7. Extras y recortes

| Prioridad | Feature | Responsables | Condición |
| --- | --- | --- | --- |
| P1 · primer extra | Correo familiar | B: envío; A: cliente/UI | P0 estable; proveedor y remitente listos; 45–60 min de B + 30 min de A disponibles |
| P2 | Telegram | B + A | Correo ya funciona y todavía sobra tiempo |
| Opcional | Alarma sonora breve | A | P0 estable; configurable, sin voz y sin forzar volumen ni silencio/No molestar |
| Opcional | Overlay | A | P0 estable; probar como máximo 20 min sin desplazar historial |
| Posterior | Vibración personalizada, segunda fuente, botón de llamada, inyector debug con UI | A | Fuera del compromiso de 8 horas |
| Posterior | Entrega durable, llamadas, modelo local, panel web | B + A | Otra iteración |

No recortar historial, pruebas ni ensayo para incluir extras. La demo base usa notificación y apertura manual de alerta. Si la fuente real falla, usar una entrada de prueba identificada y registrar que captura real sigue pendiente.
