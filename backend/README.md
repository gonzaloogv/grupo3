# Backend de Freno

Esqueleto del frente B: contrato compartido, API Ktor, Gemini, reputación local
con el feed de PhishTank, caché y evaluación. La implementación funcional se
realiza en ramas separadas por feature y se integra mediante PR a `develop`.

## Convenciones base

- Kotlin/JVM con Gradle Wrapper y dos módulos: `shared` y `server`.
- Horario regional: `America/Argentina/Buenos_Aires` (UTC-3). Se usa una zona
  IANA en vez de un offset fijo para mantener fechas y formatos correctos.
- Secretos solo en `backend/.env`, ignorado por Git. El archivo
  `backend/.env.example` documenta las variables sin valores sensibles.
- Gemini permanece detrás del servidor; ninguna clave se distribuye en la APK.
- PhishTank se consulta mediante una copia local de su feed verificado y activo.
  Una coincidencia exacta aporta evidencia; no encontrar una URL nunca significa
  que el mensaje sea seguro.
- Los datos descargados de PhishTank no se versionan ni reciben texto del usuario.

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
│       │   │   │   └── phishtank/
│       │   │   ├── observability/  # Logs sin contenido sensible
│       │   │   └── plugins/        # Configuración Ktor
│       │   └── resources/
│       └── test/kotlin/ar/com/freno/server/
├── prompts/                        # Prompts versionados
├── data/
│   ├── corpus/{development,reserved}/
│   ├── evaluation/
│   └── phishtank/                  # Feed local ignorado por Git
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
[`docs/API_CONTRACT.md`](docs/API_CONTRACT.md). Hasta integrar B-02, el servidor
responde con un analizador determinístico identificado como `FAKE`; no representa
una llamada real a Gemini.
