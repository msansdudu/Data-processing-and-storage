Param(
    [string]$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [int]$Port = 9000,
    [int]$Threads = 11,
    [int]$Clients = 111,
    [int]$MaxDelay = 5,
    [string]$IssuerDn = "CN=KeyIssuer,O=NSU",
    [Parameter(Mandatory=$true)][string]$IssuerKeyPath,
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
    param([int]$Index)
    $name = "clnt_$Index"
    $delay = Get-Random -Minimum 0 -Maximum ($MaxDelay + 1)
    $cp = Join-Path $ProjectDir 'build\classes\java\main'
    $jargs = @('-cp', $cp, 'ru.nsu.chebotareva.client.KeyClient', '--host', '127.0.0.1', '--port', $Port, '--name', $name, '--out', 'out')
    if ($delay -gt 0) { $jargs += @('--delay', $delay) }
    Start-Process -FilePath 'java' -ArgumentList $jargs -WorkingDirectory $ProjectDir -NoNewWindow | Out-Null
}

try {
    $outDir = Join-Path $ProjectDir 'out'
    if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Build-Once -ProjectDir $ProjectDir
    $server = Start-Server -ProjectDir $ProjectDir -Port $Port -Threads $Threads -IssuerDn $IssuerDn -IssuerKeyPath $IssuerKeyPath

    Write-Host "[TEST] Starting $Clients clients..."
    1..$Clients | ForEach-Object { Start-ClientJob -Index $_ }

    Write-Host "[TEST] Waiting for clients to finish..."
    Start-Sleep -Seconds ([Math]::Max(15, $MaxDelay * 2 + 10))

    $files = Get-ChildItem -Path $outDir -File -Filter *.crt
    $count = ($files | Measure-Object).Count
    if ($count -ge [Math]::Floor($Clients * 0.9)) {
        Write-Host "[TEST] Many-clients: likely OK ($count/$Clients certs)" -ForegroundColor Green
    } else {
        Write-Warning "[TEST] Many-clients: low output count ($count/$Clients). Check server logs."
    }
}
finally {
    Stop-Server -proc $server
    if ($Pause) { Read-Host "[TEST] Done. Press Enter to exit..." | Out-Null }
}
