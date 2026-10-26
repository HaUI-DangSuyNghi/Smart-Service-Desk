param(
    [ValidatePattern('^scan-[a-z0-9-]{1,45}$')][string]$Run = "scan-$([guid]::NewGuid().ToString('N').Substring(0,12))",
    [ValidateNotNullOrEmpty()][string[]]$Images = @('dx-platform:0.1.0', 'postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4', 'quay.io/keycloak/keycloak:26.0.8@sha256:09a381c715ab0b111835b70f2905955274843a219c6f27efb348e4d9f4086858', 'dx-apisix:3.11.0-sec01', 'dx-platform-build:0.1.0'),
    [switch]$ReportOnly,
    [switch]$FromCompose,
    [string[]]$ComposeFiles = @('compose.yaml'),
    [string[]]$Profiles = @(),
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,80}$')][string]$CacheVolume = 'dx-core-trivy-cache'
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if ($FromCompose) {
    $compose = @('compose')
    foreach ($file in $ComposeFiles) { $compose += @('-f', $file) }
    foreach ($profile in $Profiles) { $compose += @('--profile', $profile) }
    $Images = @(docker @compose config --images | Sort-Object -Unique)
    if ($LASTEXITCODE -ne 0 -or $Images.Count -eq 0) { throw 'Cannot resolve Compose image inventory' }
}
$scanner = 'aquasec/trivy@sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222'
$directory = Join-Path $PWD ".local/$Run"
if (Test-Path -LiteralPath $directory) { throw 'Scan output directory already exists' }
New-Item -ItemType Directory -Path $directory | Out-Null
$summary = @()
foreach ($image in $Images) {
    $index = $summary.Count
    $entry = [ordered]@{image=$image; scan='NOT VERIFIED'; high=0; critical=0; fixedHighCritical=0}
    try {
        $inspection = docker image inspect $image | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw "Image not available: $image" }
        $entry['imageId'] = $inspection.Id
        $entry['digests'] = $inspection.RepoDigests
        docker image save -o (Join-Path $directory "image-$index.tar") $image
        if ($LASTEXITCODE -ne 0) { throw "Cannot export $image" }
        docker run --rm --mount "type=bind,source=$directory,target=/scan" --mount "type=volume,source=$CacheVolume,target=/root/.cache/trivy" $scanner image --input "/scan/image-$index.tar" --scanners vuln --list-all-pkgs --no-progress --timeout 15m --format json --output "/scan/image-$index.json" --exit-code 0
        if ($LASTEXITCODE -ne 0) { throw "Scanner failed for $image; this is not a clean scan" }
        $report = Get-Content -LiteralPath (Join-Path $directory "image-$index.json") -Raw | ConvertFrom-Json
        if (!$report.Metadata -or !$report.SchemaVersion) { throw 'Invalid scanner report' }
        if (!$report.Metadata.OS -or !@($report.Results | Where-Object Class -eq os-pkgs).Count) { throw 'Missing OS package scan' }
        if ($image -match '^(dx-platform(?:-build)?:|int01-[a-z0-9-]+-platform:|quay.io/keycloak/)' -and !@($report.Results | Where-Object Type -eq jar).Count) { throw 'Missing packaged Java dependency scan' }
        if ($image -match '-intelligence:' -and !@($report.Results | Where-Object Type -eq 'python-pkg').Count) { throw 'Missing installed Python dependency scan' }
        $entry['targets'] = @($report.Results | Select-Object Target,Class,Type)
        $vulnerabilities = @($report.Results | ForEach-Object { $_.Vulnerabilities } | Where-Object { $null -ne $_ })
        $entry.high = @($vulnerabilities | Where-Object Severity -eq HIGH).Count
        $entry.critical = @($vulnerabilities | Where-Object Severity -eq CRITICAL).Count
        $entry.fixedHighCritical = @($vulnerabilities | Where-Object { $_.Severity -in @('HIGH', 'CRITICAL') -and $_.FixedVersion }).Count
        $entry['endOfSupport'] = [bool]$report.Metadata.OS.EOSL
        $entry.scan = 'PASS'
        $entry['productionGate'] = if ($entry.high + $entry.critical -gt 0 -or $entry.endOfSupport) { 'FAIL' } else { 'PASS' }
        Write-Host "$image scan=PASS HIGH=$($entry.high) CRITICAL=$($entry.critical) productionGate=$($entry.productionGate)"
    } catch {
        $entry.scan = 'FAIL'
        $entry['error'] = $_.Exception.Message
        $entry['productionGate'] = 'NOT VERIFIED'
        Write-Warning $_.Exception.Message
    }
    $summary += [pscustomobject]$entry
    ConvertTo-Json -InputObject @($summary) -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'summary.json') -Encoding UTF8
}
docker run --rm --mount "type=volume,source=$CacheVolume,target=/root/.cache/trivy" $scanner --version | Set-Content -LiteralPath (Join-Path $directory 'scanner-version.txt') -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw 'Cannot record scanner database/version' }
Write-Host "Reports and package inventories: $directory. Scan success does not mean production approval."
if (@($summary | Where-Object scan -ne PASS).Count) { throw 'One or more images were not scanned successfully' }
if (!$ReportOnly -and @($summary | Where-Object productionGate -ne PASS).Count) { throw 'Production security gate FAIL: HIGH/CRITICAL findings or end-of-support OS require remediation and retest' }
