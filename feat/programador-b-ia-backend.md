# Programador B · Backend, Gemini, Safe Browsing y evaluación

**Objetivo:** combinar la clasificación textual de Gemini con reputación de URLs de Google Safe Browsing y entregar una justificación comprensible que Android conserve en el historial. B prepara el contrato, los casos de prueba y el relato de la demo.

Stack: Kotlin/JVM, Ktor Server, cliente HTTP y kotlinx.serialization. Modelo: `gemini-3.5-flash-lite`, con credenciales del proyecto de Google AI Studio; reputación mediante Google Safe Browsing Lookup API. B controla `server/` y `shared/`; los DTO no dependen de Android.

**Prioridades:** P0 backend + Gemini + Safe Browsing + evaluación; P1 **correo electrónico** si sobra tiempo; P2 Telegram después del correo y solo si aún queda tiempo. Ningún canal externo condiciona la entrega del MVP.

**Presupuesto:** 30 min de arranque + 4 h 30 min de implementación + 1 h de pruebas + 1 h de ensayo/entrega + 1 h de buffer = **8 horas**. B-01 a B-07 son P0 y están pendientes. No quitar tiempo de validación o ensayo para incorporar extras.

## Arranque compartido · 30 min

Crear servidor, acordar `AnalysisRequest`, `AnalysisResult` y `UrlAssessment` con A y entregar respuestas de ejemplo con y sin URL. `DeliveryResult` se incorpora solo con el extra de correo. Trabajar desde `develop` y abrir PR a `develop`; ver [entorno y Git](../docs/ENTORNO_Y_GIT.md).

## B-01 · API y contrato · 20 min

Exponer `GET /health` y `POST /v1/analyze`, inicialmente con respuesta de prueba; agregar token de demo.

**Dependencia:** arranque.

- [x] A puede invocar y deserializar el contrato compartido.
- [x] Solicitudes sin token válido se rechazan; health no expone secretos.
- [x] Los enums coinciden con los ejemplos y errores documentados; `urls` acepta como máximo tres HTTP(S).

## B-02 · Clasificación real con Gemini · 40 min

Enviar texto como datos no confiables, separado del prompt, con salida estructurada y validación.

**Dependencia:** B-01.

- [x] Una llamada real funciona con el modelo elegido en la cuenta del equipo.
- [x] Validar risk, category, reasonCode, reasonSimple y action; el motivo se basa en señales del texto.
- [x] El servidor fija eventId, analyzer, model, promptVersion, explanationSource y decisionSources; registra tiempos sin cuerpos de mensajes.
- [x] reasonSimple explica la clasificación y puede guardarse tal cual en el historial, sin otra llamada al consultar el registro.
- [x] El modelo no abre enlaces ni ejecuta acciones.

## B-03 · Safe Browsing y reputación de URLs · 30 min

Encapsular Google Safe Browsing detrás de `UrlReputationProvider` y consultar hasta tres URLs sin abrirlas ni descargar contenido.

**Dependencia:** B-01.

- [x] Estados NO_URL, MATCH, NO_MATCH y UNAVAILABLE; consultar SOCIAL_ENGINEERING y MALWARE.
- [x] Omitir el proveedor cuando no hay URL; timeout inicial de 1,5 segundos y sin reintentos ilimitados.
- [x] Simular MATCH, NO_MATCH, 429 y timeout; realizar una sola prueba autenticada con la página oficial de prueba de Google.
- [x] Respetar `cacheDuration`; NO_MATCH significa «no reportada», nunca «segura».

## B-04 · Fusión conservadora y errores · 45 min

Ejecutar Gemini y Safe Browsing en paralelo y aplicar la decisión final en Kotlin. Manejar 429, 5xx, bloqueos y JSON inválido de ambos proveedores.

**Dependencias:** B-02 y B-03.

- [x] MATCH de SOCIAL_ENGINEERING o MALWARE fuerza HIGH, URL_THREAT, URL_LISTED_AS_THREAT y AVOID_LINK_AND_VERIFY, incluso si Gemini falla.
- [x] NO_MATCH no reduce el riesgo devuelto por Gemini.
- [x] Con URL, Safe Browsing UNAVAILABLE o `contentIncomplete=true` y Gemini LOW, devolver UNKNOWN; conservar HIGH o REVIEW de Gemini.
- [x] Una explicación larga o con enlaces usa TEMPLATE; un fallo técnico usa UNAVAILABLE y nunca se atribuye al proveedor incorrecto.
- [x] HIGH con action NONE u otros campos incompatibles no produce una intervención basada en datos inválidos.

## B-05 · Corpus sintético y fixtures de reputación · 30 min

