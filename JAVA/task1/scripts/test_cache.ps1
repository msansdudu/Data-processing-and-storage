Param(
    [string]$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [int]$Port = 9000,
    [int]$Threads = 2,
    [string]$IssuerDn = "CN=KeyIssuer,O=NSU",
    [Parameter(Mandatory=$true)][string]$IssuerKeyPath,
    [string]$Name = 'same_name',
    [int]$Clients = 4,
    [switch]$Pause
)

$ErrorActionPreference = 'Stop'

function Start-Server {
    param($ProjectDir, $Port, $Threads, $IssuerDn, $IssuerKeyPath)
    $gradleArgs = "runServer -Pargs='--port $Port --threads $Threads --issuer ""$IssuerDn"" --key ""$IssuerKeyPath""'"
    Write-Host "[TEST] Starting server: $gradleArgs"
    $p = Start-Process -FilePath (Join-Path $ProjectDir 'gradlew.bat') -ArgumentList $gradleArgs -PassThru -WindowStyle Minimized -WorkingDirectory $ProjectDir
    Start-Sleep -Seconds 3
    return $p
}

function Stop-Server {
    param($proc)
    if ($null -ne $proc) {
        Write-Host "[TEST] Stopping server (PID=$($proc.Id))..."
        Start-Process -FilePath "taskkill" -ArgumentList "/F /T /PID $($proc.Id)" -NoNewWindow -Wait | Out-Null
    }
}

function Build-Once {
    param($ProjectDir)
    & (Join-Path $ProjectDir 'gradlew.bat') 'build' '-x' 'test' | Out-Null
}

function Start-ClientJob {
    param([string]$OutDir)
    New-Item -ItemType Directory -Force -Path (Join-Path $ProjectDir $OutDir) | Out-Null
    $cp = Join-Path $ProjectDir 'build\classes\java\main'
    $jargs = @('-cp', $cp, 'ru.nsu.chebotareva.client.KeyClient', '--host', '127.0.0.1', '--port', $Port, '--name', $Name, '--out', $OutDir)
    Start-Process -FilePath 'java' -ArgumentList $jargs -WorkingDirectory $ProjectDir -NoNewWindow | Out-Null
}

try {
    $outDirs = @()
    for ($i=1; $i -le $Clients; $i++) {
        $outDirs += ("out_cache_" + $i)
    }

    Build-Once -ProjectDir $ProjectDir
    $server = Start-Server -ProjectDir $ProjectDir -Port $Port -Threads $Threads -IssuerDn $IssuerDn -IssuerKeyPath $IssuerKeyPath

    Write-Host "[TEST] Starting $Clients clients with the same name '$Name'..."
    foreach ($d in $outDirs) {
        Start-ClientJob -OutDir $d
    }

    Write-Host "[TEST] Waiting for clients to finish..."
    Start-Sleep -Seconds 20

    $firstKey  = Join-Path $ProjectDir (Join-Path $outDirs[0] ("$Name.key"))
    $firstCrt  = Join-Path $ProjectDir (Join-Path $outDirs[0] ("$Name.crt"))
    if (-not (Test-Path $firstKey) -or -not (Test-Path $firstCrt)) { throw "[TEST] Base files not found: $firstKey or $firstCrt" }

    $refKeyBytes = [System.IO.File]::ReadAllBytes($firstKey)
    $refCrtBytes = [System.IO.File]::ReadAllBytes($firstCrt)

    $ok = $true
    for ($i=1; $i -lt $outDirs.Count; $i++) {
        $keyPath = Join-Path $ProjectDir (Join-Path $outDirs[$i] ("$Name.key"))
        $crtPath = Join-Path $ProjectDir (Join-Path $outDirs[$i] ("$Name.crt"))
        if (-not (Test-Path $keyPath) -or -not (Test-Path $crtPath)) { Write-Warning "[TEST] Missing $keyPath or $crtPath"; $ok = $false; continue }
        $k = [System.IO.File]::ReadAllBytes($keyPath)
        $c = [System.IO.File]::ReadAllBytes($crtPath)
        if ($k.Length -ne $refKeyBytes.Length -or -not ($k -ceq $refKeyBytes)) { Write-Warning "[TEST] Keys differ: $keyPath"; $ok = $false }
        if ($c.Length -ne $refCrtBytes.Length -or -not ($c -ceq $refCrtBytes)) { Write-Warning "[TEST] Certs differ: $crtPath"; $ok = $false }
    }

    if ($ok) { Write-Host "[TEST] Cache test: OK (all PEMs identical for the same name)" -ForegroundColor Green } else { throw "Cache test failed" }
}
finally {
    Stop-Server -proc $server
    if ($Pause) { Read-Host "[TEST] Done. Press Enter to exit..." | Out-Null }
}

