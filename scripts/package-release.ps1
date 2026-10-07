param([string]$WebsiteRoot)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$releaseRoot=Join-Path $projectRoot 'artifacts/releases'
if($WebsiteRoot){$WebsiteRoot=[IO.Path]::GetFullPath($WebsiteRoot);if(!(Test-Path -LiteralPath (Join-Path $WebsiteRoot 'index.html'))){throw 'WebsiteRoot must contain index.html.'}}
Push-Location $projectRoot
try {
    Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'LocalCam.Desktop.exe' -and $_.ExecutablePath -and $_.ExecutablePath.StartsWith($projectRoot+'\',[StringComparison]::OrdinalIgnoreCase) } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    dotnet publish desktop/LocalCam.Desktop.csproj --configuration Release --self-contained false --output artifacts/desktop --nologo
    if($LASTEXITCODE -ne 0){throw 'Windows publish failed'}
    Copy-Item -LiteralPath android/app/build/outputs/apk/debug/app-debug.apk -Destination artifacts/LocalCam-Android-debug.apk -Force
    New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
    Copy-Item -LiteralPath artifacts/LocalCam-Android-debug.apk -Destination (Join-Path $releaseRoot 'LocalCam-Android-0.8.apk') -Force
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zipPath=Join-Path $releaseRoot 'LocalCam-Windows-0.8.zip'
    $zip=[IO.Compression.ZipArchive]::new([IO.File]::Open($zipPath,[IO.FileMode]::Create),[IO.Compression.ZipArchiveMode]::Create)
    try {
        $desktopRoot=Join-Path $projectRoot 'artifacts/desktop'
        foreach($file in Get-ChildItem -LiteralPath $desktopRoot -File -Recurse){
            if($file.Name -eq 'output-settings.json' -or $file.Name -like '*.tmp'){continue}
            $entry=$file.FullName.Substring($desktopRoot.Length+1).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$entry) | Out-Null
        }
    } finally { $zip.Dispose() }
    $sourceZip=Join-Path $releaseRoot 'LocalCam-Source-0.8.zip'
    $zip=[IO.Compression.ZipArchive]::new([IO.File]::Open($sourceZip,[IO.FileMode]::Create),[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach($dir in @('desktop','android','scripts','branding','docs')){
            foreach($file in Get-ChildItem -LiteralPath (Join-Path $projectRoot $dir) -File -Recurse){
                $relative=$file.FullName.Substring($projectRoot.Length+1)
                if($relative -match '(^|\\)(bin|obj|build|downloads|node_modules|\.gradle|\.git|\.idea|\.vscode)(\\|$)' -or $file.Name -eq 'local.properties' -or $file.Name -match '\.(apk|aab|class|dll|exe|pdb|zip|log|tmp|jks|keystore|p12|pfx|pem|key)$' -or $file.Name -like '.env*' -or $file.Name -eq 'secrets.json' -or $file.Name -like 'output-settings*' -or $relative -match '^scripts\\tests\\(oriented-\d\.jpg|pairing-qr\.(png|json))$'){continue}
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$relative.Replace('\','/')) | Out-Null
            }
        }
        foreach($name in @('LICENSE','README.md','CONTRIBUTING.md','SECURITY.md','.gitignore','.gitattributes','START-HERE.txt','PLAN.txt','VALIDATION.txt','SECURITY.txt')){
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $projectRoot $name),$name) | Out-Null
        }
    } finally { $zip.Dispose() }
    $checksums=foreach($name in @('LocalCam-Windows-0.8.zip','LocalCam-Android-0.8.apk','LocalCam-Source-0.8.zip')){
        $hash=Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releaseRoot $name)
        $hash.Hash.ToLowerInvariant()+'  '+$name
    }
    [IO.File]::WriteAllLines((Join-Path $releaseRoot 'SHA256SUMS.txt'),$checksums)
    if($WebsiteRoot){
        $downloads=Join-Path $WebsiteRoot 'downloads';New-Item -ItemType Directory -Path $downloads -Force | Out-Null
        foreach($name in @('LocalCam-Windows-0.8.zip','LocalCam-Android-0.8.apk','LocalCam-Source-0.8.zip','SHA256SUMS.txt')){Copy-Item -LiteralPath (Join-Path $releaseRoot $name) -Destination (Join-Path $downloads $name) -Force}
    }
    Write-Output 'Packaged Windows 0.8.1 and Android 0.8 downloads in artifacts/releases; source excludes generated/private files and the separate website.'
} finally { Pop-Location }
