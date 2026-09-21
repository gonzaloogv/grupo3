$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = Join-Path $PSScriptRoot 'jdk17-extracted\jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $PSScriptRoot 'android-sdk'
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot 'gradle-home'
$socketDirectory = Join-Path (Split-Path $projectRoot -Parent) 'tmp'
$androidUserDirectory = Join-Path $PSScriptRoot 'android-user-home'
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $androidUserDirectory | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="' + $socketDirectory + '" -Duser.home="' + $androidUserDirectory + '"'
$env:ANDROID_USER_HOME = $androidUserDirectory
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Push-Location $projectRoot
try {
    & .\gradlew.bat --no-daemon --console=plain assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'La compilación falló; no se instaló ninguna APK.' }
    $adb = Join-Path $PSScriptRoot 'scrcpy-win64-v4.1\adb.exe'
    & $adb -d install -r (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la app en el teléfono USB.' }
    & $adb -d shell am start -W -n com.grupo3.freno/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir Freno.' }
} finally {
    Pop-Location
}
