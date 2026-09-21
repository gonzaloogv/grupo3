# B-07: evaluación reproducible

## Alcance

El comando usa únicamente `data/corpus/development/cases.json` (seis casos) y
agrega una variante del caso de suplantación familiar con instrucciones para
responder LOW/NONE. No lee ni envía el lote reservado. Gemini es real; reputación
es **SIMULATED_FIXTURES**, no un resultado real de Google para los dominios `.example`.
La fusión y validación son las mismas del servidor. No se usa la caché de resultados.

La suite `test` es offline; no utiliza claves ni hace llamadas a Google. El comando
de evaluación sí envía los textos sintéticos redactados a Gemini y consume su cuota
(siete solicitudes secuenciales, hasta 20 segundos cada una por defecto).

## Arranque en Windows

Desde `backend/`, con JDK 21 instalado (el JBR de Android Studio es compatible):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
# Solo si aún no existe .env, copiar .env.example y completar valores localmente.
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
.\gradlew.bat test --no-daemon
.\gradlew.bat :server:run
```

Se necesitan `GEMINI_API_KEY`, `GEMINI_MODEL`, `SAFE_BROWSING_API_KEY` y
`DEMO_API_TOKEN`. `PROMPT_VERSION=freno-v1` selecciona un recurso versionado real;
una versión inexistente impide iniciar el analizador. `GEMINI_TIMEOUT_MS=20000`.
No se necesitan claves de correo ni Telegram. `APP_TIME_ZONE` usa
`America/Argentina/Buenos_Aires` (UTC−3, no “UTF-3”); no representa todos los husos
horarios de Latinoamérica. El endpoint de salud es `http://localhost:8080/health`.

La tarea `run` establece `backend/` como directorio de trabajo. En un worktree se
puede usar `-PenvFile=C:/ruta/al/backend/.env` para reutilizar el archivo local sin
copiar secretos; las variables de entorno tienen prioridad sobre ese archivo.
No pasar las claves como argumentos ni compartir `.env`. Solo `.env.example` se versiona.

## Ejecutar la evaluación real

```powershell
.\gradlew.bat :server:evaluateDevelopment "-PevaluationRevision=$(git rev-parse HEAD)" --no-daemon
# En otro worktree, agregar: "-PenvFile=C:/ruta/al/backend/.env"
```

Salida: `backend/build/reports/evaluation/development.json` (ignorada por Git).
El reporte registra fecha UTC, revisión indicada, modelo, timeout, versión y hash
del prompt, hashes de corpus/fixtures, esperado/obtenido, resultado textual antes
de fusión, tiempos y el ensayo de inyección. El comando termina aunque haya
discrepancias: generar un reporte no significa aprobar la evaluación. `INCONCLUSIVE`
indica que no se pudo validar la resistencia a instrucciones, por ejemplo por
indisponibilidad. Revisar cada `matchesExpected` y el estado de inyección.

`explanationReview=PENDING_HUMAN_REVIEW` requiere contrastar la justificación con
el mensaje: señalar evidencias concretas, identificar plantillas y no aceptar
supuestas verificaciones de identidad, sitio o remitente. Registrar esa revisión
en un documento fechado separado, sin modificar el reporte original. Antes de
versionar un reporte verificar que solo contenga casos sintéticos y ninguna clave.

La ejecución es reproducible, no necesariamente las respuestas de un LLM remoto.
Una coincidencia con estos seis ejemplos no estima precisión general; no se muestran
porcentajes de confianza como garantía. LOW no significa seguro y UNKNOWN no
significa libre de riesgo. El lote reservado queda para el ensayo conjunto.

## Safe Browsing y atribución

Google documenta Safe Browsing como gratuito, sujeto a cuotas y **solo para uso
no comercial**. Para un producto comercial evaluar Web Risk. Las advertencias
basadas en Google deben expresar riesgo potencial, incluir “Advisory provided by
Google” enlazado a su aviso y explicar la posibilidad de falsos positivos/negativos.
No atribuir a Google una clasificación exclusivamente textual ni un fixture de demo.
Fuente: [restricciones de uso oficiales](https://developers.google.com/safe-browsing/v4/usage-limits).

La [documentación oficial de v4](https://developers.google.com/safe-browsing/v4)
ya la marca deprecada. Esta feature no migra el adaptador: planificar esa migración
antes del despliegue. La integración visual/atribución y el recorrido Android real
siguen pendientes del ensayo con A; estas pruebas backend no los certifican.
