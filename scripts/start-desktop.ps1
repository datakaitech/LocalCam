$ErrorActionPreference = 'Stop'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$project = Join-Path $PSScriptRoot '../desktop/LocalCam.Desktop.csproj'
Write-Host 'Starting the native LocalCam Windows application.'
dotnet run --project $project
