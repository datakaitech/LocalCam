param([string]$JdkPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(!$JdkPath){throw 'Set JAVA_HOME or pass -JdkPath with a JDK 17+ directory.'}
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$output=Join-Path $projectRoot 'artifacts/test-java'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$javaRoot=Join-Path $projectRoot 'android/app/src/main/java/org/localcam/app'
$sources=@('YuvFrames','OrientationMath','FrameProtocol','CaptureCadence','AdaptiveQuality','JpegOrientation') | ForEach-Object {Join-Path $javaRoot ($_+'.java')}
$tests=@('YuvFramesTest','AndroidCoreTest','CaptureCadenceTest','AdaptiveQualityTest','JpegOrientationTest')
$testSources=$tests | ForEach-Object {Join-Path $PSScriptRoot ('tests/'+$_+'.java')}
& (Join-Path $JdkPath 'bin/javac.exe') -d $output $sources $testSources
if($LASTEXITCODE -ne 0){throw 'Java test compilation failed.'}
Push-Location $projectRoot
try{
    foreach($test in $tests){& (Join-Path $JdkPath 'bin/java.exe') -cp $output $test;if($LASTEXITCODE -ne 0){throw ($test+' failed.')}}
    dotnet run --project scripts/ui-tests/UiTests.csproj
    if($LASTEXITCODE -ne 0){throw 'WPF video regression failed.'}
}finally{Pop-Location}
