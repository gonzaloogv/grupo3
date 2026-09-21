# Programador A · Android, interfaz e historial

**Objetivo:** analizar notificaciones en segundo plano, mostrar una alerta visual y permitir consultar el historial con la justificación de cada clasificación.

Stack: Kotlin, Jetpack Compose, NotificationListenerService, Coroutines, Ktor Client y Room. A controla `app/`, Gradle y Manifest; B controla backend y contratos compartidos. **No se implementa voz ni TextToSpeech.** Una alarma sonora breve es opcional.

**Presupuesto:** 30 min de arranque + 4 h 30 min de implementación + 1 h de pruebas + 1 h de ensayo/entrega + 1 h de buffer = **8 horas**. A-01 a A-07 son P0 y están pendientes.

## Arranque compartido · 30 min

Crear la app, preparar teléfono con ADB/scrcpy y elegir una fuente real: SMS o WhatsApp. Acordar DTO y justificaciones con B. Preparar dependencia Room con la plantilla compatible. Trabajar desde `develop` y abrir PR a `develop`; ver [entorno y Git](../docs/ENTORNO_Y_GIT.md).

## A-01 · Captura de una fuente real · 30 min

Implementar el listener y obtener el evento sin hacer red dentro del callback.

**Dependencia:** arranque.

- [ ] Recibir una notificación con Freno fuera del primer plano.
- [ ] Obtener origen, fecha y texto disponible; ignorar notificaciones propias.
- [ ] No escribir mensajes completos en logs.

## A-02 · Alerta visual y acceso al motivo · 40 min

Publicar una notificación de alta importancia que abre la advertencia al tocarla. Reutilizar la tarjeta de explicación en alerta y detalle del historial.

**Dependencia:** arranque; usar datos simulados inicialmente.

- [ ] HIGH muestra «Pausa. Puede ser una estafa», motivo breve, recomendación y ENTENDIDO.
- [ ] REVIEW usa aviso discreto; LOW queda en historial sin interrumpir; UNKNOWN no se presenta como seguro.
- [ ] ENTENDIDO cierra; ninguna acción abre enlaces sospechosos.
- [ ] No hay voz. El overlay y la alarma sonora no son necesarios para completar esta feature.

## A-03 · Historial persistente y detalle · 75 min

Implementar Room, repositorio local, lista cronológica y vista de detalle. Es la pantalla principal al abrir Freno, aunque el análisis normalmente trabaje en segundo plano.

**Dependencias:** DTO acordados; puede empezar con resultados simulados.

- [ ] Cada evento conserva fecha, origen, fragmento redactado, nivel de riesgo y estado del análisis.
- [ ] El detalle muestra «¿Por qué se marcó?», reasonSimple, señal representada por reasonCode y recomendación.
- [ ] Distinguir explicación de Gemini, coincidencia de Google Safe Browsing, texto de respaldo y análisis no disponible; si se usa la señal de Google, mostrar `Advisory provided by Google` con su enlace oficial.
- [ ] Los registros sobreviven a cerrar/reabrir la app y se pueden consultar sin internet.
- [ ] Guardar hasta 100 eventos, actualizar el mismo eventId y permitir borrar historial.
- [ ] Abrir un registro no consulta otra vez a Gemini ni vuelve a emitir alertas.
- [ ] Un estado ANALYZING interrumpido por muerte del proceso se muestra como análisis incompleto al reabrir.

## A-04 · Estado de protección y ajustes mínimos · 20 min

Mostrar estado y pausa en la cabecera del historial; agrupar permisos y consentimiento en un panel simple.

**Dependencia:** A-01; puede usar la pantalla principal provisional.

- [ ] Explicar envío del texto minimizado a Gemini y conservación del fragmento/justificación en el teléfono.
- [ ] Mostrar accesos reales y permitir pausar; pausar no borra automáticamente registros.
- [ ] Ofrecer borrado del historial; correo, Telegram y sonido no se requieren para activar P0.

## A-05 · Extracción, duplicados y coordinación · 40 min

Conectar captura, historial, `RiskAnalyzer` y presentación. Usar `FakeRiskAnalyzer` al principio; minimizar datos y descartar resúmenes de grupo.

**Dependencias:** A-01, A-02 y contrato de historial A-03.

- [ ] Crear registro ANALYZING antes de solicitar análisis y actualizarlo con el resultado.
- [ ] Descartar duplicados por paquete, clave y hash durante 60 segundos; texto distinto puede procesarse.
- [ ] Marcar texto insuficiente/truncado; no tratarlo como LOW por defecto.
- [ ] Mantener un análisis y una alerta activos; cola de cinco eventos y degradación visible ante saturación.

## A-06 · Backend real y conservación del resultado · 40 min

Conectar `POST /v1/analyze` y guardar la respuesta validada con su explicación, modelo y versión del prompt.

**Dependencias:** A-05, B-01 y B-02; ensayo conjunto con B-05.

- [ ] Notificación real → Gemini y, si hay URL, Safe Browsing → alerta visual → registro consultable.
- [ ] Extraer como máximo tres URLs HTTP(S) antes de redactar el texto para Gemini y enviarlas en el campo separado `urls`; si hay más, marcar `contentIncomplete=true`. Nunca abrirlas desde Freno.
- [ ] La justificación del historial coincide con la usada para esa decisión; no se regenera al abrirla.
- [ ] eventId identifica el registro correcto; errores producen UNKNOWN con causa comprensible.
- [ ] El circuito funciona sin credenciales de correo ni Telegram.

## A-07 · Fallos, persistencia y accesibilidad · 25 min

Cerrar los errores principales antes de congelar funciones; dedicar la hora de pruebas a verificar más casos.

**Dependencias:** A-02, A-03, A-04 y A-06.

- [ ] Retirar permisos o perder red no provoca crash ni falsea el estado de protección.
- [ ] Fuente al 200 % mantiene lectura y controles accesibles.
- [ ] Cerrar/reabrir conserva la justificación; borrar historial elimina las filas visibles y persistidas.
- [ ] Pantalla bloqueada muestra un aviso genérico sin el fragmento privado.
- [ ] Si falla el guardado, la alerta visual sigue funcionando y se informa que el evento no pudo registrarse.

## Orden, hitos y entrega

Orden recomendado: **A-01 → A-02 → A-03 → A-05 → A-06 → A-04 → A-07**.

Hora 1:30: captura probada y alerta en desarrollo. Hora 3: lista y detalle con registros simulados persistentes. Hora 4: integración real en curso. Hora 5: circuito completo con historial y configuración mínima.

De 5:00 a 6:00, probar permisos, duplicados, persistencia, borrado y red con B. De 6:00 a 7:00, generar APK y operar el teléfono durante dos ensayos; B prepara guion y grabación. Reservar la última hora para fallos.

**Opcionales:** alarma sonora breve y configurable; overlay si queda tiempo y el teléfono lo permite. Para correo, A dedica unos 30 min adicionales a cliente/consentimiento/estados; B implementa el envío. Telegram siempre después del correo. No quitar historial de P0 para añadir estos extras.

Diseño y campos: [Interfaz e historial](../docs/INTERFAZ_E_HISTORIAL.md). Referencias: [Arquitectura](../docs/ARQUITECTURA.md) y [Tareas](../docs/TAREAS.md).
