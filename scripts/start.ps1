# Dung: scripts/start.ps1           khoi dong toan bo he thong
#       scripts/start.ps1 -Reset    them buoc xoa va import lai realm Keycloak dx-lab (cung nhan --reset)
#       scripts/start.ps1 -Intelligence   chay kem service AI phan loai, profile Service Desk
#       scripts/start.ps1 -AiProfile roomrequest   chay kem AI, nap profile app dat phong thay cho Service Desk
param([switch]$Reset, [switch]$Intelligence, [ValidateSet('servicedesk', 'roomrequest')][string]$AiProfile)
$ErrorActionPreference = 'Stop'
# PowerShell khong co tham so dang --reset; nhan them de ai quen cu phap bash van dung duoc
if ($args -contains '--reset') { $Reset = $true }
# Chon profile AI thi hieu la muon chay kem AI
if ($AiProfile) { $Intelligence = $true }

& "$PSScriptRoot/setup.ps1"
# Co -f thi Compose khong tu nap compose.override.yaml nua nen ghi ro
$compose = @('compose', '-f', 'compose.yaml')
if (Test-Path compose.override.yaml) { $compose += @('-f', 'compose.override.yaml') }
$previousToken = $env:INTELLIGENCE_TRIAGE_TOKEN
$previousUrl = $env:INTELLIGENCE_TRIAGE_BASE_URL
$previousProfileRoot = $env:INTELLIGENCE_PROFILE_ROOT
$previousProfiles = $env:INTELLIGENCE_REQUIRED_PROFILES
try {
    if ($Intelligence) {
        $compose += @('-f', 'infra/compose/compose.triage.yaml', '--profile', 'intelligence')
        if (-not $env:INTELLIGENCE_TRIAGE_TOKEN) {
            $configuration = docker @compose config --format json
            if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve existing Intelligence configuration' }
            $env:INTELLIGENCE_TRIAGE_TOKEN = (($configuration -join "`n") | ConvertFrom-Json).services.platform.environment.INTELLIGENCE_TRIAGE_TOKEN
            if (-not $env:INTELLIGENCE_TRIAGE_TOKEN) {
                $bytes = New-Object byte[] 32
                $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
                try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
                $env:INTELLIGENCE_TRIAGE_TOKEN = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
            }
        }
        $env:INTELLIGENCE_TRIAGE_BASE_URL = 'http://intelligence:8000'
        # Service AI chi nap mot thu muc profile moi lan chay
        if ($AiProfile -eq 'roomrequest') {
            $env:INTELLIGENCE_PROFILE_ROOT = './intelligence/src/apps/roomrequest/intelligence/profiles'
            $env:INTELLIGENCE_REQUIRED_PROFILES = 'room-request@0.3.0'
        }
    }
    docker @compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose config' }
    docker @compose up -d --build --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Startup failed; inspect docker compose logs' }
    if ($Reset) { & "$PSScriptRoot/reset-keycloak-realm.ps1" }
} finally {
    $env:INTELLIGENCE_TRIAGE_TOKEN = $previousToken
    $env:INTELLIGENCE_TRIAGE_BASE_URL = $previousUrl
    $env:INTELLIGENCE_PROFILE_ROOT = $previousProfileRoot
    $env:INTELLIGENCE_REQUIRED_PROFILES = $previousProfiles
}
