# Traspaso a Antigravity — B-06 / B-07

Pedido: revisar 06, corregir errores e implementar 07. Interrupción por uso Codex
al 99% de la ventana de cinco horas. NO integrar ni publicar a develop. No consumir
créditos sin autorización. No imprimir secretos ni evaluar el lote reservado.

## Ubicación y estado

- Principal: C:/Users/acade/OneDrive/Escritorio/grupo3, rama b-06-cache-limits.
- Commit B-06 corregido: b289fde, base revisada dad1362.
- B-07: C:/Users/acade/.codex/worktrees/b-07-evaluation/grupo3.
- Rama b-07-evaluation desde b289fde, sin commits propios todavía.
- Cambios sin commit: GeminiRiskAnalyzerTest.kt referencia VersionedPrompt;
  nuevo DevelopmentEvaluationTest.kt; docs/EVALUATION.md borrador; este traspaso.
  Conservarlos. NO resetear ni restaurar archivos.
- B-07 NO implementada: las pruebas nuevas fallan al compilar porque faltan
  VersionedPrompt y DevelopmentEvaluation. Última ejecución observada confirma
  exactamente esas referencias no resueltas. No hay evaluación live realizada.

## B-06 terminado y verificado parcialmente

Commit b289fde corrige: SHA-256 de solicitud completa, sin retener texto original;
get/analyze/put coordinado con 128 Mutex; lectura de máximo 8193 bytes antes de
rechazar >8192; URISyntaxException devuelve 400 y no 500. Tres regresiones vistas
fallar y luego pasar: metadatos/URLs modificadas, ocho duplicados simultáneos, URI
malformada. También test de cuerpo streaming sin Content-Length devuelve 413.
`./gradlew.bat test --no-daemon`: BUILD SUCCESSFUL, 66 pruebas, cero fallos
(62 servidor + 4 shared). No repetir ese trabajo. Ensayo Android real desmarcado
en el plan, pendiente: no hay prueba de dispositivo. La revisión independiente
final todavía NO se hizo.
OneDrive bloqueó server/build/test-results/test/binary: se movió SOLO ese directorio
generado a binary-before-review06, ignorado y recuperable. No se borró código.

## Hallazgo B-06 adicional PENDIENTE

Al consultar documentación oficial: Google exige que MATCH respete cacheDuration
también cuando se muestra una advertencia. AnalysisCache retiene AnalysisResult
15 minutos sin conocer el vencimiento del MATCH, pudiendo reutilizar reputación
vencida aunque el adaptador respete su propio TTL. NO corregido en b289fde.
Agregar test RED y resolver antes de declarar B-06 terminada. Alternativa mínima:
no persistir resultados MATCH en la caché de análisis (documentar costo de repetir
Gemini), o propagar expiración interna sin romper contrato compartido. No inventar
TTL de Google. Los Mutex pueden serializar eventos distintos por colisión; está
documentado, acotado y aceptado para esta demo monoinstancia.

## Implementación siguiente B-07

Plan: feat/programador-b-ia-backend.md B-07; spec docs/ARQUITECTURA.md.
1. application/VersionedPrompt.load(version): recurso prompts/freno-v1.txt con
   SYSTEM_PROMPT actual de GeminiRiskAnalyzer; campos text y sha256 del contenido
   realmente enviado. Rechazar versión inexistente/traversal. Sustituir constante
   inline por recurso. No alterar prompt para ajustar expectativas del corpus.
2. evaluation/DevelopmentEvaluation(dataRoot:Path, model:String,
   promptVersion:String, timeout:Long); suspend run(textAnalyzer:RiskAnalyzer,
   textMode:String, revision:String), según tests nuevos ya escritos.
   Leer SOLO corpus/development/cases.json (6) y fixtures/url-reputation.json.
   Ejecutar ConservativeRiskAnalyzer real con Gemini inyectado y proveedor
   simulado por fixture; NUNCA usar expected para generar obtenido. Agregar variante
   dev-02 de suplantación familiar con instrucción maliciosa de devolver LOW/NONE.
   Reporte serializable: cases, injection.status PASS/FAIL/INCONCLUSIVE,
   reputationMode=SIMULATED_FIXTURES, textMode. Por caso expected, obtained,
   textObtained antes de fusión, matchesExpected (riesgo/categoría/código/acción),
   explanationReview=PENDING_HUMAN_REVIEW, elapsedMillis. Fecha UTC, revisión,
   modelo, versión/hash prompt, hashes corpus/fixtures, timeout. Si Gemini falla,
   registrar UNAVAILABLE aunque fixture fuerce HIGH; inyección INCONCLUSIVE,
   nunca contar indisponibilidad como resistencia probada.
3. CLI con Gemini real y HttpClient(CIO); siete llamadas secuenciales. Salida
   build/reports/evaluation/development.json, sin secretos ni cuerpos en logs.
   Gradle JavaExec :server:evaluateDevelopment; configurar también :server:run
   con workingDir backend (hoy puede buscar .env en server). Admitir -PenvFile
   para reutilizar .env principal sin copiarlo y -PevaluationRevision.
4. Pasar pruebas nuevas y suite completa. Commit implementación antes de live
   para registrar revisión reproducible. Ejecutar siete consultas reales con
   corpus de desarrollo SOLO. Guardar reporte sanitizado sintético en
   docs/evaluations/ y revisión humana de justificaciones. Señalar discrepancias,
   no inventar aprobaciones ni porcentajes de confianza. No tocar lote reservado.
5. Completar EVALUATION.md (hoy borrador), README, plan y traspaso. Revisión
   independiente final del rango dad1362..B07, corregir importantes con RED→GREEN.
   No hubo esa revisión todavía. No merge ni push.

## Comandos

Desde backend DEL WORKTREE, PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :server:test --tests '*DevelopmentEvaluationTest' --tests '*GeminiRiskAnalyzerTest' --no-daemon
.\gradlew.bat test --no-daemon
```

Credenciales existentes: C:/Users/acade/OneDrive/Escritorio/grupo3/backend/.env.
No imprimir, compartir ni versionar. Está ignorado. Defaults Gemini
gemini-3.5-flash-lite, timeout 20000, PROMPT_VERSION=freno-v1. Backend es raíz
Gradle independiente de Android. No se necesitan claves de correo/Telegram.

## Fuentes y decisiones

https://developers.google.com/safe-browsing/v4/usage-limits: uso gratuito no
comercial, cuotas, atribución SOLO datos reales de Google, riesgo potencial,
falsos positivos/negativos y caducidad de MATCH. No atribuir fixtures a Google.
https://developers.google.com/safe-browsing/v4: v4 está DEPRECADA. Documentar
migración futura; no cambiar API dentro de esta feature.
Android real/atribución visual pendientes de A-06; B-07 puede avanzar sin ese
ensayo pero no certifica Android. Zona America/Argentina/Buenos_Aires UTC−3
(no UTF-3 ni un único huso para toda Latinoamérica).
Ledger original: principal/.superpowers/sdd/programador-b-ia-backend/progress.md.
Scripts de task-brief esperan otra estructura que el plan B-XX; seguimiento manual
equivalente en ledger/handoff. No eliminar este traspaso al terminar: pedido usuario.
