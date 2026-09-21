# Freno: plan de producto y hackathon

Plan actualizado para **2 programadores y 8 horas**. A asume Android, interfaz e historial; B asume backend, Gemini, justificaciones y evaluación. Voz descartada; alarma sonora opcional. Las estimaciones son presupuestos de trabajo, no tiempos ya medidos.

## 1. La propuesta mejorada

**Una pausa comprensible antes de una decisión riesgosa.** Freno detecta señales de engaño en el texto disponible de una notificación y ayuda a verificar el mensaje con una persona de confianza.

El MVP conecta detección, advertencia visual y explicación consultable. Aunque el análisis opere en segundo plano, la app tiene una interfaz principal con historial y detalle del motivo de cada clasificación. Ayuda familiar es una extensión: correo primero y Telegram después, solo con tiempo restante.

Usuarios:

- **Persona protegida:** recibe una advertencia breve y conserva el control de su teléfono.
- **Persona de confianza:** ayuda con la instalación; puede recibir avisos si se implementa el extra y ambas partes lo acuerdan.

La configuración inicial es asistida y consentida. Después, analiza notificaciones sin abrirla manualmente. El aviso abre la alerta visual al tocarlo; overlay y alarma quedan opcionales. Al abrir Freno normalmente se muestra el historial, el estado de protección y la opción de pausa.

## 2. Qué demostrar

Historia principal: llega un mensaje que suplanta a un familiar, dice que cambió de número y solicita una transferencia urgente. Freno presenta una advertencia, recomienda verificar por un canal conocido y guarda el motivo. Después, la persona abre el historial para consultar por qué se marcó. Si se completa correo, también avisa al contacto configurado.

Historia secundaria: llega un mensaje bancario que exige ingresar datos mediante un enlace. Se reutiliza exactamente el mismo flujo.

Control negativo: llega un mensaje cotidiano. El teléfono continúa sin una intervención de Freno.

**Resultado esperado del MVP:** detección → alerta visual → historial persistente con justificación. El aviso externo no condiciona la entrega. No afirmar que se evitó una pérdida real o que se bloqueó al remitente.

## 3. Alcance y prioridades

| Prioridad | Incluye | Criterio de alcance |
| --- | --- | --- |
| P0 | Una fuente real de notificaciones de texto | Elegir SMS o WhatsApp según el teléfono disponible; validar una primero |
| P0 | Clasificación con Gemini | HIGH, REVIEW, LOW; UNKNOWN para fallos o contenido insuficiente |
| P0 | Advertencia visual | Motivo, recomendación y ENTENDIDO; apertura desde notificación |
| P0 | Historial persistente y detalle | Pantalla principal con registros y justificación; consulta offline y borrado |
| P0 | Ajustes mínimos | Consentimiento, accesos, estado y pausa desde la pantalla principal |
| P0 | Respuesta ante errores | Sin conexión, permisos retirados y texto oculto sin fingir protección completa |
| P0 | Demo repetible | 12 casos sintéticos, un dispositivo y una fuente; ensayo y video de respaldo |
| P1 · primer extra | Aviso familiar por correo | Solo con P0 completo y tiempo disponible; dirección de demo preconfigurada y resumen sin mensaje original |
| Opcional | Alarma sonora breve y overlay | Solo con P0 estable; el historial tiene prioridad |
| Posterior | Segunda fuente y botón de llamada | Fuera del compromiso de 8 horas para dos personas |
| Posterior | Vibración personalizada e inyector debug con UI | No dedicar tiempo obligatorio a estas mejoras |
| P2 | Aviso por Telegram | Solo después de implementar y probar correo, si todavía sobra tiempo |
| P2 | Llamadas, reputación de números, audio | Investigación posterior; fuera de esta hackathon de 8 horas |
| P2 | Modelo local entrenado, panel web, iOS | Sin implementación durante el MVP |

Tampoco habrá registro de usuarios, múltiples familiares, pagos, OCR, lectura de chats abiertos ni bloqueo de enlaces en otras apps. No se implementará voz ni TextToSpeech.

## 4. Experiencia propuesta

### Configuración inicial

