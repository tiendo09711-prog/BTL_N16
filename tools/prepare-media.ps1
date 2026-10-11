$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$destination = Join-Path $PSScriptRoot 'media'
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
$binaries = @{
    'ffmpeg.exe' = '1326DDE4C84FF1F96FE6B8916C5BED29E163E9B5DCCF995F6F3DB069D143EC5E'
    'ffprobe.exe' = 'B49CCC7C6547B141AD5A2F6EC69CC04323D7133D7704D70B331B904C63EECB07'
}
foreach ($binary in $binaries.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $destination $binary) -Algorithm SHA256).Hash -ne $binaries[$binary]) { throw "Checksum mismatch: $binary" }
}
Write-Output "Verified FFmpeg 8.1.2 processing tools: $destination"
