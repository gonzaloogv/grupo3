# Interfaz de Freno e historial de justificaciones

**Alcance obligatorio para 2 programadores y 8 horas.** El análisis se activa con notificaciones en segundo plano; al abrir la app, la persona puede revisar qué ocurrió y por qué se marcó cada mensaje. No hay voz ni lectura en voz alta. Una alarma sonora breve se considera opcional.

## 1. Pantalla principal: estado e historial

La pantalla inicial contiene una cabecera de protección, acceso a ajustes y una lista de eventos, del más reciente al más antiguo. Mostrar hasta 100 registros; no construir gráficos, estadísticas, buscador ni filtros avanzados en P0.

Ejemplo conceptual:

```text
FRENO                              Ajustes
Análisis activo                    [Pausar]
Acceso a notificaciones habilitado

Historial
Hoy · 14:32 · SMS
ALTO RIESGO · Posible suplantación familiar
«Soy tu hijo, cambié de número. Transferime…»
Pidió dinero urgente y dijo que cambió de número.
[Ver motivo]

Hoy · 14:20 · WhatsApp
SIN SEÑALES CLARAS
«Mañana paso a tomar mate…»
No se detectaron señales claras en el texto recibido.
[Ver detalle]
```

Es un ejemplo de diseño: para la demo se implementa una sola fuente real, aunque la estructura permita ambas.

Cada fila muestra fecha/hora, app de origen, etiqueta de riesgo, fragmento redactado y resumen de justificación. El color acompaña una etiqueta escrita; no es la única señal.

Estados mínimos:

- Sin registros: «Todavía no se analizaron notificaciones».
- ANALYZING: «Analizando mensaje».
- HIGH: «Alto riesgo · Posible estafa».
- REVIEW: «Conviene revisar».
- LOW: «Sin señales claras»; no «Mensaje seguro».
- UNKNOWN: «No se pudo analizar», con causa comprensible.
- Protección pausada o permisos faltantes: banner visible; el historial sigue disponible.

Solo se registran eventos capturados y procesados por Freno. No es el historial completo de WhatsApp/SMS ni prueba de que se hayan examinado todos los mensajes.

## 2. Detalle: por qué se marcó

Al abrir un registro mostrar:

1. Fecha/hora, origen, categoría y riesgo.
2. Fragmento del texto analizado, con identificadores sensibles sustituidos y aviso si está recortado.
3. **«¿Por qué se marcó?»**: explicación guardada para ese evento.
4. **Señal detectada:** etiqueta humana del reasonCode, por ejemplo «Cambio de número y pedido urgente de dinero».
5. **Qué conviene hacer:** recomendación fija correspondiente a action.
6. Origen de la explicación: «Análisis de Gemini», «Texto de respaldo de Freno» o «Análisis no disponible».

Ejemplo:

> **Posible suplantación familiar**
>
> El mensaje dice que tu familiar cambió de número y pide dinero urgente.
>
> **Qué hacer:** antes de transferir, llamá al número que ya conocés.

Para LOW o UNKNOWN, cambiar la pregunta por «Resultado del análisis». Un timeout debe explicar que no pudo analizarse; no atribuirle una decisión de fraude a Gemini.

No mostrar una cadena de razonamiento interna ni un porcentaje de confianza inventado. Se conserva una justificación breve basada en señales del texto. No afirmar verificación de identidad, titularidad de dominio o bloqueo efectivo.

## 3. Alerta visual

Cuando se obtiene HIGH, emitir una notificación que abre la advertencia visual y el motivo. Reutilizar los componentes del detalle para que alerta e historial presenten la misma información.

Texto: «Pausa. Puede ser una estafa», explicación breve, recomendación y ENTENDIDO. Cerrar la advertencia no borra el registro ni cambia su clasificación. Consultar el historial tampoco emite otra alerta.

El overlay es opcional y depende de permisos/dispositivo; la demo base puede mostrar el toque para abrir la advertencia. No se promete apertura automática a pantalla completa.

**Alarma posible:** si se incorpora, usar un sonido breve configurable para HIGH, respetando volumen y silencio/No molestar. Sin reproducción de voz, sin repetir al consultar historial y sin requerir permisos de alarma exacta. La app debe seguir siendo útil sin sonido.

## 4. Qué se guarda localmente

| Campo propuesto | Uso |
| --- | --- |
| eventId | Identificador único para actualizar una fila sin duplicarla |
| capturedAt / analyzedAt | Momento de captura y del resultado; analyzedAt puede ser null |
| source | SMS o WhatsApp; no exige número o identidad del remitente |
| redactedPreview / previewTruncated | Fragmento de hasta 300 caracteres del texto minimizado; indicar recorte |
| contentIncomplete | El contenido disponible para análisis era incompleto |
| analysisStatus | ANALYZING, COMPLETED o FAILED; separado del nivel de riesgo |
| risk / category | Clasificación y tipo de señal; risk null mientras analiza |
| reasonCode / reasonSimple | Señal y justificación breve validadas; no reconstruirlas al abrir |
| action | Recomendación de la política local |
| analyzer / model / promptVersion | Procedencia de la decisión; ocultar versiones en la UI normal |
| explanationSource | GEMINI, TEMPLATE o UNAVAILABLE para rotular la explicación con honestidad |

El servidor proporciona la clasificación y su explicación; Android añade metadatos locales y persiste con Room. La caché del servidor sigue siendo temporal y no reemplaza el historial del teléfono.

Mantener los 100 registros más recientes, sin guardar mensajes completos, cuentas, códigos o números sin redactar. Descartar texto original tras procesarlo. Explicar el almacenamiento al activar la app y ofrecer «Borrar historial». Datos solo locales, sin sincronización ni exportación en este MVP; excluir esta base del backup automático del prototipo.

## 5. Persistencia y errores

- Insertar ANALYZING antes de consultar Gemini; actualizar esa fila al terminar.
- Una respuesta válida, incluso UNKNOWN por falta de contexto, queda COMPLETED; un fallo técnico queda FAILED y risk UNKNOWN.
- Si el proceso muere durante el análisis, al reiniciar marcar esos pendientes como incompletos; no dejarlos eternamente «Analizando».
- Reabrir o consultar sin internet conserva el motivo original sin otra llamada a Gemini.
- Borrar historial no borra mensajes de la app de origen. No reinsertar registros borrados al terminar solicitudes previas: cancelar los análisis pendientes o ignorar esas respuestas.
- Un error de almacenamiento no debe impedir una advertencia urgente; informar que no pudo guardarse.

## 6. Reparto y aceptación

**A:** Room, repositorio, lista, detalle, ajustes y alerta visual. **B:** explicación consistente, reasonCode, origen de explicación, versión del prompt y casos de evaluación.

La demo debe mostrar mensaje sospechoso → alerta → historial → detalle con motivo → cerrar/reabrir y conservar el registro. También mostrar un mensaje cotidiano y un análisis fallido con etiquetas diferentes. Comprobar que abrir historial no vuelve a analizar ni dispara sonido.

Referencia de persistencia: [Room en Android](https://developer.android.com/training/data-storage/room).