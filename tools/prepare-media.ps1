param([string]$Destination = (Join-Path $PSScriptRoot 'media'))

$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$destination = [IO.Path]::GetFullPath($Destination)
$binaries = @{
    'ffmpeg.exe' = '1326DDE4C84FF1F96FE6B8916C5BED29E163E9B5DCCF995F6F3DB069D143EC5E'
    'ffprobe.exe' = 'B49CCC7C6547B141AD5A2F6EC69CC04323D7133D7704D70B331B904C63EECB07'
}

function Test-VerifiedMediaTools([string]$Directory) {
    foreach ($binary in $binaries.Keys) {
        $file = Join-Path $Directory $binary
        if (!(Test-Path -LiteralPath $file -PathType Leaf) -or
            (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $binaries[$binary]) { return $false }
    }
    return $true
}

if (Test-VerifiedMediaTools $destination) {
    Write-Output "Verified FFmpeg 8.1.2 processing tools: $destination"
    return
}

# A source checkout can reuse the verified server bundle without downloading it again.
$bundled = Join-Path $project 'dist\BTL16-Auction-Server\app\tools\media'
if (Test-VerifiedMediaTools $bundled) {
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    foreach ($file in @('ffmpeg.exe', 'ffprobe.exe', 'LICENSE', 'README.txt', 'PROVENANCE.txt')) {
        $sourceFile = Join-Path $bundled $file
        if (Test-Path -LiteralPath $sourceFile -PathType Leaf) {
            Copy-Item -LiteralPath $sourceFile -Destination $destination -Force
        }
    }
    if (!(Test-VerifiedMediaTools $destination)) { throw 'Media tool checksum mismatch after restoring the server bundle.' }
    Write-Output "Restored verified FFmpeg 8.1.2 processing tools from the server bundle: $destination"
    return
}

$archive = Join-Path $project 'target\ffmpeg-8.1.2.zip'
$unpacked = Join-Path $project 'target\ffmpeg-unpacked'
$expected = 'DB580001CAA24AC104C8CB856CD113A87B0A443F7BDF47D8C12B1D740584A2EC'
New-Item -ItemType Directory -Force -Path (Join-Path $project 'target'), $destination | Out-Null
if (!(Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -UseBasicParsing -Uri 'https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-8.1.2-essentials_build.zip' -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
    throw 'FFmpeg archive checksum mismatch. No tools were installed.'
}
Expand-Archive -LiteralPath $archive -DestinationPath $unpacked -Force
$source = Join-Path $unpacked 'ffmpeg-8.1.2-essentials_build'
Copy-Item -LiteralPath (Join-Path $source 'bin\ffmpeg.exe'), (Join-Path $source 'bin\ffprobe.exe'), (Join-Path $source 'LICENSE'), (Join-Path $source 'README.txt') -Destination $destination -Force
if (!(Test-VerifiedMediaTools $destination)) { throw 'Media tool checksum mismatch after extracting the archive.' }
Write-Output "Verified FFmpeg 8.1.2 processing tools: $destination"
