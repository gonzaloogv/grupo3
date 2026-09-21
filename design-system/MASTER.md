# Freno — Design System

Sistema visual para una app Android que protege a personas mayores frente a estafas. Su interfaz no debe parecer una app financiera ni una pantalla técnica: debe sentirse como una intervención humana, inmediata y tranquilizadora.

## Principios

1. **Entender antes que explorar.** En una alerta, una frase clara y una sola acción visible.
2. **El peligro no depende solo del rojo.** Cada estado crítico combina color, icono de escudo, título explícito, sonido/voz y texto.
3. **Grande, calmo y sin jerga.** Tipografía amplia, frases breves y espacio generoso.
4. **Toda decisión es trazable y reversible.** Cada bloqueo muestra motivo y momento; confiar o desconfiar de un evento puede deshacerse.
5. **La animación informa, no entretiene.** Nada parpadea, pulsa sin parar ni demora una acción crítica.

## Foundations

### Color tokens

| Token | Valor | Uso |
| --- | --- | --- |
| `color/danger` | `#B42318` | Fondo de alerta crítica, icono de amenaza |
| `color/on-danger` | `#FFFFFF` | Texto e iconos sobre alerta crítica |
| `color/danger-soft` | `#FFF1F0` | Fondo de tarjetas de riesgo |
| `color/safety-blue` | `#1D4ED8` | Acciones de contactar a la familia |
| `color/on-safety-blue` | `#FFFFFF` | Texto sobre acción azul |
| `color/ink` | `#162033` | Texto principal y navegación |
| `color/ink-muted` | `#526176` | Metadatos y explicaciones secundarias |
| `color/surface` | `#FFFFFF` | Tarjetas y pantallas estándar |
| `color/canvas` | `#F6F8FB` | Fondo de la app de familia |
| `color/border` | `#D9E0EA` | Separadores y contornos |
| `color/safe` | `#16794A` | Estado protegido, nunca como única señal |
| `color/focus` | `#F59E0B` | Foco accesible de alto contraste |

No usar gradientes, neón, sombras duras ni rojo para acciones que no sean de peligro.

### Typography

Usar **Roboto** (Android nativo), con soporte de Dynamic Type / escalado de fuente. Números en Roboto Mono solo en IDs u horarios.

| Estilo | Tamaño / interlineado | Peso | Uso |
| --- | --- | --- | --- |
| `Display alert` | 40 / 48 sp | 700 | “POSIBLE ESTAFA” |
| `Headline` | 28 / 36 sp | 700 | Títulos de sección |
| `Title` | 22 / 28 sp | 700 | Tarjetas y resumen |
| `Body large` | 20 / 30 sp | 400 | Instrucciones para el adulto mayor |
| `Body` | 16 / 24 sp | 400 | Contexto y listados |
| `Label` | 14 / 20 sp | 700 | Etiquetas y chips |

No usar texto menor de 16 sp en la experiencia del adulto mayor. Evitar mayúsculas sostenidas salvo la etiqueta de peligro.

### Spacing, shape & elevation

- Escala: `4, 8, 12, 16, 24, 32, 40, 48, 64` dp.
- Márgenes laterales de alerta: 24 dp; respetar las safe areas.
- Radio: 16 dp para tarjetas, 20 dp para contenedores grandes, 14 dp para botones. No usar pills salvo chips de estado.
- Elevación: usar bordes y contraste antes que sombras. Solo la tarjeta de evidencia puede tener sombra suave `0 8 24 rgba(22,32,51,0.14)`.
- Objetivos táctiles: mínimo 48 × 48 dp; botón “ENTENDIDO” de 72 dp de alto y ancho completo.

### Iconografía

Material Symbols Rounded, trazo de 2 px visuales, 24 dp en UI normal y 40–48 dp en alerta. Usar `shield`, `warning`, `phone`, `message`, `family_restroom`, `check_circle`; nunca emojis ni iconos decorativos ambiguos.

## Components