1. Explicar qué texto se enviará a Gemini y qué fragmento/justificación se conservará localmente.
2. Activar acceso a notificaciones; superposición solo si se implementa ese extra.
3. Habilitar los avisos de Freno y probar la alerta visual.
4. Si se implementó el extra de correo, verificar el destinatario de demo y activar voluntariamente el aviso familiar; omitir este paso en P0.
5. Abrir la pantalla principal de historial con «Listo para analizar notificaciones» o «Revisar permisos/conexión».

El destinatario del correo opcional se configura en el servidor. P0 no requiere destinatario ni credenciales de envío. No construir emparejamiento por QR ni selección de contactos.

### Alerta de riesgo alto

Texto propuesto:

> **Pausa. Puede ser una estafa.**
>
> Este mensaje pide dinero y dice que tu familiar cambió de número.
>
> Antes de transferir, llamá al número que ya conocés.
>
> **ENTENDIDO**

Fondo de alerta con contraste alto, título e icono: el color nunca será la única señal. Texto grande, sin parpadeos ni cuenta regresiva. Solo interfaz visual en P0. Si se incorpora alarma sonora, será breve, configurable y respetará ajustes del sistema; no habrá lectura del mensaje.

ENTENDIDO cierra la advertencia y conserva el registro. No abre el enlace sospechoso ni significa «caso resuelto». Evitar frases alarmistas como «te están robando» cuando solo existe una clasificación probabilística.

### Pantalla principal y detalle del historial

Al abrir la app: cabecera de protección/pausa y lista cronológica de hasta 100 eventos persistidos con Room. Cada fila muestra fecha, fuente, fragmento redactado, riesgo y resumen del motivo. Tocar un registro abre «¿Por qué se marcó?», la señal detectada, explicación guardada y recomendación.

Ejemplo de motivo: «El mensaje dice que tu familiar cambió de número y pide dinero urgente». Debe corresponder al texto analizado, sin afirmar identidad verificada. Distinguir explicación de Gemini, respaldo de Freno y fallo técnico; UNKNOWN nunca se presenta como una estafa detectada.

El historial sobrevive a cerrar/reabrir, funciona offline y permite borrado. Consultarlo no vuelve a ejecutar Gemini ni dispara alarmas. Ver [diseño de interfaz e historial](INTERFAZ_E_HISTORIAL.md).

### Otros resultados

| Resultado | Respuesta de Freno |
| --- | --- |
| HIGH | Alerta visual y registro con motivo; alarma/correo solo si esos extras están implementados y habilitados |
| REVIEW | Aviso discreto e historial: «Conviene verificar este mensaje»; sin aviso familiar automático |
| LOW | Historial sin interrupción; «Sin señales claras», no garantía de seguridad |
| UNKNOWN | Historial con causa de falta de análisis; aviso técnico agrupado si falla el servicio |

### Mensaje familiar opcional · Correo antes que Telegram

«Freno detectó señales de posible suplantación familiar a las 14:32. Se intentó advertir en el teléfono. Conviene llamar al número que ya conocés.»

Asunto del correo: «Freno: mensaje sospechoso detectado». No adjuntar el texto original ni enlaces. El teléfono solo muestra «Aviso enviado» cuando el servidor confirma aceptación del servicio de envío; eso no garantiza llegada a la bandeja de entrada ni lectura. Telegram queda como segundo canal opcional, posterior al correo.

## 5. Decisiones que hacen viable el proyecto

- **Kotlin en Android y servidor.** Un backend pequeño con Ktor concentra Gemini; los adaptadores de correo y Telegram se agregan solo si sobra tiempo. La APK no lleva secretos de proveedores.
- **Una app y un servicio pequeño.** Room solo en el teléfono para historial; backend sin base de datos, microservicios ni colas externas.
- **IA con salida estructurada.** La lógica de la app decide cómo intervenir; el modelo no ejecuta acciones ni abre enlaces.
- **Filtrado conservador.** Eliminar notificaciones propias, duplicadas o sin texto; analizar todos los mensajes elegibles de la fuente seleccionada. No depender de palabras clave para decidir qué llega a Gemini.
- **Dispositivo elegido desde el inicio.** Probar captura y persistencia reales. Overlay solo con tiempo restante y prueba máxima de 20 minutos; la base abre la alerta desde notificación.
- **Un contacto fijo para el extra.** Destinatario de correo configurado manualmente; configuración multiusuario queda para después.

