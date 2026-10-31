param(
    [string]$Python = 'python',
    [ValidatePattern('^dx-tier-reuse-[a-z0-9-]{1,40}$')]
    [string]$Project = "dx-tier-reuse-$([guid]::NewGuid().ToString('N').Substring(0,12))"
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Run-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: $($args[0])" }
}
function Run-Python {
    & $Python @args
    if ($LASTEXITCODE -ne 0) { throw 'Reuse/contract verification failed' }
}
if (Run-Docker ps -aq --filter "label=com.docker.compose.project=$Project") { throw "Refusing existing project $Project" }
if (Run-Docker volume ls -q --filter "label=com.docker.compose.project=$Project") { throw "Refusing existing volumes for $Project" }
$names = @('INTELLIGENCE_TRIAGE_TOKEN', 'INTELLIGENCE_TRIAGE_BASE_URL', 'INTELLIGENCE_PROFILE_ROOT', 'INTELLIGENCE_REQUIRED_PROFILES')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$composeArguments = $null
try {
    Run-Python -c 'import yaml, openapi_spec_validator, openapi_schema_validator'
    & "$PSScriptRoot/setup.ps1"
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    $env:INTELLIGENCE_TRIAGE_TOKEN = [Convert]::ToBase64String($bytes)
    $env:INTELLIGENCE_TRIAGE_BASE_URL = 'http://intelligence:8000'
    $env:INTELLIGENCE_PROFILE_ROOT = './intelligence/src/apps/roomrequest/intelligence/profiles'
    $env:INTELLIGENCE_REQUIRED_PROFILES = 'room-request@0.3.0'
    $override = Join-Path $PWD ".local/$Project-images.yaml"
    [IO.File]::WriteAllText($override, "services:`n  platform:`n    image: ${Project}-platform:local`n  intelligence:`n    image: ${Project}-intelligence:local`n")
    $composeArguments = @('-p', $Project, '-f', 'compose.yaml', '-f', 'infra/compose/compose.triage.yaml', '-f', 'scripts/acceptance/intelligence-compose.yaml', '-f', $override, '--profile', 'intelligence')
    Run-Docker compose @composeArguments up -d --build --wait --wait-timeout 300
    $output = ".local/$Project.json"
    Run-Python scripts/acceptance/tier-reuse.py --output $output
    Run-Python scripts/check-core-contract.py ".local/$Project-room.json" contracts/apps/roomrequest/openapi.yaml
    Run-Python scripts/check-core-contract.py ".local/$Project-hpdi.json" contracts/hpdi/openapi.yaml
    Write-Host "PASS real Room Request reuse and wire contracts; evidence: $output"
} finally {
    if ($composeArguments) {
        & docker compose @composeArguments stop
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not stop proof project $Project" }
    }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
