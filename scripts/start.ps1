# Dung: scripts/start.ps1           khoi dong toan bo he thong
#       scripts/start.ps1 -Reset    them buoc xoa va import lai realm Keycloak dx-lab (cung nhan --reset)
#       scripts/start.ps1 -Intelligence   chay kem service AI phan loai, profile Service Desk
#       scripts/start.ps1 -AiProfile roomrequest   chay kem AI, nap profile app dat phong thay cho Service Desk
#       scripts/start.ps1 -Model   Qwen local; saves AI_MODE=model in .env
#       scripts/start.ps1 -Api     Gemini/OpenAI-compatible API + rules; saves AI_MODE=api
#       scripts/start.ps1 -Model -AskApi   Qwen + rules for classification, external API for Q&A
#       scripts/start.ps1 -NoAi    disable AI and save AI_MODE=disabled
param([switch]$Reset, [switch]$Intelligence, [ValidateSet('servicedesk', 'roomrequest')][string]$AiProfile, [switch]$Model, [switch]$NoAi, [switch]$Api, [switch]$AskApi)
$ErrorActionPreference = 'Stop'
# PowerShell khong co tham so dang --reset; nhan them de ai quen cu phap bash van dung duoc
if ($args -contains '--reset') { $Reset = $true }
# Chon profile AI thi hieu la muon chay kem AI
if ($AiProfile) { $Intelligence = $true }

& "$PSScriptRoot/setup.ps1"
. "$PSScriptRoot/docker-native.ps1"
$explicitModes = @([bool]$Model, [bool]$NoAi, [bool]$Intelligence, [bool]$Api) | Where-Object { $_ }
if ($explicitModes.Count -gt 1) { throw 'Choose only one of -Model, -Api, -Intelligence/-AiProfile, or -NoAi.' }
$configuredMode = 'disabled'
Get-Content .env -Encoding UTF8 | ForEach-Object { if ($_ -match '^AI_MODE=(.*)$') { $configuredMode = $Matches[1] } }
if ($Model) { $configuredMode = 'model' } elseif ($Api) { $configuredMode = 'api' } elseif ($NoAi) { $configuredMode = 'disabled' } elseif ($Intelligence) { $configuredMode = 'rules' }
if ($configuredMode -notin @('disabled', 'rules', 'model', 'api')) { throw 'AI_MODE in .env must be disabled, rules, model, or api.' }
if ($AskApi -and $configuredMode -notin @('model', 'api')) { throw 'Use -Model -AskApi for Qwen classification plus API Q&A, or -Api for both tasks via API.' }
function Save-AiMode {
    $lines = @(Get-Content .env -Encoding UTF8 | Where-Object { $_ -notmatch '^AI_MODE=' })
    if ($Api -or $AskApi -or $NoAi) {
        $lines = @($lines | Where-Object { $_ -notmatch '^AI_ASSISTANCE_MODE=' })
        $lines += if ($NoAi) { 'AI_ASSISTANCE_MODE=disabled' } else { 'AI_ASSISTANCE_MODE=api' }
    }
    [IO.File]::WriteAllLines((Join-Path $PWD '.env'), [string[]]($lines + "AI_MODE=$configuredMode"), (New-Object Text.UTF8Encoding $false))
}
if ($configuredMode -in @('model', 'api')) {
    & "$PSScriptRoot/start-model.ps1" -Reset:$Reset -Api:($configuredMode -eq 'api') -AskApi:$AskApi
    if ($explicitModes.Count -gt 0 -or $AskApi) { Save-AiMode }
    return
}
$Intelligence = $configuredMode -eq 'rules'
# Co -f thi Compose khong tu nap compose.override.yaml nua nen ghi ro
$compose = @('compose', '-f', 'compose.yaml')
if (Test-Path compose.override.yaml) { $compose += @('-f', 'compose.override.yaml') }
if ($configuredMode -eq 'disabled') { $compose += @('-f', 'infra/compose/compose.ai-disabled.yaml') }
$previousToken = $env:INTELLIGENCE_TRIAGE_TOKEN
$previousUrl = $env:INTELLIGENCE_TRIAGE_BASE_URL
$previousProfileRoot = $env:INTELLIGENCE_PROFILE_ROOT
$previousProfiles = $env:INTELLIGENCE_REQUIRED_PROFILES
try {
    if ($Intelligence) {
        $compose += @('-f', 'infra/compose/compose.triage.yaml', '--profile', 'intelligence')
        if (-not $env:INTELLIGENCE_TRIAGE_TOKEN) {
            $configuration = Invoke-Docker @compose config --format json
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
    $resolvedConfiguration = Invoke-Docker @compose config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose config' }
    $resolvedProject = (($resolvedConfiguration -join "`n") | ConvertFrom-Json).name
    $previousPlatform = Invoke-Docker @compose ps -a -q platform
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the previous backend.' }
    Invoke-Docker @compose up -d --build --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Startup failed; inspect docker compose logs' }
    if ($Reset) {
        & "$PSScriptRoot/reset-keycloak-realm.ps1"
        Invoke-Docker @compose up -d --wait --wait-timeout 300
        if ($LASTEXITCODE -ne 0) { throw 'Cannot restore the selected AI mode after realm reset.' }
    }
    Sync-GatewayUpstream -ComposeArgs $compose -PreviousPlatform $previousPlatform
    & "$PSScriptRoot/stop-unused-model.ps1" -ProjectName $resolvedProject
    if ($configuredMode -eq 'disabled') {
        Invoke-Docker compose -p $resolvedProject -f compose.yaml -f infra/compose/compose.triage.yaml --profile intelligence stop intelligence
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stop the previously selected rules service.' }
    }
    if ($explicitModes.Count -gt 0) { Save-AiMode }
} finally {
    $env:INTELLIGENCE_TRIAGE_TOKEN = $previousToken
    $env:INTELLIGENCE_TRIAGE_BASE_URL = $previousUrl
    $env:INTELLIGENCE_PROFILE_ROOT = $previousProfileRoot
    $env:INTELLIGENCE_REQUIRED_PROFILES = $previousProfiles
}