### AlertOverlay / Critical

- Ocupa todo el viewport, incluyendo la jerarquía por encima de cualquier app.
- Fondo `color/danger`; icono de escudo de 48 dp; etiqueta “ALERTA DE SEGURIDAD”.
- Título: “POSIBLE ESTAFA”.
- Explicación en lenguaje directo: “Este mensaje parece intentar robarte dinero o datos.”
- Tarjeta blanca de evidencia con el remitente y un extracto de hasta dos líneas; los enlaces se muestran como texto inactivo y nunca son tocables.
- Instrucción: “No toques enlaces. No compartas códigos ni dinero. Llamá a tu familia.”
- Un único CTA: `ENTENDIDO`, blanco, texto `color/danger`, con icono check. Al tocarlo baja la alerta pero conserva el evento en historial.

### NotificationEventCard

- Tarjeta sobre fondo blanco de 20 dp de radio y borde de 1 dp.
- Cabecera: estado escrito `BLOQUEADA` o `CONFIADA`, acompañado por icono y color.
- Contenido: origen, remitente, fragmento redactado, fecha y explicación breve.
- Acción reversible de ancho completo: `Confiar` o `Desconfiar`.
- Confiar afecta solo al evento visible; no crea una excepción permanente para el remitente.

### ProtectionSummary

- Primer bloque de la pantalla inicial.
- Número de bloqueos en 48 sp y etiqueta explícita “notificaciones bloqueadas”.
- Texto breve que explica qué hizo Freno, sin gráficos ni métricas técnicas.

### PermissionStatus

- Dos accesos visibles: lectura de notificaciones y alertas sobre otras apps.
- Cada fila muestra icono, nombre, explicación y estado real.
- Si falta un acceso, el CTA `Permitir` abre la pantalla correcta de ajustes del sistema.

### ProtectionStatus

- Estado normal: icono de escudo con check, “Freno está protegiendo a Rosa”.
- Estado sin permisos/red: icono de información y explicación accionable; no esconder el problema.

### Buttons

- `Primary / acknowledgement`: superficie blanca sobre danger; 72 dp de alto; texto 20 sp, 700.
- `Secondary / family`: azul, 56 dp de alto; texto blanco de 16 sp, 700.
- En presión, escala a `0.97` durante 120–160 ms sin cambiar layout. Focus ring de 3 dp en `color/focus`.

## Screen patterns

1. **Alerta del adulto mayor:** prioridad absoluta; no menú, no botón atrás visible, no links activos ni puntuaciones técnicas.
2. **Inicio protegido:** resumen de cantidad, pestañas Bloqueadas/Confiadas y eventos con motivo y fecha.
3. **Confianza reversible:** cada evento cambia de lista sin desaparecer del historial ni habilitar futuros mensajes.
4. **Permisos del sistema:** apartado dentro del inicio con estado completo/incompleto y lenguaje no técnico.

## Motion & accessibility

- Alerta: aparición de opacidad `0 → 1` y escala `0.98 → 1` en 160 ms, `cubic-bezier(0.23, 1, 0.32, 1)`. La voz y vibración se disparan inmediatamente; la animación nunca las retrasa.
- Botones: 120–160 ms; tarjetas y sheets: 180–220 ms. Sin loops, rebotes o confeti.
- Con “reducir movimiento”: solo fundido de 150 ms, sin escala ni desplazamiento.
- Contraste mínimo AA 4.5:1; jerarquía comprensible sin color; orden de lector de pantalla: severidad → título → explicación → evidencia → instrucción → CTA.
- La alerta anuncia mediante live region: “Alerta de seguridad. Posible estafa. No toque enlaces.”

## Anti-patterns

- No dashboards densos, gráficos ni vistas separadas para familiares.
- No tarjetas de vidrio, gradientes, tipografía condensada ni fondos con textura.
- No copy técnico: “phishing”, “typosquatting”, “score 0.93” o URLs largas como foco principal.
- No más de un botón prominente en la alerta.
