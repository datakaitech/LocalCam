param([string]$GradlePath, [string]$JdkPath = 'C:\Program Files\Android\Android Studio\jbr', [switch]$ReleaseBundle, [switch]$Lint)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!(Test-Path -LiteralPath (Join-Path $JdkPath 'bin/java.exe'))) { throw 'A JDK 17+ is required. Pass -JdkPath or use Android Studio.' }
if (!$GradlePath) {
    $localGradle = Join-Path $root 'tools/gradle-8.13/bin/gradle.bat'
    $wrapper = Join-Path $root 'android/gradlew.bat'
    if (Test-Path -LiteralPath $localGradle) { $GradlePath = $localGradle }
    elseif (Test-Path -LiteralPath $wrapper) { $GradlePath = $wrapper }
    else { throw 'Gradle 8.13 is required. Pass -GradlePath pointing to gradle.bat.' }
}
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
Push-Location (Join-Path $root 'android')
try {
    $tasks=@('assembleDebug')
    if($ReleaseBundle){$tasks+='bundleRelease'}
    if($Lint){$tasks+='lintDebug'}
    & $GradlePath --no-daemon --max-workers 1 --no-watch-fs --gradle-user-home (Join-Path $root '.gradle-cache') @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
} finally { Pop-Location }
