# Backend de Freno

Frente B: contrato compartido, API Ktor y clasificación con Gemini. La consulta
de reputación de URLs con Google Safe Browsing, la caché y la evaluación se
implementan en ramas posteriores y se integran a `develop` por feature.

## Convenciones base

- Kotlin/JVM con Gradle Wrapper y dos módulos: `shared` y `server`.
- Horario regional: `America/Argentina/Buenos_Aires` (UTC-3). Se usa una zona
  IANA en vez de un offset fijo para mantener fechas y formatos correctos.
- Secretos solo en `backend/.env`, ignorado por Git. El archivo
  `backend/.env.example` documenta las variables sin valores sensibles.
- Gemini permanece detrás del servidor; ninguna clave se distribuye en la APK.
- Google Safe Browsing se usará solo para URLs, en el servidor. Una ausencia de
  coincidencia no equivale a declarar seguro el mensaje.

## Estructura

```text
backend/
├── shared/                         # DTO y enums Kotlin sin Android
│   └── src/{main,test}/kotlin/ar/com/freno/shared/contract/
├── server/
│   └── src/
│       ├── main/
│       │   ├── kotlin/ar/com/freno/server/
│       │   │   ├── api/            # Rutas y mapeo HTTP
│       │   │   ├── application/    # Casos de uso
│       │   │   ├── config/         # Variables, reloj y zona horaria
│       │   │   ├── domain/         # Política y validación semántica
│       │   │   ├── infrastructure/
│       │   │   │   ├── cache/
│       │   │   │   ├── gemini/
│       │   │   │   └── safe_browsing/
│       │   │   ├── observability/  # Logs sin contenido sensible
│       │   │   └── plugins/        # Configuración Ktor
│       │   └── resources/
│       └── test/kotlin/ar/com/freno/server/
├── prompts/                        # Prompts versionados
├── data/
│   ├── corpus/{development,reserved}/
│   ├── evaluation/
├── docs/                           # Contrato y resultados reproducibles
└── scripts/                        # Arranque, actualización y evaluación
```

## Flujo de ramas previsto

Cada rama nace del último `develop`, contiene una sola feature y vuelve por PR:

1. `b-01-api-contract`
2. `b-02-gemini`
3. `b-03-error-handling`
4. `b-04-corpus`
5. `b-05-android-integration`
6. `b-06-cache-limits`
7. `b-07-evaluation`

Antes de iniciar una rama: actualizar `develop`, comprobar que el árbol esté
limpio y no modificar el contrato compartido sin coordinar con Android.

## Contrato HTTP

El contrato inicial, los encabezados y ejemplos de B-01 están documentados en
[`docs/API_CONTRACT.md`](docs/API_CONTRACT.md). El servidor usa Gemini para
clasificar el texto. Si falla o excede el plazo, devuelve `UNKNOWN` con
`analyzer=UNAVAILABLE`; no atribuye esa respuesta a Gemini.

## Arranque local

Copiar `.env.example` a `backend/.env`, completar `GEMINI_API_KEY` y
`DEMO_API_TOKEN` y ejecutar `./gradlew :server:run` desde `backend/`.
`SAFE_BROWSING_API_KEY` se utilizará en la feature posterior de URLs.
El plazo para Gemini se controla con `GEMINI_TIMEOUT_MS`; para la demo se fijó
en 20 segundos tras observar respuestas variables, una de ellas de 13,2 s.
Esto prioriza obtener una clasificación y puede superar el objetivo original
de 7 segundos para mostrar la alerta. El cliente debe contemplar esa espera.
