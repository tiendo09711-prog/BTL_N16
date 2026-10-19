$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$serverConfig = Join-Path $root 'dist\BTL16-Auction-Server\app\config\server.properties'
if ((Test-Path -LiteralPath $serverConfig) -and (Get-Content -LiteralPath $serverConfig -Raw) -match '(?m)^db\.driver=com\.mysql\.cj\.jdbc\.Driver\s*$') {
    Write-Output '[INFO] MySQL-only build: the legacy H2 EXE smoke cannot isolate data with btl16.dataDir. Running packaged-runtime tests with disposable MySQL databases instead; production EXEs/database are not launched by this helper.'
    Push-Location -LiteralPath $root
    try {
        & node tools/run-tests.mjs --ui --mysql --packaged
        $result = $LASTEXITCODE
    } finally { Pop-Location }
    exit $result
}
$java = (Get-Command java -ErrorAction Stop).Source
$cacheRoot = Join-Path $env:LOCALAPPDATA 'BTL16\DesktopApps'
$data = Join-Path $root ('target\bundle-smoke-' + [Guid]::NewGuid().ToString('N'))
$state = Join-Path $data 'state.properties'
$owned = [System.Collections.Generic.List[int]]::new()
New-Item -ItemType Directory -Path $data -Force | Out-Null

function Test-AuctionPort {
    $socket = [System.Net.Sockets.TcpClient]::new()
    try {
        $attempt = $socket.ConnectAsync('127.0.0.1', 8890)
        if (-not $attempt.Wait(500)) { return $false }
        return $socket.Connected
    } catch { return $false }
    finally { $socket.Dispose() }
}

function Wait-AuctionPort {
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-AuctionPort) { return }
        Start-Sleep -Milliseconds 300
    }
    throw 'Packaged server did not listen on TCP 8890.'
}

function Start-AuctionBundle([string] $role) {
    $name = 'BTL16-Auction-' + $role + '.exe'
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = Join-Path $root ('dist\' + $name)
    $info.WorkingDirectory = $env:TEMP
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
    $info.EnvironmentVariables['JAVA_HOME'] = ''
    $info.EnvironmentVariables['PATH'] = $env:WINDIR + '\System32;' + $env:WINDIR
    $info.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = '-Dbtl16.dataDir="' + $data + '"'
    $bootstrap = [System.Diagnostics.Process]::Start($info)
    $owned.Add($bootstrap.Id)
    if (-not $bootstrap.WaitForExit(120000)) { throw 'Single-file bootstrap timed out.' }
    $owned.Remove($bootstrap.Id) | Out-Null
    $child = Get-CimInstance Win32_Process -Filter ('ParentProcessId = ' + $bootstrap.Id) |
        Where-Object { $_.Name -eq $name -and $_.ExecutablePath.StartsWith($cacheRoot, [StringComparison]::OrdinalIgnoreCase) } |
        Select-Object -First 1
    if ($null -eq $child) { throw ('Bundled runtime did not launch: ' + $name) }
    $owned.Add([int]$child.ProcessId)
    return [int]$child.ProcessId
}

function Invoke-NetworkCheck([string] $phase) {
    & $java -cp "$root\target\classes;$root\target\test-classes;$root\target\dependency\*" `
        vn.ptit.btl16.selftest.PackagedDesktopSelfTest 'ws://127.0.0.1:8890/ws' $phase $state
    if ($LASTEXITCODE -ne 0) { throw ('Packaged network check failed: ' + $phase) }
}

if (Test-AuctionPort) { throw 'Port 8890 is in use. Close the existing server before running this test.' }
try {
    $serverProcess = Start-AuctionBundle 'Server'
    Wait-AuctionPort
    $clientProcess = Start-AuctionBundle 'Client'
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    do {
        $client = Get-Process -Id $clientProcess -ErrorAction Stop
        $client.Refresh()
        if ($client.MainWindowHandle -ne 0) { break }
        $runtime = Get-CimInstance Win32_Process -Filter ('ParentProcessId = ' + $clientProcess) |
            Where-Object { $_.Name -eq 'BTL16-Auction-Client.exe' } | Select-Object -First 1
        if ($null -ne $runtime) {
            $client = Get-Process -Id $runtime.ProcessId -ErrorAction Stop
            $client.Refresh()
            if ($client.MainWindowHandle -ne 0) { break }
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($client.MainWindowHandle -eq 0) { throw 'Packaged JavaFX client did not open its window within 40 seconds.' }
    Write-Output '[PASS] Both single-file EXEs start outside the workspace without JAVA_HOME or Java on PATH.'
    Invoke-NetworkCheck 'create'
    Stop-Process -Id $serverProcess -ErrorAction Stop
    $owned.Remove($serverProcess) | Out-Null
    Start-Sleep -Seconds 1
    $serverProcess = Start-AuctionBundle 'Server'
    Wait-AuctionPort
    Invoke-NetworkCheck 'verify'
    Write-Output ('[PASS] Packaged application smoke test. Isolated test data: ' + $data)
} finally {
    foreach ($processId in $owned) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($null -ne $process) { Stop-Process -Id $processId -ErrorAction SilentlyContinue }
    }
}
