param([switch]$SkipImageLoad)
$ErrorActionPreference = 'Stop'
$packageRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$package = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $packageRoot 'package.json') | ConvertFrom-Json
if ($package.schemaVersion -ne 'ai07-qwen-team-package@0.1.0') { throw 'Unexpected package schema' }

foreach ($entry in $package.files.PSObject.Properties) {
    $targetPath = [IO.Path]::GetFullPath((Join-Path $packageRoot $entry.Name))
    if (-not $targetPath.StartsWith($packageRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Package path escapes the package directory'
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetPath).Hash.ToLowerInvariant()
    if ($actualHash -ne $entry.Value) { throw "Package checksum mismatch: $($entry.Name)" }
}

if (-not $SkipImageLoad) {
    $localImages = @(docker image ls --no-trunc --quiet)
    if ($LASTEXITCODE -ne 0) { throw 'Docker is not available' }
    if ($package.imageId -notin $localImages) {
        docker load --input (Join-Path $packageRoot 'qwen-image.tar.gz')
        if ($LASTEXITCODE -ne 0) { throw 'Cannot load the pinned Docker image' }
    }
}

$secretDirectory = Join-Path $packageRoot '.secrets'
New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null
$secretPath = Join-Path $secretDirectory 'classification-token'
if (-not (Test-Path -LiteralPath $secretPath)) {
    $randomBytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
    $token = [BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant()
    [IO.File]::WriteAllText($secretPath, $token, [Text.Encoding]::ASCII)
}

$env:AI07_QWEN_IMAGE = $package.imageId
$env:AI07_QWEN_MODEL_DIGEST = $package.modelDigest
$env:AI07_QWEN_BUNDLE = Join-Path $packageRoot 'model'
$env:AI07_QWEN_APP_ROOT = Join-Path $packageRoot 'apps'
$env:AI07_QWEN_TOKEN_FILE = $secretPath
$env:AI07_QWEN_ENABLED = '1'
$env:AI07_QWEN_TEST_CLIENT = Join-Path $packageRoot 'classify-client.py'
$env:AI07_QWEN_PACKAGE_METADATA = Join-Path $packageRoot 'package.json'
docker compose -p ai07-qwen-team -f (Join-Path $packageRoot 'compose.yaml') -f (Join-Path $packageRoot 'compose.test.yaml') --profile ai07-qwen up -d --wait --wait-timeout 240
if ($LASTEXITCODE -ne 0) { throw 'Qwen did not become ready; do not send inference requests' }
Write-Host 'Qwen is ready in the private Docker network; run Test-Qwen.ps1 for a synthetic request.'
