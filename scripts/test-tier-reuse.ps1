param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$serviceCredentialBytes = New-Object byte[] 32
$randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $randomGenerator.GetBytes($serviceCredentialBytes) } finally { $randomGenerator.Dispose() }
$env:DX_INTELLIGENCE_SERVICE_TOKEN = [Convert]::ToBase64String($serviceCredentialBytes)
$env:DX_INTELLIGENCE_URL = 'http://intelligence:9091/v1/triage'
$env:DX_INTELLIGENCE_PROFILE_ROOT = './apps/roomrequest/intelligence/profiles'
$composeArguments = @('-p', 'dx-tier-reuse', '-f', 'compose.yaml', '-f', 'scripts/acceptance/intelligence-compose.yaml', '--profile', 'intelligence')
try {
    & "$PSScriptRoot/setup.ps1"
    docker compose @composeArguments up -d --build --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Isolated reuse stack failed to start' }
    & $Python scripts/acceptance/tier-reuse.py --output .local/tier-reuse-acceptance.json
    if ($LASTEXITCODE -ne 0) { throw 'Real three-tier reuse acceptance failed' }
    & $Python scripts/check-core-contract.py .local/tier-reuse-acceptance-room.json contracts/apps/roomrequest/openapi.yaml
    if ($LASTEXITCODE -ne 0) { throw 'Room Request wire contract failed' }
    & $Python scripts/check-core-contract.py .local/tier-reuse-acceptance-hpdi.json contracts/hpdi/openapi.yaml
    if ($LASTEXITCODE -ne 0) { throw 'HPDI wire contract failed' }
    & "$PSScriptRoot/smoke.ps1" -ComposeProject dx-tier-reuse -GatewayUrl http://127.0.0.1:19080 -IdentityUrl http://127.0.0.1:18081
    & $Python scripts/check-core-contract.py .local/core-integration-http.json contracts/openapi/core.yaml
    if ($LASTEXITCODE -ne 0) { throw 'Core smoke wire contract failed' }
    $env:DX_ACCEPTANCE_GATEWAY_URL = 'http://127.0.0.1:19080'
    $env:DX_ACCEPTANCE_IDENTITY_URL = 'http://127.0.0.1:18081'
    & $Python scripts/acceptance/servicedesk.py .local/tier-servicedesk-http.json
    if ($LASTEXITCODE -ne 0) { throw 'Service Desk acceptance failed' }
    & $Python scripts/check-core-contract.py .local/tier-servicedesk-http.json contracts/openapi/servicedesk.yaml
    if ($LASTEXITCODE -ne 0) { throw 'Service Desk wire contract failed' }
} finally {
    docker compose @composeArguments stop
}
