# Flujo de Git y pruebas en Android

Decisiones del equipo: integrar los trabajos en `develop` antes de pasarlos a `main`, y ejecutar la app en un teléfono Android físico conectado a la PC, con scrcpy para visualizarlo y controlarlo.

## Ramas e integración

```text
android ─────┐
ai-backend ──┴── PR → develop ── PR → main
```

- `main`: versión estable, validada para la demo.
- `develop`: integración de los dos frentes: A (Android, interfaz e historial) y B (backend, Gemini y evaluación).
- Ramas de trabajo: nacen de `develop` y usan nombres sin prefijos; los nombres del diagrama son ejemplos. Preferir cambios pequeños por tarea.
- Cada PR de trabajo apunta a `develop` y lo revisa otro integrante. Integrar en los hitos de [TAREAS.md](TAREAS.md).
- El PR de `develop` a `main` se realiza cuando la compilación y el circuito completo funcionan en el teléfono de la demo.
- Antes de integrar, actualizar la rama con `origin/develop`, resolver conflictos y volver a verificar lo afectado. Coordinar cambios de contratos, Gradle y Manifest.

Ejemplo en una copia de trabajo sin cambios pendientes, para una rama nueva:

```powershell
git fetch origin
git switch -c android origin/develop
# Implementar y hacer commits de la tarea.
git push -u origin android
```

En GitHub, abrir el PR con **base: develop** y **compare: android**. Para entregar la versión estable, usar **base: main** y **compare: develop**.

Este documento define el acuerdo de trabajo. Las protecciones de ramas y los controles automáticos de GitHub deben configurarse por separado; no se consideran habilitados por documentar el flujo.

## Teléfono Android y scrcpy

La app corre en el teléfono. scrcpy refleja su pantalla y permite controlarlo desde la computadora; el entorno de pruebas será un dispositivo físico. La compilación de Kotlin sigue necesitando JDK, Android SDK y Gradle, aunque el editor sea VS Code. [Documentación de scrcpy](https://github.com/Genymobile/scrcpy).

Preparación inicial:

1. Instalar scrcpy desde su repositorio oficial y disponer de ADB (Android SDK Platform-Tools).
2. Habilitar las opciones de desarrollador y la depuración USB en el teléfono.
3. Conectar un cable USB de datos, desbloquear el teléfono y aceptar la autorización de depuración.
4. En Windows, instalar el controlador USB del fabricante si el equipo lo requiere.
5. Comprobar que ADB muestra el teléfono con estado `device` y abrir scrcpy.

Con ambos ejecutables disponibles en `PATH`:

```powershell
adb devices
scrcpy --max-size=1280 --max-fps=30 --no-audio
```

`--no-audio` desactiva la retransmisión de audio a la PC; la demo no incluye voz. Si se incorpora alarma opcional, comprobarla en el teléfono. Si se usan ejecutables portables desde su carpeta en PowerShell, invocarlos como `./adb.exe` y `./scrcpy.exe`. Si aparece `unauthorized`, aceptar la autorización en el teléfono. [Configuración oficial de dispositivos Android](https://developer.android.com/studio/run/device).

Para el backend local propuesto, si escucha en el puerto `8080` de la PC:

```powershell
adb reverse tcp:8080 tcp:8080
```

La app de desarrollo podrá apuntar a `http://127.0.0.1:8080` usando ese túnel. Ajustar ambos puertos al servidor real, volver a comprobar el túnel después de reconectar el teléfono y permitir HTTP únicamente para ese destino en la configuración debug. Ver [ARQUITECTURA.md](ARQUITECTURA.md).

## Backend local y emulador

Completar las claves en `backend/.env` y asignar un `DEMO_API_TOKEN` propio.
Desde la raíz del proyecto, iniciar el servidor con:

```powershell
.\tools\run-backend.ps1
```

El servidor queda activo mientras ese proceso siga abierto. Su estado se consulta
en `http://127.0.0.1:8080/health`. Para probar únicamente desde esta PC, usar
`SERVER_HOST=127.0.0.1` en `backend/.env`.

Agregar en el `local.properties` de la raíz, conservando `sdk.dir`:

```properties
freno.api.baseUrl=http://10.0.2.2:8080
freno.api.token=EL_MISMO_DEMO_API_TOKEN_DEL_BACKEND
```

`10.0.2.2` permite al emulador acceder al servidor de esta PC. Ambos archivos de
configuración local están excluidos de Git. Las claves de Gemini y Safe Browsing
se guardan únicamente en el backend; la app usa el token de acceso de la demo.
Tras cambiar estas propiedades, sincronizar Gradle y volver a ejecutar `app`.

Para probar la captura, habilitar **Leer notificaciones** en Freno y generar un
SMS desde los controles del emulador (**More > Phone > Send Message**). El
servidor debe estar activo para obtener la evaluación de riesgo.

## Criterio para pasar de develop a main

- APK compilada desde la versión integrada e instalada en el teléfono de la demo.
- Acceso a notificaciones y permisos del flujo habilitados y comprobados.
- Notificación de prueba → análisis → alerta visual → historial con justificación; registrar si se usa apertura manual u overlay opcional.
- Cerrar/reabrir conserva registros y motivos; consulta offline y borrado funcionan sin volver a llamar a Gemini.
- Botón de cierre y comportamiento sin red o sin permisos comprobados.
- Resultado registrado en el PR: modelo del teléfono, versión de Android y casos probados.

La preparación del teléfono y la comprobación con scrcpy forman parte de F-00. Estas instrucciones no implican que el entorno ya esté instalado o que las pruebas se hayan ejecutado.
