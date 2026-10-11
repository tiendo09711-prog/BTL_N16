$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jarName = 'btl16-realtime-auction-1.0.0.jar'
$jarHash = (Get-FileHash -LiteralPath (Join-Path $project "target\$jarName") -Algorithm SHA256).Hash.ToLowerInvariant()
function Stream-Hash($stream) {
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose() }
}
function Entry($archive, $name) {
    return $archive.Entries | Where-Object { $_.FullName.Replace('\', '/') -eq $name } | Select-Object -First 1
}
foreach ($role in @('Server', 'Client')) {
    $name = "BTL16-Auction-$role"
    $image = Join-Path $project "dist\$name"
    $executable = Join-Path $project "dist\$name.exe"
    $assembly = [Reflection.Assembly]::ReflectionOnlyLoadFrom($executable)
    $resource = $assembly.GetManifestResourceStream('auction-payload.zip')
    if (!$resource) { throw "Missing embedded payload: $name" }
    $payload = [IO.Compression.ZipArchive]::new($resource, [IO.Compression.ZipArchiveMode]::Read)
    try {
        $entry = Entry $payload "app/$jarName"
        if (!$entry) { throw "Missing application JAR: $name" }
        $stream = $entry.Open()
        try {
            if ((Stream-Hash $stream) -ne $jarHash) { throw "Stale embedded JAR: $name" }
        } finally { $stream.Dispose() }
        if ((Get-FileHash -LiteralPath (Join-Path $image "app\$jarName") -Algorithm SHA256).Hash.ToLowerInvariant() -ne $jarHash) { throw "Stale app-image JAR: $name" }
        if (!(Entry $payload 'app/javafx-media-21.0.4-win.jar')) { throw "Missing JavaFX Media natives: $name" }
        $databases = @($payload.Entries | Where-Object { $_.FullName -match '(\.mv\.db|\.h2\.db|\.trace\.db|\.lock\.db)$|media[\\/]products[\\/]|media[\\/]staging[\\/]' })
        if ($databases.Count -gt 0) { throw "Bundled user data: $name" }
        foreach ($binary in @('ffmpeg.exe', 'ffprobe.exe')) {
            $native = Entry $payload "app/tools/media/$binary"
            if ($role -eq 'Server') {
                if (!$native) { throw "Missing native processor: $binary" }
                $stream = $native.Open()
                try { if ((Stream-Hash $stream) -ne (Get-FileHash -LiteralPath (Join-Path $project "tools\media\$binary") -Algorithm SHA256).Hash.ToLowerInvariant()) { throw "Changed processor: $binary" } }
                finally { $stream.Dispose() }
            } elseif ($native) { throw 'Client unexpectedly includes server media processors' }
        }
        if ($role -eq 'Server' -and !(Entry $payload 'app/tools/media/LICENSE')) { throw 'Missing processor license' }
    } finally { $payload.Dispose(); $resource.Dispose() }
    $jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $image "app\$jarName"))
    try {
        foreach ($class in @('server/media/MediaProcessor', 'server/media/MediaHttpServer', 'client/fx/ProductMediaEditor', 'client/fx/MediaGalleryPane')) {
            if (!(Entry $jar "vn/ptit/btl16/$class.class")) { throw "Missing compiled feature: $class" }
        }
        if (Entry $jar 'vn/ptit/btl16/server/auction/service/ProductImageValidator.class') { throw 'Obsolete header-only image validator remains packaged' }
    } finally { $jar.Dispose() }
    $modules = Get-Content -LiteralPath (Join-Path $image 'runtime\release') -Raw
    if ($modules -notmatch '\bjdk\.httpserver\b') { throw "Missing HTTP runtime module: $name" }
    Write-Output "[PASS] $name embedded JAR, JavaFX Media, processors/license, runtime modules and no bundled user data"
}
foreach ($line in Get-Content -LiteralPath (Join-Path $project 'dist\SHA256SUMS.txt')) {
    if ($line -notmatch '^([0-9a-f]{64})  (BTL16-Auction-(Server|Client)\.exe)$') { throw 'Invalid checksum manifest' }
    if ((Get-FileHash -LiteralPath (Join-Path $project "dist\$($Matches[2])") -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Matches[1]) { throw 'EXE checksum mismatch' }
}
if ((Get-FileHash -LiteralPath (Join-Path $project 'docs\LUAT_DAU_GIA_BTL16.docx') -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath (Join-Path $project 'dist\LUAT_DAU_GIA_BTL16.docx') -Algorithm SHA256).Hash) { throw 'Stale distributed rules' }
Write-Output '[PASS] EXE checksums and distributed Word rules. No application entry point was launched.'
