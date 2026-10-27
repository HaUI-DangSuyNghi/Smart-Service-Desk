param(
    [Parameter(Mandatory=$true)][string]$BundleRoot,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path -LiteralPath $BundleRoot).Path
& $Python (Join-Path $PSScriptRoot 'team_release.py') stop --root $releaseRoot
if ($LASTEXITCODE -ne 0) { throw 'D2 release action failed; no credentials were printed.' }