Preparar **12 casos**: 6 con señales de engaño, 4 cotidianos y 2 ambiguos. Agregar fixtures deterministas para las cuatro respuestas de reputación y separar 6 casos de desarrollo y 6 reservados.

**Dependencia:** arranque; puede avanzarse sin esperar Android.

- [x] Incluir suplantación bancaria, familiar y pedido de códigos; al menos dos mensajes contienen enlaces sintéticos `.example`.
- [x] Cubrir MATCH, NO_MATCH, UNAVAILABLE y mensaje sin URL mediante el proveedor simulado.
- [x] Compartir casos de desarrollo para integración y conservar los reservados para el cierre.
- [x] No usar resultados reservados para ajustar el prompt ni presentar esta muestra como precisión real.

## B-06 · Integración Android, caché y límites · 45 min

Probar con A el recorrido real, los cuatro niveles de riesgo y los estados de reputación. Guardar hasta 100 resultados de análisis durante 15 minutos y cachear reputación según la respuesta de Google.

**Dependencias:** B-01 a B-04 y A-06 para el ensayo conjunto.

- [ ] Un evento vuelve con eventId, justificación y urlAssessment correctos, y actualiza la fila correspondiente.
- [ ] Repetir un análisis completado reutiliza el resultado sin otra llamada a Gemini o Safe Browsing.
- [ ] Mismo eventId con texto distinto se rechaza, sin devolver una clasificación ajena.
- [ ] Rechazar cuerpos mayores a 8 KB y texto superior a 2.000 caracteres.
- [ ] La alerta funciona sin correo/Telegram y abrir el historial no consulta nuevamente ningún proveedor.

## B-07 · Evaluación inicial y ejecución reproducible · 60 min

Evaluar con los seis casos de desarrollo, versionar el prompt y documentar ejecución. Reservar el lote final para la hora compartida de pruebas.

**Dependencias:** B-04, B-05 y B-06.

- [ ] Registrar esperado/obtenido y comprobar que la justificación describe señales presentes en el mensaje, sin inventar verificaciones.
- [ ] Probar que instrucciones dentro de una estafa no cambian la política de acciones.
- [ ] No usar porcentajes de confianza del LLM como garantía.
- [ ] Documentar GEMINI_API_KEY, GEMINI_MODEL, SAFE_BROWSING_API_KEY, token de demo y arranque sin claves de canales opcionales.
- [ ] Documentar que Safe Browsing es gratuito para uso no comercial y que una alerta basada en Google requiere su atribución.
- [ ] No versionar secretos ni registrarlos en logs.

## Orden, hitos y entrega

B-01 + B-02 permiten una primera clasificación real a la hora 1:30. Completar B-03 y B-04 antes de la hora 3; después avanzar con corpus y caché mientras A construye Android e historial. Preparar B-06 desde la hora 3 y hacer la prueba conjunta cuando A-06 esté disponible, antes de la hora 5.

De 5:00 a 6:00, ejecutar el lote reservado, revisar justificaciones y probar fallos de API con A. De 6:00 a 7:00, preparar el guion de 3 minutos y grabar alerta → historial → detalle → reapertura mientras A prepara la APK. Reservar 7:00–8:00 para fallos.

## Extra P1 · Correo electrónico · Solo si sobra tiempo

Primer canal externo a implementar. Estimación adicional: **45–60 min de B y 30 min de A**, suponiendo servicio de envío y remitente disponibles. Si prepararlos demora, mantener el extra pendiente.

Agregar `POST /v1/family-alerts` y un adaptador de correo. El proveedor se elige al abordar esta tarea; no requiere contratación ni configuración durante P0.

- [ ] Enviar solo para HIGH válido, aviso habilitado y una dirección de confianza preconfigurada.
- [ ] Asunto «Freno: mensaje sospechoso detectado»; categoría, hora y recomendación en el cuerpo, sin mensaje original ni enlaces sospechosos.
- [ ] El cliente envía eventId y estado de presentación; no elige destinatarios arbitrarios.
- [ ] Controlar duplicados y SENT, FAILED, UNKNOWN_DELIVERY y SKIPPED; aceptación del servicio no significa lectura ni llegada garantizada a la bandeja de entrada.
- [ ] Comprobar recepción en la casilla de demo; el fallo de envío no afecta la alerta local.

## Extra P2 · Telegram · Solo después del correo

Reutilizar el contrato opcional con un adaptador Telegram, únicamente si el correo funciona y todavía queda tiempo. Activar un solo canal durante la demo para evitar avisos dobles. No desplazar validación ni ensayo.

Referencias: [Arquitectura y prompt](../docs/ARQUITECTURA.md), [Interfaz e historial](../docs/INTERFAZ_E_HISTORIAL.md) y [Tareas y validación](../docs/TAREAS.md).
