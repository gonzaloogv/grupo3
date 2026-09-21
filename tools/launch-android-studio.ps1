$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$studioRoot = Join-Path $PSScriptRoot 'android-studio'
$studioBin = 'C:\Program Files\Android\Android Studio\bin\studio.bat'

New-Item -ItemType Directory -Force -Path @(
    (Join-Path $studioRoot 'roaming'),
    (Join-Path $studioRoot 'local')
) | Out-Null

Get-Process studio64 -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -eq 'C:\Program Files\Android\Android Studio\bin\studio64.exe' } |
    Stop-Process -Force

$env:APPDATA = Join-Path $studioRoot 'roaming'
$env:LOCALAPPDATA = Join-Path $studioRoot 'local'
$env:USERPROFILE = Join-Path $studioRoot 'user'
$env:ANDROID_HOME = Join-Path $PSScriptRoot 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $PSScriptRoot 'android-user-home'
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot 'gradle-home'
$env:Path = "$(Join-Path $env:ANDROID_HOME 'platform-tools');$env:Path"
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="C:\Users\foto alex\Desktop\hackaton\tmp" -Didea.trust.all.projects=true'

& $studioBin $projectRoot
