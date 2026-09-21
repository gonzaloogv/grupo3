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
antes de enviarlos y antes de devolver la explicación. Una respuesta inválida,
un fallo del proveedor o el vencimiento de `GEMINI_TIMEOUT_MS` produce
`risk=UNKNOWN`, `category=UNKNOWN`, `reasonCode=ANALYSIS_UNAVAILABLE`,
`action=NONE`, `analyzer=UNAVAILABLE`, `model=null`,
`explanationSource=UNAVAILABLE` y `decisionSources=["LOCAL_POLICY"]`.
No se presenta como una evaluación de seguridad realizada por Gemini.

`urls` admite hasta tres URLs HTTP(S) absolutas con host. Se rechaza la
solicitud con `400 Bad Request` si excede ese límite o contiene otro esquema.
Puede omitirse durante la transición del cliente; equivale a `[]`. El servidor
no abre las URLs en este paso.

Ejemplo con una URL antes de incorporar Google Safe Browsing:

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

La respuesta mantiene la clasificación textual de Gemini, pero usa
`"urlAssessment":{"status":"UNAVAILABLE","provider":"NONE","threatTypes":[]}`.
Esto indica que aún no hubo una consulta real de reputación.

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
