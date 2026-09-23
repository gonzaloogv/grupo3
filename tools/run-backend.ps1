$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$backendDirectory = Join-Path $projectRoot 'backend'

if (-not (Test-Path (Join-Path $backendDirectory '.env'))) {
    throw 'Falta backend/.env. Copiá backend/.env.example y completá las claves.'
}

if (-not $env:JAVA_HOME) {
    $javaInstallation = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -Filter '*21*' -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
        Select-Object -First 1
    if (-not $javaInstallation) { throw 'Configurá JAVA_HOME con la ubicación de un JDK 21.' }
    $env:JAVA_HOME = $javaInstallation.FullName
}

Push-Location $backendDirectory
try {
    & .\gradlew.bat --console=plain :server:run
    if ($LASTEXITCODE -ne 0) { throw 'El backend terminó con un error.' }
} finally {
    Pop-Location
}
