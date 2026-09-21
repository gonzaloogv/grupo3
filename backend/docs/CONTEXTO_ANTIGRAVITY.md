# Continuación B-06 / B-07

Solicitud: revisar B-06, corregir errores e implementar B-07. No integrar a develop ni publicar sin nueva indicación; mantener ramas por feature.

Estado inicial: rama b-06-cache-limits, commit dad1362, checkout principal C:/Users/acade/OneDrive/Escritorio/grupo3 limpio. B-05 está incluida mediante 8da6f11. develop remoto conocido: 5569c5b.

Revisión B-06 corregida: SHA-256 de solicitud completa sin retener texto original, exclusión por evento con 128 bloqueos acotados, lectura de máximo 8.193 bytes y URI malformada devuelve 400. Tres regresiones observadas fallar primero y luego pasar; agregada cobertura de cuerpo sin Content-Length. Ensayo Android marcado pendiente, no se ejecutó en dispositivo.

B-07 pendiente: runner reproducible sobre SOLO seis casos development, Gemini real + reputación simulada claramente rotulada; prompt versionado como recurso único, reporte esperado/obtenido y revisión de explicaciones, prueba de inyección, documentación de arranque/atribución/limitaciones. No ejecutar lote reservado ni ajustar prompt a sus resultados. No mostrar ni versionar .env.

Verificación B-06: desde backend, JAVA_HOME=C:/Program Files/Android/Android Studio/jbr; ./gradlew.bat test --no-daemon: BUILD SUCCESSFUL, 66 pruebas (62 servidor + 4 shared), cero fallos. El directorio generado test-results/test/binary bloqueado por OneDrive se movió a binary-before-review06 (recuperable, ignorado); no se eliminó código ni datos.

Actualizar este archivo al completar cada etapa con commits, pruebas, pendientes y rutas. No repetir trabajo ya registrado como completo.
