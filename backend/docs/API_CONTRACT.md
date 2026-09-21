# Contrato HTTP

Base local de desarrollo: `http://127.0.0.1:8080` después de ejecutar
`adb reverse tcp:8080 tcp:8080` para el teléfono de demo.

## Autenticación

`GET /health` es público. `POST /v1/analyze` requiere:

```http
Authorization: Bearer <DEMO_API_TOKEN>
Content-Type: application/json
```

El token se configura únicamente en el servidor y no se devuelve en ninguna
respuesta. Un token ausente o incorrecto produce `401 Unauthorized`.

## GET /health

Respuesta `200 OK`:

```json
{
  "status": "ok",
  "timeZone": "America/Argentina/Buenos_Aires"
}
```

No expone claves, tokens ni configuración de proveedores.

## POST /v1/analyze

Solicitud:

```json
{
  "eventId": "demo-001",
  "source": "SMS",
  "text": "Soy tu hijo, cambié de número. Transferime urgente al alias [ALIAS].",
  "contentIncomplete": false,
  "urls": [],
  "locale": "es-AR"
}
```

Respuesta cuando Gemini clasifica el mensaje:

```json
{
  "eventId": "demo-001",
  "risk": "HIGH",
  "category": "FAMILY_IMPERSONATION",
  "reasonCode": "NEW_NUMBER_AND_URGENT_PAYMENT",
  "reasonSimple": "El mensaje dice que tu familiar cambió de número y pide dinero urgente.",
  "action": "VERIFY_KNOWN_CONTACT",
  "analyzer": "GEMINI",
  "model": "gemini-3.5-flash-lite",
  "promptVersion": "freno-v1",
  "explanationSource": "GEMINI",
  "decisionSources": ["GEMINI"],
  "urlAssessment": {
    "status": "NO_URL",
    "provider": "NONE",
    "threatTypes": []
  }
}
```

El motivo de `GEMINI` se valida y se redactan identificadores reconocibles
antes de enviarlos y antes de devolver la explicación. Si el motivo contiene
un enlace, está vacío o supera 25 palabras, el servidor usa una explicación
local y marca `explanationSource=TEMPLATE`. Una respuesta semánticamente
incompatible, un fallo del proveedor o el vencimiento de `GEMINI_TIMEOUT_MS` produce
`risk=UNKNOWN`, `category=UNKNOWN`, `reasonCode=ANALYSIS_UNAVAILABLE`,
`action=NONE`, `analyzer=UNAVAILABLE`, `model=null`,
`explanationSource=UNAVAILABLE` y `decisionSources=["LOCAL_POLICY"]`.
No se presenta como una evaluación de seguridad realizada por Gemini.

`urls` admite hasta tres URLs HTTP(S) absolutas con host. Se rechaza la
solicitud con `400 Bad Request` si excede ese límite o contiene otro esquema.
Puede omitirse; equivale a `[]`. El servidor no abre las URLs: las consulta en
Safe Browsing al mismo tiempo que Gemini analiza el texto.

## Límites y Caché de Análisis

- **Tamaño del cuerpo**: El cuerpo JSON de la solicitud no puede superar los 8 KB (8.192 bytes). Peticiones que superen este tamaño se rechazan con `413 Payload Too Large`.
- **Longitud del texto**: `text` no puede superar los 2.000 caracteres. Peticiones con textos mayores se rechazan con `400 Bad Request`.
- **Caché de resultados**: El servidor retiene hasta 100 resultados de análisis durante 15 minutos (`ANALYSIS_CACHE_MAX_ENTRIES` y `ANALYSIS_CACHE_TTL_MINUTES`).
- **Reutilización por `eventId`**: Consultas repetidas con el mismo `eventId` y mismo texto devuelven la respuesta en caché sin invocar nuevamente a Gemini ni a Safe Browsing.
- **Detección de colisión**: Si se recibe una petición con un `eventId` activo pero con texto distinto, el servidor rechaza la solicitud con `409 Conflict` para evitar devolver clasificaciones ajenas o permitir suplantaciones de identificadores.

Ejemplo con una URL:

```json
{
  "eventId": "demo-url",
  "source": "SMS",
  "text": "Tu cuenta será suspendida. Entrá al enlace para verificarla.",
  "contentIncomplete": false,
  "urls": ["https://banco.example/ingresar"],
  "locale": "es-AR"
}
```

La fusión aplica estas reglas:

- `MATCH` de `MALWARE` o `SOCIAL_ENGINEERING` fuerza `HIGH`, `URL_THREAT`,
  `URL_LISTED_AS_THREAT` y `AVOID_LINK_AND_VERIFY`, incluso si Gemini falla.
- `NO_MATCH` solo significa «no reportada» y nunca reduce el riesgo de Gemini.
- Con URL, `UNAVAILABLE` convierte un `LOW` textual en `UNKNOWN`; conserva
  `HIGH` o `REVIEW`.
- `contentIncomplete=true` también convierte un `LOW` en `UNKNOWN`.
- Un resultado incompatible, como `HIGH` con `action=NONE`, se degrada a
  `UNKNOWN` y no ordena una intervención.

El analizador determinístico `FAKE` solo se usa en pruebas del servidor;
nunca debe mostrarse como una clasificación real al usuario.

## Valores cerrados

- `source`: `SMS`, `WHATSAPP`.
- `risk`: `HIGH`, `REVIEW`, `LOW`, `UNKNOWN`.
- `category`: `FAMILY_IMPERSONATION`, `BANK_PHISHING`, `CODE_REQUEST`,
  `URL_THREAT`, `OTHER`, `NONE`, `UNKNOWN`.
- `reasonCode`: `NEW_NUMBER_AND_URGENT_PAYMENT`, `CREDENTIAL_REQUEST`,
  `CODE_SHARING_REQUEST`, `INSUFFICIENT_CONTEXT`, `NO_CLEAR_SIGNAL`,
  `OTHER_SIGNAL`, `ANALYSIS_UNAVAILABLE`, `URL_LISTED_AS_THREAT`.
- `action`: `VERIFY_KNOWN_CONTACT`, `AVOID_LINK_AND_VERIFY`,
  `DO_NOT_SHARE_CODE`, `NONE`.
- `analyzer`: `GEMINI`, `UNAVAILABLE`, `FAKE`.
- `explanationSource`: `GEMINI`, `TEMPLATE`, `UNAVAILABLE`.
- `decisionSources`: lista no vacía de `GEMINI`, `GOOGLE_SAFE_BROWSING`,
  `LOCAL_POLICY`.
- `urlAssessment.status`: `NO_URL`, `MATCH`, `NO_MATCH`, `UNAVAILABLE`.
- `urlAssessment.provider`: `NONE`, `GOOGLE_SAFE_BROWSING`.
- `urlAssessment.threatTypes`: `SOCIAL_ENGINEERING`, `MALWARE`.
