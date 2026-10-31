param(
    [Parameter(Mandatory=$true)][string]$BundleRoot,
    [Parameter(Mandatory=$true)][string]$ExpectedManifestSha256,
    [Parameter(Mandatory=$true)][string]$PublicKey,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path -LiteralPath $BundleRoot).Path
$trustKey = (Resolve-Path -LiteralPath $PublicKey).Path
& $Python (Join-Path $PSScriptRoot 'team_release.py') install --root $releaseRoot --manifest-sha256 $ExpectedManifestSha256 --public-key $trustKey
if ($LASTEXITCODE -ne 0) { throw 'D2 release action failed; no credentials were printed.' }
