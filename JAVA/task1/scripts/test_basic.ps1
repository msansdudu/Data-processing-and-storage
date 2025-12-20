Param(
    [string]$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [int]$Port = 9000,
    [int]$Threads = 2,
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

function Run-Client {
    param($ProjectDir, [string]$ServerHost, [int]$Port, [string]$Name, [int]$Delay=0, [switch]$Abort, [string]$OutDir="out")
    $cp = Join-Path $ProjectDir 'build\classes\java\main'
    $jargs = @('-cp', $cp, 'ru.nsu.chebotareva.client.KeyClient', '--host', $ServerHost, '--port', $Port, '--name', $Name, '--out', $OutDir)
    if ($Delay -gt 0) { $jargs += @('--delay', $Delay) }
    if ($Abort) { $jargs += '--abort' }
    Write-Host "[TEST] Client (java): $($jargs -join ' ')"
    Start-Process -FilePath 'java' -ArgumentList $jargs -PassThru -Wait -NoNewWindow -WorkingDirectory $ProjectDir | Out-Null
}

try {
    Build-Once -ProjectDir $ProjectDir
    $server = Start-Server -ProjectDir $ProjectDir -Port $Port -Threads $Threads -IssuerDn $IssuerDn -IssuerKeyPath $IssuerKeyPath

    Run-Client -ProjectDir $ProjectDir -ServerHost '127.0.0.1' -Port $Port -Name 'alice' -OutDir 'out'

    Run-Client -ProjectDir $ProjectDir -ServerHost '127.0.0.1' -Port $Port -Name 'bob' -Delay 2 -OutDir 'out'

    Run-Client -ProjectDir $ProjectDir -ServerHost '127.0.0.1' -Port $Port -Name 'charlie' -Abort -OutDir 'out'

    $ok = $true
    $aliceKey = Join-Path $ProjectDir 'out\alice.key'
    $aliceCrt = Join-Path $ProjectDir 'out\alice.crt'
    $bobKey   = Join-Path $ProjectDir 'out\bob.key'
    $bobCrt   = Join-Path $ProjectDir 'out\bob.crt'
    if (-not (Test-Path $aliceKey)) { Write-Warning "[TEST] Missing $aliceKey"; $ok = $false }
    if (-not (Test-Path $aliceCrt)) { Write-Warning "[TEST] Missing $aliceCrt"; $ok = $false }
    if (-not (Test-Path $bobKey)) { Write-Warning "[TEST] Missing $bobKey"; $ok = $false }
    if (-not (Test-Path $bobCrt)) { Write-Warning "[TEST] Missing $bobCrt"; $ok = $false }
    $charlieKey = Join-Path $ProjectDir 'out\charlie.key'
    $charlieCrt = Join-Path $ProjectDir 'out\charlie.crt'
    if (Test-Path $charlieKey -or Test-Path $charlieCrt) { Write-Warning "[TEST] Abort scenario produced files"; $ok = $false }

    if ($ok) { Write-Host "[TEST] Basic scenarios: OK" -ForegroundColor Green } else { throw "Basic scenarios failed" }
} 
finally {
    Stop-Server -proc $server
    if ($Pause) { Read-Host "[TEST] Done. Press Enter to exit..." | Out-Null }
}
