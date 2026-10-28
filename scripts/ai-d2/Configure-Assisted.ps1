param(
    [Parameter(Mandatory=$true)][string]$BundleRoot,
    [Parameter(Mandatory=$true)][string]$ExpectedManifestSha256,
    [Parameter(Mandatory=$true)][string]$PublicKey,
    [Parameter(Mandatory=$true)][string]$Activation,
    [Parameter(Mandatory=$true)][string]$ActivationSignature,
    [Parameter(Mandatory=$true)][string]$RightsDirectory,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path -LiteralPath $BundleRoot).Path
$trustKey = (Resolve-Path -LiteralPath $PublicKey).Path
& $Python (Join-Path $PSScriptRoot 'team_release.py') configure-assisted --root $releaseRoot --manifest-sha256 $ExpectedManifestSha256 --public-key $trustKey --activation $Activation --activation-signature $ActivationSignature --rights-directory $RightsDirectory
if ($LASTEXITCODE -ne 0) { throw 'Assisted activation rejected; no credentials were printed.' }
