# Contrato HTTP B-01

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

Respuesta temporal de B-01:

```json
{
  "eventId": "demo-001",
  "risk": "HIGH",
  "category": "FAMILY_IMPERSONATION",
  "reasonCode": "NEW_NUMBER_AND_URGENT_PAYMENT",
  "reasonSimple": "El mensaje dice que tu familiar cambió de número y pide dinero urgente.",
  "action": "VERIFY_KNOWN_CONTACT",
  "analyzer": "FAKE",
  "model": null,
  "promptVersion": "freno-v1",
  "explanationSource": "TEMPLATE",
  "decisionSources": ["LOCAL_POLICY"],
  "urlAssessment": {
    "status": "NO_URL",
    "provider": "NONE",
    "threatTypes": []
  }
}
```

Esta respuesta determinística sirve solamente para integrar Android y comprobar
el contrato. `analyzer=FAKE` evita presentarla como una decisión real. B-02
reemplazará este adaptador por Gemini y conservará los mismos DTO. Si se
envían URLs durante el modo de prueba, la evaluación queda `UNAVAILABLE`;
`FAKE` no representa una consulta real a Google Safe Browsing.

`urls` admite hasta tres URLs HTTP(S) absolutas con host. Se rechaza la
solicitud con `400 Bad Request` si excede ese límite o contiene otro esquema.
Puede omitirse durante la transición del cliente; equivale a `[]`. El servidor
no abre las URLs en este paso.

## Valores cerrados

- `source`: `SMS`, `WHATSAPP`.
- `risk`: `HIGH`, `REVIEW`, `LOW`, `UNKNOWN`.
- `category`: `FAMILY_IMPERSONATION`, `BANK_PHISHING`, `CODE_REQUEST`,
  `OTHER`, `NONE`, `UNKNOWN`.
- `reasonCode`: `NEW_NUMBER_AND_URGENT_PAYMENT`, `CREDENTIAL_REQUEST`,
  `CODE_SHARING_REQUEST`, `INSUFFICIENT_CONTEXT`, `NO_CLEAR_SIGNAL`,
  `OTHER_SIGNAL`, `ANALYSIS_UNAVAILABLE`.
  También `URL_LISTED_AS_THREAT` para una futura coincidencia de reputación.
- `action`: `VERIFY_KNOWN_CONTACT`, `AVOID_LINK_AND_VERIFY`,
  `DO_NOT_SHARE_CODE`, `NONE`.
- `analyzer`: `GEMINI`, `UNAVAILABLE`, `FAKE`.
- `explanationSource`: `GEMINI`, `TEMPLATE`, `UNAVAILABLE`.
- `decisionSources`: lista no vacía de `GEMINI`, `GOOGLE_SAFE_BROWSING`,
  `LOCAL_POLICY`.
- `urlAssessment.status`: `NO_URL`, `MATCH`, `NO_MATCH`, `UNAVAILABLE`.
- `urlAssessment.provider`: `NONE`, `GOOGLE_SAFE_BROWSING`.
- `urlAssessment.threatTypes`: `SOCIAL_ENGINEERING`, `MALWARE`.
