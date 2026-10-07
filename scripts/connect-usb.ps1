param([string]$DeviceSerial)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'Android platform-tools not found. Install the SDK or update the adb path in this script.' }
if ($DeviceSerial) { & $adb -s $DeviceSerial reverse tcp:8788 tcp:8788 } else { & $adb reverse tcp:8788 tcp:8788 }
if ($LASTEXITCODE -ne 0) { throw 'USB setup failed. Enable USB debugging, unlock the phone, and approve this computer. With multiple phones, pass -DeviceSerial.' }
Write-Host 'USB tunnel ready. Enter 127.0.0.1 in the Android app. No Wi-Fi is required.'
