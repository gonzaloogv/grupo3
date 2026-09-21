# Google Safe Browsing

El backend usa la Lookup API v4 mediante
`POST https://safebrowsing.googleapis.com/v4/threatMatches:find`. Envía solo
las URLs recibidas en `AnalysisRequest.urls`; no abre ni descarga su contenido.
Consulta las listas `MALWARE` y `SOCIAL_ENGINEERING` para `ANY_PLATFORM`.

## Semántica

- `NO_URL`: no se llamó al proveedor.
- `MATCH`: Google devolvió al menos una amenaza admitida.
- `NO_MATCH`: Google no devolvió una coincidencia; significa «no reportada»,
  nunca «segura».
- `UNAVAILABLE`: timeout, límite de cuota, error HTTP o respuesta inválida.

Las coincidencias positivas se conservan como máximo durante el
`cacheDuration` de cada respuesta. Las respuestas vacías de v4 no incluyen un
plazo negativo, por lo que este adaptador no inventa uno.

## Prueba autenticada

El 21 de septiembre de 2026 se realizó una única consulta con la clave local y
la URL de prueba `http://testsafebrowsing.appspot.com/apiv4/ANY_PLATFORM/MALWARE/URL/`
publicada por el cliente de referencia de Google. Resultado observado:
HTTP exitoso, una coincidencia `MALWARE`, `cacheDuration` presente y 309 ms de
tiempo total. La clave no se imprimió ni se versionó.

Esta medición confirma configuración y formato en ese momento; no garantiza
latencia ni disponibilidad futuras. Safe Browsing se ofrece para uso no
comercial. Para detección comercial, Google indica usar Web Risk.

- [Lookup API v4](https://developers.google.com/safe-browsing/v4/lookup-api)
- [Reglas de caché](https://developers.google.com/safe-browsing/v4/caching)
- [Uso apropiado](https://developers.google.com/safe-browsing/reference/Appropriate.Usage)
- [Cliente de referencia y URLs de prueba](https://github.com/google/safebrowsing)
