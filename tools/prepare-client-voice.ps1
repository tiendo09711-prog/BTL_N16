param()
$ErrorActionPreference = 'Stop'
$speechRoot = Join-Path $PSScriptRoot 'speech'
$downloadRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'target\speech-downloads'
New-Item -ItemType Directory -Force -Path $speechRoot, $downloadRoot | Out-Null

function Get-VoiceFile([string] $Url, [string] $Destination) {
    & curl.exe --fail --silent --show-error --location --retry 2 --max-time 300 --output $Destination $Url
    if ($LASTEXITCODE -ne 0) { throw "Voice download failed: $Url" }
}

$piperExecutable = Join-Path $speechRoot 'piper\piper.exe'
if (-not (Test-Path -LiteralPath $piperExecutable)) {
    $archive = Join-Path $downloadRoot 'piper_windows_amd64.zip'
    Get-VoiceFile 'https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip' $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $speechRoot -Force
}

$voiceName = 'vi_VN-vais1000-medium.onnx'
$voiceHash = 'ec7c89e2c85f4d1edc24b6120c18aaf1bda614f06b511567eb9c7c0de15e2dab'
$voiceUrl = 'https://huggingface.co/rhasspy/piper-voices/resolve/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/vi/vi_VN/vais1000/medium/'
$voiceFile = Join-Path $speechRoot $voiceName
if (-not (Test-Path -LiteralPath $voiceFile) -or (Get-FileHash -LiteralPath $voiceFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $voiceHash) {
    Get-VoiceFile ($voiceUrl + $voiceName) $voiceFile
}
if ((Get-FileHash -LiteralPath $voiceFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $voiceHash) {
    throw 'Vietnamese voice model SHA-256 mismatch.'
}
Get-VoiceFile ($voiceUrl + $voiceName + '.json') ($voiceFile + '.json')
Get-VoiceFile ($voiceUrl + 'MODEL_CARD') (Join-Path $speechRoot 'MODEL_CARD.md')
Get-VoiceFile 'https://raw.githubusercontent.com/rhasspy/piper/2023.11.14-2/LICENSE.md' (Join-Path $speechRoot 'PIPER_LICENSE.md')
Get-VoiceFile 'https://raw.githubusercontent.com/espeak-ng/espeak-ng/1.51/COPYING' (Join-Path $speechRoot 'ESPEAK_COPYING.txt')
Write-Host '[VOICE] Vietnamese voice ready. Bid announcements are synthesized locally.'
