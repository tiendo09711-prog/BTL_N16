$jdkRoot = Join-Path $env:LOCALAPPDATA 'BTL16\tools'
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $jdk) {
    throw "BTL16 JDK 17 was not found in $jdkRoot. See docs/21_LOCAL_WINDOWS_SETUP.md."
}

$env:JAVA_HOME = $jdk.FullName
$javaBin = Join-Path $env:JAVA_HOME 'bin'
$pathEntries = @($env:Path -split ';' | Where-Object { $_ -and $_ -ne $javaBin })
$env:Path = (@($javaBin) + $pathEntries) -join ';'

Write-Host "[BTL16] JAVA_HOME=$env:JAVA_HOME"
Write-Host '[BTL16] Environment ready for this PowerShell session.'
