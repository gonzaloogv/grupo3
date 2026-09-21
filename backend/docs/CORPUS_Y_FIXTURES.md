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

- **Dominios `.example`**: Todos los enlaces incluidos utilizan dominios reservados sintéticos (ej. `banco-alerta.example`, `portal-seguro.example`). Al menos dos casos por partición incluyen enlaces sintéticos explícitos.
- **Sin números de 6+ dígitos**: Ningún texto contiene secuencias numéricas continuas de 6 dígitos o más (`Regex("\\b\\d{6,}\\b")`), previniendo la exposición accidental de tokens OTP reales en logs o datos de prueba.

## 3. Fixtures Deterministas de Reputación

El archivo `backend/data/fixtures/url-reputation.json` proporciona respuestas deterministas para simular todos los estados del proveedor de Safe Browsing:

- `match-social-engineering`: estado `MATCH`, lista `SOCIAL_ENGINEERING`.
- `match-malware`: estado `MATCH`, lista `MALWARE`.
- `no-match-url`: estado `NO_MATCH`, URL consultada sin coincidencias reportadas.
- `unavailable-url`: estado `UNAVAILABLE`, simula caída técnica o límite de servicio.
- `no-url`: estado `NO_URL`, proveedor `NONE`, lista vacía.

## 4. Validación Automatizada

La coherencia del corpus, el balance y la compatibilidad con los contratos se verifican mediante `CorpusFixtureTest`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test --tests ar.com.freno.server.data.CorpusFixtureTest
```
