# Programador B · Backend, Gemini y evaluación

**Objetivo:** entregar clasificación con Gemini y una justificación comprensible que Android conserve en el historial. B prepara el contrato, los casos de prueba y el relato de la demo.

Stack: Kotlin/JVM, Ktor Server, cliente HTTP y kotlinx.serialization. Modelo: `gemini-3.5-flash-lite`, con credenciales del proyecto de Google AI Studio. B controla `server/` y `shared/`; los DTO no dependen de Android.

**Prioridades:** P0 backend + Gemini + evaluación; P1 **correo electrónico** si sobra tiempo; P2 Telegram después del correo y solo si aún queda tiempo. Ningún canal externo condiciona la entrega del MVP.

**Presupuesto:** 30 min de arranque + 4 h 30 min de implementación + 1 h de pruebas + 1 h de ensayo/entrega + 1 h de buffer = **8 horas**. B-01 a B-07 son P0 y están pendientes. No quitar tiempo de validación o ensayo para incorporar extras.

## Arranque compartido · 30 min

Crear servidor, acordar `AnalysisRequest` y `AnalysisResult` con A y entregar una respuesta de ejemplo. `DeliveryResult` se incorpora solo con el extra de correo. Trabajar desde `develop` y abrir PR a `develop`; ver [entorno y Git](../docs/ENTORNO_Y_GIT.md).

## B-01 · API y contrato · 20 min

Exponer `GET /health` y `POST /v1/analyze`, inicialmente con respuesta de prueba; agregar token de demo.

**Dependencia:** arranque.

- [x] A puede invocar y deserializar el contrato compartido.
- [x] Solicitudes sin token válido se rechazan; health no expone secretos.
- [x] Los enums coinciden con los ejemplos y errores documentados.

## B-02 · Clasificación real con Gemini · 40 min

Enviar texto como datos no confiables, separado del prompt, con salida estructurada y validación.

**Dependencia:** B-01.

- [ ] Una llamada real funciona con el modelo elegido en la cuenta del equipo.
- [ ] Validar risk, category, reasonCode, reasonSimple y action; el motivo se basa en señales del texto.
- [ ] El servidor fija eventId, analyzer, model, promptVersion y explanationSource; registra tiempos sin cuerpos de mensajes.
- [ ] reasonSimple explica la clasificación y puede guardarse tal cual en el historial, sin otra llamada al consultar el registro.
- [ ] El modelo no abre enlaces ni ejecuta acciones.

## B-03 · Errores y respuestas inválidas · 45 min

Manejar timeout, 429, 5xx, bloqueos y JSON inválido, con validación semántica.

**Dependencia:** B-02.

- [ ] Fallos técnicos devuelven UNKNOWN/UNAVAILABLE, nunca LOW por defecto.
- [ ] Explicación larga o con enlaces se reemplaza por texto fijo y explanationSource TEMPLATE; un fallo técnico usa UNAVAILABLE, sin atribuir ese texto a Gemini.
- [ ] HIGH con action NONE u otros campos incompatibles no produce una intervención basada en datos inválidos.
- [ ] No hay reintentos ilimitados; A puede reproducir los estados de error.

## B-04 · Corpus sintético reducido · 35 min

Preparar **12 casos**: 6 con señales de engaño, 4 cotidianos y 2 ambiguos. Revisar etiquetas con A y separar 6 de desarrollo y 6 reservados, con 3/2/1 por grupo.

**Dependencia:** arranque; puede avanzarse sin esperar Android.

- [ ] Incluir suplantación bancaria, familiar y pedido de códigos; dos ejemplos por categoría.
- [ ] Incluir controles educativos y cotidianos; usar datos ficticios y dominios `.example`.
- [ ] Compartir casos de desarrollo para integración y conservar los reservados para el cierre.
- [ ] No usar resultados reservados para ajustar el prompt ni presentar esta muestra como precisión real.

## B-05 · Integración con Android y pruebas de contrato · 45 min

Probar junto a A el recorrido real y los errores del servidor. Preparar respuestas reproducibles para los cuatro estados.

**Dependencias:** B-01, B-02, B-03 y A-06 para el ensayo conjunto.

- [ ] Un evento real vuelve con su eventId y justificación correctos, y actualiza la fila de historial correspondiente.
- [ ] HIGH, REVIEW, LOW y UNKNOWN se deserializan correctamente.
- [ ] La alerta local funciona sin correo ni Telegram.
- [ ] Abrir el historial no llama de nuevo a Gemini; el motivo mostrado coincide con el resultado guardado.
- [ ] Anotar diferencias de contrato y resolverlas antes de congelar P0 a la hora 5.

## B-06 · Caché de análisis y límites · 35 min

Guardar resultados válidos hasta 15 minutos y 100 eventos, sin texto original. Reconocer repeticiones con eventId y hash de contenido.

**Dependencias:** B-01 y B-02.

- [ ] Repetir un análisis completado reutiliza su resultado sin otra llamada a Gemini.
- [ ] Mismo eventId con texto distinto se rechaza, sin devolver una clasificación ajena.
- [ ] Rechazar cuerpos mayores a 8 KB y texto superior a 2.000 caracteres.
- [ ] Expirar caché y mantener límites; documentar pérdida de memoria al reiniciar.

## B-07 · Evaluación inicial y ejecución reproducible · 50 min

Evaluar con los seis casos de desarrollo, versionar el prompt y documentar ejecución. Reservar el lote final para la hora compartida de pruebas.

**Dependencias:** B-03, B-04 y B-06.

- [ ] Registrar esperado/obtenido y comprobar que la justificación describe señales presentes en el mensaje, sin inventar verificaciones.
- [ ] Probar que instrucciones dentro de una estafa no cambian la política de acciones.
- [ ] No usar porcentajes de confianza del LLM como garantía.
- [ ] Documentar GEMINI_API_KEY, GEMINI_MODEL, token de demo y arranque sin claves de canales opcionales.
- [ ] No versionar secretos ni registrarlos en logs.

## Orden, hitos y entrega

B-01 + B-02 permiten una primera clasificación real a la hora 1:30. Después, avanzar con errores, corpus y caché mientras A construye Android e historial. Preparar B-05 desde la hora 3 y hacer la prueba conjunta cuando A-06 esté disponible, antes de la hora 5.

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