El modelo oficial es `gemini-3.5-flash-lite` y admite salida estructurada. La primera prueba debe comprobar acceso y cuota en el proyecto del equipo; esta planificación no ejecutó una solicitud autenticada. [Documentación de Google](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite).

## 6. Cronograma paralelo de 8 horas

| Tiempo | A · Android, interfaz e historial | B · Backend, Gemini y evaluación | Hito compartido |
| --- | --- | --- | --- |
| 0:00–0:30 | Crear app, preparar teléfono y fuente | Crear servidor y acordar DTO | App, health y contrato inicial |
| 0:30–1:30 | Capturar notificación e iniciar pantalla de alerta | API y primera clasificación real | Captura y Gemini probados por separado |
| 1:30–3:00 | Room, lista y detalle con datos simulados | Errores, 12 casos y caché | Historial persistente con justificaciones de prueba |
| 3:00–4:00 | Extracción, coordinador e inicio de cliente real | Preparar contrato real y evaluación inicial | Pipeline de captura/historial; integración real en curso |
| 4:00–5:00 | Terminar cliente, ajustes y manejo de fallos | Ensayo conjunto, motivo persistido y entorno reproducible | Circuito completo con historial; congelar P0 |
| 5:00–6:00 | Permisos, duplicados, persistencia, borrado y texto grande | Lote reservado, coherencia de motivos y fallos de API | Resultados anotados y fallos críticos corregidos |
| 6:00–7:00 | APK candidata y operación del teléfono | Guion, grabación y apoyo al ensayo | Dos demos seguidas y video de respaldo |
| 7:00–8:00 | Buffer y apoyo | Buffer y apoyo | Entrega final |

Son **16 horas-persona**: 1 de arranque, 9 de implementación, 2 de validación, 2 de preparación y 2 de buffer. Cada persona tiene 270 minutos de features obligatorias. Se elimina la voz y se reserva un bloque de 75 minutos de A para historial, persistencia y detalle; overlay sale del trabajo obligatorio.

Correo y Telegram no tienen un bloque obligatorio. Si P0 se completa antes o queda buffer libre, priorizar correo (45–60 min de B y 30 min de A, con servicio de envío disponible). No recortar pruebas ni ensayo. Telegram solo se evalúa después de probar el correo.

## 7. Recortes previstos

- **Si se intenta overlay y supera 20 minutos:** conservar notificación que abre la alerta visual. Mostrar ese toque en la demo; no afirmar apertura automática.
- **Si no hay fuente real disponible:** usar una entrada de prueba desde debug para verificar el circuito, sin construir un simulador con UI. Identificar la simulación y marcar captura real como pendiente.
- **Si Gemini no está disponible:** usar respuestas de prueba para integrar y mostrar claramente «modo demo». No sustituir el modelo elegido en silencio ni afirmar que hubo análisis en vivo.
- **Si no sobra tiempo:** entregar alerta visual e historial con motivos; alarma, correo y Telegram quedan pendientes.
- **Si Android queda sobrecargado:** conservar lista/detalle y ajustes mínimos; descartar gráficos, filtros, sonido y overlay. El historial solicitado sigue en P0. B prepara casos y material de demo.

Para 24 horas: completar P0 primero, sumar persistencia de avisos pendientes, segunda fuente y pruebas en dos versiones de Android. Para 48 horas: añadir emparejamiento familiar, evaluación con más casos y validación de usabilidad consentida. No incorporar análisis de llamadas antes de estabilizar los mensajes.

## 8. Qué habría que validar después

La utilidad real se evalúa con personas mayores: comprensión de la explicación, capacidad de cerrar o pedir ayuda y nivel tolerable de interrupciones. La detección se evalúa con mensajes variados y etiquetas revisadas, separando falsos positivos y falsos negativos.

Medir consumo, latencia, disponibilidad y funcionamiento con pantalla bloqueada antes de prometer cifras. El documento inicial mencionaba RAM, batería y tiempos de inferencia que todavía no están verificados. Entrenar un modelo local es una línea de investigación, no una garantía de rendimiento o precisión.
