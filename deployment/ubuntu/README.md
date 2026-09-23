# Freno en Ubuntu para pruebas privadas

El backend se ejecuta en un proyecto Docker Compose llamado `freno`. No utiliza
las carpetas, redes ni contenedores de MU-RCIA. La API se publica en el puerto
8081, enlazado únicamente a la IP elegida; por defecto, solo a localhost.

Este despliegue utiliza una distribución compilada del backend, por lo que no
requiere clonar el repositorio en Ubuntu. El código se compila en la PC de
desarrollo y se transfiere el paquete al servidor. Un `git push` a `develop`
actualiza el repositorio, pero no reemplaza la versión que ejecuta Docker;
para eso hay que desplegar un nuevo paquete como se indica en «Operación».

## Archivos del servidor

En `/home/muadmin/freno`:

- `compose.yaml`: copiar el archivo de esta carpeta.
- `.env`: configuración de Compose, por ejemplo `FRENO_BIND_IP=100.125.16.53`.
- `.env.backend`: copia privada de `backend/.env`, con permisos `0600`.
- `releases/<commit>/server-0.1.0-SNAPSHOT/`: distribución extraída del backend.
- `app`: enlace simbólico a la distribución de la versión activa.

El usuario del contenedor tiene UID y GID 1000, correspondientes a `muadmin` en
este servidor. Comprobar `id` antes de reutilizar esta configuración en otro host.
La imagen contiene Java 21; Ubuntu no necesita Java ni Gradle instalados.
El contenedor puede usar hasta 512 MiB de memoria y una CPU.

## Preparación y arranque

Generar el paquete desde `backend/` con `./gradlew :server:distTar` (en Windows,
usar `gradlew.bat`). Transferir el TAR, Compose y la configuración privada por
SSH. Extraer el TAR dentro de `releases/<commit>` y crear `app` apuntando a
`releases/<commit>/server-0.1.0-SNAPSHOT`.

Ejecutar en Ubuntu:

```bash
cd /home/muadmin/freno
chmod 600 .env .env.backend
docker compose config --quiet
docker compose up -d
docker compose ps
curl --fail http://100.125.16.53:8081/health
```

El puerto y host internos del servidor se fijan en Compose; las claves se leen
del archivo privado montado en modo lectura. No se incluyen claves de Google
en la imagen, el paquete ni la APK.

## APK y teléfono de prueba

Compilar la app con `freno.api.baseUrl=http://100.125.16.53:8081` en el
`local.properties` privado de Android. `freno.api.token` debe coincidir con
`DEMO_API_TOKEN` del backend. Usar esta APK solamente para la prueba privada:
contiene el token de la demo y requiere Android 10 o posterior.

El teléfono necesita Tailscale activo y acceso al equipo `murcia`. Para un amigo,
compartir ese equipo con su propia cuenta desde la consola de Tailscale; verificar
que la política permita acceder al puerto 8081. Abrir primero
`http://100.125.16.53:8081/health` en el navegador del teléfono. Después instalar
la APK y habilitar los accesos a notificaciones y alertas de Freno.

Probar un mensaje cotidiano y la URL oficial de prueba de Safe Browsing
`https://testsafebrowsing.appspot.com/s/phishing.html` enviada como texto, sin
abrir el enlace. El historial debe mostrar respectivamente "Sin señales claras"
y "Riesgo alto" cuando las consultas completan correctamente.

Ubuntu debe permanecer encendido, sin suspensión y con conexión a Internet.
Después de reiniciar Ubuntu, comprobar `docker compose ps` y `/health`: el
contenedor requiere que la IP de Tailscale esté disponible para publicar el puerto.
No se necesita abrir puertos en el router. La PC Windows no participa en las
consultas de la APK una vez desplegado el backend.

## Operación

Desde `/home/muadmin/freno`:

```bash
docker compose logs --tail 50 backend
docker compose restart backend
docker compose stop backend
```

Estos comandos afectan únicamente al proyecto `freno`. Para actualizar, conservar
la versión anterior, extraer el nuevo paquete en otra carpeta de `releases`,
cambiar el enlace `app` y ejecutar `docker compose up -d --force-recreate backend`.
Para volver atrás, restaurar el enlace anterior y recrear solo ese servicio.
