$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaTools = 'C:\Program Files\Android\Android Studio\jbr\bin'
$zxingJar = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.gradle-cache\caches\modules-2\files-2.1\com.google.zxing\core\3.4.1') -Recurse -Filter '*.jar' | Select-Object -First 1
$jsonJar = Join-Path $projectRoot 'tools\test-libs\json-20240303.jar'
if (-not $zxingJar -or -not (Test-Path -LiteralPath $jsonJar)) { throw 'Build Android first; the test-only JSON jar must be present in tools/test-libs.' }
$outputDir = Join-Path $PSScriptRoot 'tests\bin'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$classpath = $zxingJar.FullName + ';' + $jsonJar
& (Join-Path $javaTools 'javac.exe') -cp $classpath -d $outputDir (Join-Path $projectRoot 'android\app\src\main\java\org\localcam\app\PairingPayload.java') (Join-Path $PSScriptRoot 'tests\PairingQrTest.java')
if ($LASTEXITCODE -ne 0) { throw 'QR test compilation failed.' }
Push-Location $projectRoot
try {
    & (Join-Path $javaTools 'java.exe') -cp ($outputDir + ';' + $classpath) PairingQrTest $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'QR test failed.' }
} finally { Pop-Location }
