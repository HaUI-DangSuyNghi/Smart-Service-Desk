param([string[]]$KeepService = @(), [string]$ProjectName = '')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/docker-native.ps1"
# Limit operations to the selected Compose project and the two known AI services.
$project = $ProjectName
if (-not $project) {
    $compose = @('compose', '-f', 'compose.yaml')
    if (Test-Path compose.override.yaml) { $compose += @('-f', 'compose.override.yaml') }
    $raw = Invoke-Docker @compose config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the active Compose project.' }
    $project = (($raw -join "`n") | ConvertFrom-Json).name
}
if ($project -notmatch '^[a-z0-9][a-z0-9_-]*$') { throw 'Invalid Compose project identity.' }
foreach ($service in @('ai07-qwen', 'intelligence-api')) {
    if ($service -in $KeepService) { continue }
    $containers = @(Invoke-Docker ps -q --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=$service")
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the previous AI service.' }
    if ($containers.Count -gt 0) {
        Invoke-Docker stop @containers
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stop the previously selected AI provider.' }
    }
}
