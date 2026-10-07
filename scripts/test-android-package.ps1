param([string]$Apk='android/app/build/outputs/apk/debug/app-debug.apk')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path (Get-Location) $Apk))
try {
    $tested=0
    foreach($entry in $zip.Entries | Where-Object {$_.FullName -match '^lib/(arm64-v8a|x86_64)/.*\.so$'}) {
        $stream=$entry.Open();$memory=[IO.MemoryStream]::new()
        try{$stream.CopyTo($memory);$bytes=$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}
        if($bytes[0] -ne 127 -or $bytes[1] -ne 69 -or $bytes[2] -ne 76 -or $bytes[3] -ne 70 -or $bytes[4] -ne 2){throw 'Unexpected ELF format'}
        $offset=[BitConverter]::ToUInt64($bytes,32);$stride=[BitConverter]::ToUInt16($bytes,54);$count=[BitConverter]::ToUInt16($bytes,56)
        for($i=0;$i -lt $count;$i++){$header=[int]($offset+$i*$stride);if([BitConverter]::ToUInt32($bytes,$header) -eq 1 -and [BitConverter]::ToUInt64($bytes,$header+48) -lt 16384){throw ('Native library lacks 16KB alignment: '+$entry.FullName)}}
        $tested++
    }
    Write-Output ('PASS: '+$tested+' packaged 64-bit native libraries use >=16KB ELF load-segment alignment.')
}finally{$zip.Dispose()}
