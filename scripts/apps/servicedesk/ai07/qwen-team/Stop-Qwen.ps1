$ErrorActionPreference = 'Stop'
$package = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'package.json') | ConvertFrom-Json
$env:AI07_QWEN_IMAGE = $package.imageId
$env:AI07_QWEN_MODEL_DIGEST = $package.modelDigest
$env:AI07_QWEN_BUNDLE = Join-Path $PSScriptRoot 'model'
$env:AI07_QWEN_APP_ROOT = Join-Path $PSScriptRoot 'apps'
$env:AI07_QWEN_TOKEN_FILE = Join-Path $PSScriptRoot '.secrets/classification-token'
$env:AI07_QWEN_TEST_CLIENT = Join-Path $PSScriptRoot 'classify-client.py'
$env:AI07_QWEN_PACKAGE_METADATA = Join-Path $PSScriptRoot 'package.json'
docker compose -p ai07-qwen-team -f (Join-Path $PSScriptRoot 'compose.yaml') -f (Join-Path $PSScriptRoot 'compose.test.yaml') --profile ai07-qwen down
if ($LASTEXITCODE -ne 0) { throw 'Cannot stop Qwen services' }
