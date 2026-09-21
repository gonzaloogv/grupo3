# Corpus Sintético y Fixtures de Reputación

Documentación de los conjuntos de datos y fixtures deterministas creados en la tarea **B-05** para la evaluación y prueba de Freno Backend.

## 1. Estructura y Balance del Corpus

El corpus cuenta con un total de **12 casos sintéticos**, organizados en dos particiones balanceadas de 6 casos cada una:

- `backend/data/corpus/development/cases.json`: partición para integración con cliente y calibración.
- `backend/data/corpus/reserved/cases.json`: partición reservada para la prueba final a ciegas y validación reproducible.

### Balance de escenarios

| Tipo de Escenario | Total | Desarrollo | Reservado | Detalle de familias |
|---|---|---|---|---|
| `DECEPTIVE` | 6 | 3 | 3 | 2 `BANK`, 2 `FAMILY`, 2 `CODE` (1 de cada uno por partición) |
| `ROUTINE` | 4 | 2 | 2 | Notificaciones habituales (`family: NONE`) |
| `AMBIGUOUS` | 2 | 1 | 1 | Casos con incertidumbre o incompletos (`family: NONE`) |
| **Total** | **12** | **6** | **6** | Identificadores únicos (`dev-*`, `res-*`) |

## 2. Enlaces Sintéticos y Reglas de Seguridad

- **Dominios `.example`**: Todos los enlaces incluidos utilizan dominios reservados sintéticos (ej. `banco-alerta.example`, `portal-seguro.example`). El corpus contiene cuatro mensajes con enlaces sintéticos en total (uno en desarrollo y tres en reservado), cumpliendo y superando el requisito de al menos dos mensajes con enlaces en total.
- **Sin números de 6+ dígitos**: Ningún texto contiene secuencias numéricas continuas de 6 dígitos o más (`Regex("\\b\\d{6,}\\b")`), previniendo la exposición accidental de tokens OTP reales en logs o datos de prueba.

## 3. Fixtures Deterministas y Política de Fusión

El archivo `backend/data/fixtures/url-reputation.json` proporciona respuestas deterministas para simular todos los estados del proveedor de Safe Browsing:

- `match-social-engineering`: estado `MATCH`, lista `SOCIAL_ENGINEERING`.
- `match-malware`: estado `MATCH`, lista `MALWARE`.
- `no-match-url`: estado `NO_MATCH`, URL consultada sin coincidencias reportadas.
- `unavailable-url`: estado `UNAVAILABLE`, simula caída técnica o límite de servicio.
- `no-url`: estado `NO_URL`, proveedor `NONE`, lista vacía.

### Política de Fusión Conservadora en Resultados Esperados
- **Prevalencia de `MATCH`**: Cuando un mensaje con enlace coincide con una amenaza en Safe Browsing (`MATCH`), la política conservadora en `ConservativeRiskAnalyzer` fuerza `category = URL_THREAT`, `reasonCode = URL_LISTED_AS_THREAT` y `action = AVOID_LINK_AND_VERIFY`, prevaleciendo sobre la categorización textual previa (por ejemplo, `BANK_PHISHING`).
- **Comportamiento con `UNAVAILABLE`**: Cuando la reputación resulta `UNAVAILABLE`, un análisis textual `LOW` se degrada conservadoramente a `UNKNOWN` con `ANALYSIS_UNAVAILABLE`.
- **Inocuidad de `NO_MATCH`**: Un resultado `NO_MATCH` nunca reduce el nivel de riesgo determinado para el texto.

## 4. Validación Automatizada

La coherencia del corpus, el balance, los dominios sintéticos y la ejecución completa de cada caso a través del coordinador `ConservativeRiskAnalyzer` con un proveedor simulado de reputación se verifican mediante `CorpusFixtureTest`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :server:test --tests ar.com.freno.server.data.CorpusFixtureTest
```
