param([string]$DeviceSerial)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$apk = Join-Path $root 'android/app/build/outputs/apk/debug/app-debug.apk'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $apk)) { throw 'Build Android first with scripts/build-android.ps1.' }
if (!(Test-Path -LiteralPath $adb)) { throw 'Android platform-tools not found.' }
if ($DeviceSerial) { & $adb -s $DeviceSerial install -r $apk } else { & $adb install -r $apk }
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Unlock your Android phone, enable USB debugging and approve this computer. Pass -DeviceSerial if multiple devices are connected.' }
Write-Host 'Installed. Open LocalCam on your phone and allow camera access when prompted.'