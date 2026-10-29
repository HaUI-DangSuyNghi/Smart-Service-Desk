# Invoked by start.ps1 after setup; no host Python/Java is needed.
param([switch]$Reset, [switch]$Api, [switch]$AskApi, [switch]$ValidateOnly, [string]$SettingsFile = '.env')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. "$PSScriptRoot/docker-native.ps1"
$settings = @{}
Get-Content -LiteralPath $SettingsFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([A-Z0-9_]+)=(.*)$') {
        $name = $Matches[1]; $value = $Matches[2]
        if ($value.Length -ge 2 -and (($value[0] -eq [char]34 -and $value[$value.Length-1] -eq [char]34) -or ($value[0] -eq [char]39 -and $value[$value.Length-1] -eq [char]39))) { $value = $value.Substring(1, $value.Length-2) }
        $settings[$name] = $value
    }
}
foreach ($profiles in @($settings['SPRING_PROFILES_ACTIVE'], $env:SPRING_PROFILES_ACTIVE)) {
    if ($profiles -match '(^|[,\s])(prod|production)([,\s]|$)') { throw 'Local model mode cannot use a production Spring profile.' }
}
function Get-TextDigest([string]$Value) {
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value))).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose() }
}
$assistanceMode = if ($settings['AI_ASSISTANCE_MODE']) { $settings['AI_ASSISTANCE_MODE'] } else { 'disabled' }
if ($assistanceMode -notin @('disabled', 'api')) { throw 'AI_ASSISTANCE_MODE must be disabled or api.' }
$useApi = $Api -or $AskApi -or $assistanceMode -eq 'api'
$runtimeDirectory = if ($ValidateOnly) { '.local/model/validation-' + [guid]::NewGuid().ToString('N').Substring(0,12) } else { '.local/model' }
New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
$extraRuntime = @()
$providerLabel = 'Qwen'
$providerId = 'qwen35-08b-lora-labops-silver-r1'
$deadline = 4000
$service = 'ai07-qwen'
$overlay = 'infra/compose/compose.model-local.yaml'
if ($useApi) {
    $vendor = $settings['AI_API_PROVIDER']
    if ($vendor -notin @('openai', 'groq', 'gemini')) { throw 'Set AI_API_PROVIDER to openai, groq, or gemini in .env.' }
    $defaults = @{openai='https://api.openai.com/v1'; groq='https://api.groq.com/openai/v1'; gemini='https://generativelanguage.googleapis.com/v1beta'}
    $base = if ($settings['AI_API_BASE_URL']) { $settings['AI_API_BASE_URL'].TrimEnd('/') } else { $defaults[$vendor] }
    $uri = $null
    if (-not [Uri]::TryCreate($base, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) { throw 'AI_API_BASE_URL must be an HTTPS API base URL without credentials or query parameters.' }
    $apiModel = $settings['AI_API_MODEL']
    if ($apiModel -notmatch '^[A-Za-z0-9_.:/-]{1,150}$') { throw 'Set AI_API_MODEL to a model available to your API account.' }
    $keyName = @{openai='OPENAI_API_KEY';groq='GROQ_API_KEY';gemini='GEMINI_API_KEY'}[$vendor]
    $providerKey = [Environment]::GetEnvironmentVariable($keyName)
    $providerKeyFile = [Environment]::GetEnvironmentVariable($keyName + '_FILE')
    $key = if ($env:AI_API_KEY) { $env:AI_API_KEY } elseif ($settings['AI_API_KEY']) { $settings['AI_API_KEY'] } elseif ($providerKey) { $providerKey } else { $settings[$keyName] }
    $keySource = if ($env:AI_API_KEY_FILE) { $env:AI_API_KEY_FILE } elseif ($settings['AI_API_KEY_FILE']) { $settings['AI_API_KEY_FILE'] } elseif ($providerKeyFile) { $providerKeyFile } else { $settings[$keyName + '_FILE'] }
    if ($keySource) {
        $source = Get-Item -LiteralPath $keySource
        if ($source.PSIsContainer -or $source.Length -gt 4096) { throw 'API key file is invalid.' }
        $key = [IO.File]::ReadAllText($source.FullName).Trim()
    }
    $keyFile = $runtimeDirectory + '/api-key-' + $vendor
    if (-not $key -and (Test-Path -LiteralPath $keyFile)) { $key = [IO.File]::ReadAllText((Join-Path $PWD $keyFile)).Trim() }
    if (-not $key -or $key.Length -lt 8 -or $key.Length -gt 4096 -or $key -match '[^\x21-\x7E]') { throw 'Set AI_API_KEY or AI_API_KEY_FILE (or the selected provider key) privately. Keys are never printed or sent to the frontend.' }
    [IO.File]::WriteAllText((Join-Path $PWD $keyFile), $key, [Text.Encoding]::ASCII)
    $key = $null
    $format = if ($settings['AI_API_RESPONSE_FORMAT']) { $settings['AI_API_RESPONSE_FORMAT'] } else { 'json_object' }
    if ($format -notin @('json_object', 'json_schema')) { throw 'AI_API_RESPONSE_FORMAT must be json_object or json_schema.' }
    $maxTokens = if ($settings['AI_API_MAX_TOKENS']) { [int]$settings['AI_API_MAX_TOKENS'] } else { 1024 }
    if ($maxTokens -lt 64 -or $maxTokens -gt 2048) { throw 'AI_API_MAX_TOKENS must be 64..2048.' }
    $apiModelDigest = Get-TextDigest ($vendor + "`n" + $base + "`n" + $apiModel)
    $apiPolicyDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath 'intelligence/src/apps/servicedesk/intelligence/api-classifier-policy-v0.1.json').Hash.ToLowerInvariant()
    $advicePolicyDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath 'intelligence/src/apps/servicedesk/intelligence/api-assistance-policy-v0.1.json').Hash.ToLowerInvariant()
    $adviceTokens = if ($settings['AI_ASSISTANCE_MAX_TOKENS']) { [int]$settings['AI_ASSISTANCE_MAX_TOKENS'] } else { 2048 }
    if ($adviceTokens -lt 64 -or $adviceTokens -gt 2048) { throw 'AI_ASSISTANCE_MAX_TOKENS must be 64..2048.' }
    $extraRuntime = @("SSD_AI_API_PROVIDER=$vendor", "SSD_AI_API_BASE_URL=$base", "SSD_AI_API_MODEL=$apiModel", "SSD_AI_API_KEY_FILE=./$keyFile", "SSD_AI_API_RESPONSE_FORMAT=$format", "SSD_AI_API_MAX_TOKENS=$maxTokens", "SSD_API_MODEL_DIGEST=$apiModelDigest", "SSD_API_POLICY_DIGEST=$apiPolicyDigest", "SSD_AI_ASSISTANCE_POLICY_DIGEST=$advicePolicyDigest", "SSD_AI_ASSISTANCE_MAX_TOKENS=$adviceTokens")
    Write-Host 'API Q&A selected: questions and ticket title/description go to the configured provider; keys stay server-side.'
}
if ($Api) {
    $modelDigest = $apiModelDigest
    $policyDigest = $apiPolicyDigest
    $codeImage = ''
    $image = [PSCustomObject]@{Id=''}
    $providerLabel = $vendor + ' API'
    $providerId = $vendor + '-api'
    $deadline = 15000
    $service = 'intelligence-api'
    $overlay = 'infra/compose/compose.model-api.yaml'
} else {
$modelImage = if ($env:LOCAL_MODEL_IMAGE) { $env:LOCAL_MODEL_IMAGE } elseif ($settings['LOCAL_MODEL_IMAGE']) { $settings['LOCAL_MODEL_IMAGE'] } else { 'ssd-ai07-qwen-full:a40d655f95f8ed6b' }
$metadata = $null
try { $metadata = Invoke-Docker image inspect $modelImage --format '{{json .}}' 2>$null } catch { $metadata = $null }
if (-not $metadata -or $LASTEXITCODE -ne 0) { throw "Model image '$modelImage' is missing. Load the team's embedded-model image with docker load -i <image.tar>, then set LOCAL_MODEL_IMAGE in .env. See docs/local-model.md." }
$image = ($metadata -join "`n") | ConvertFrom-Json
$labels = $image.Config.Labels
$modelDigest = $labels.'vn.haui.ssd.model-digest'
$policyDigest = $labels.'vn.haui.ssd.policy-digest'
$codeImage = $labels.'vn.haui.ssd.code-image-id'
if ($modelDigest -notmatch '^[a-f0-9]{64}$' -or $policyDigest -notmatch '^[a-f0-9]{64}$' -or $codeImage -notmatch '^sha256:[a-f0-9]{64}$' -or $image.Os -ne 'linux') {
    throw 'Use a verified Qwen image containing /opt/model and /opt/apps with the three model identity labels.'
}
Write-Host 'Checking NVIDIA GPU access...'
$gpuArguments = @('run', '--rm', '--network', 'none', '--gpus', 'all', '--entrypoint', 'python', $image.Id, '-c', 'import torch; assert torch.cuda.is_available(); print(torch.cuda.get_device_name(0))')
Invoke-Docker @gpuArguments
if ($LASTEXITCODE -ne 0) { throw 'Qwen requires NVIDIA GPU access from Docker. Check Docker Desktop GPU support and free VRAM.' }
}
$policyRoot = 'backend/src/main/resources/apps/servicedesk/intelligence'
function Get-PolicyDigest([string]$Name) { return (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $policyRoot $Name)).Hash.ToLowerInvariant() }
$assessmentDigest = Get-TextDigest ((Get-PolicyDigest 'taxonomy-v0.1.json') + ':' + (Get-PolicyDigest 'assessment-policy-v0.1.json') + ':' + (Get-PolicyDigest 'triage-policy.json'))
$contextEntries = @('context-slots-v0.1.json', 'enrichment-questions-v0.1.json', 'duplicate-policy-v0.1.json', 'taxonomy-v0.1.json', 'assessment-policy-v0.1.json') | ForEach-Object { $_ + '=' + (Get-PolicyDigest $_) }
$contextDigest = Get-TextDigest ('{' + ($contextEntries -join ', ') + '}')
$contextualDigest = Get-TextDigest ((Get-PolicyDigest 'contextual-input-policy-v0.1.json') + ':' + (Get-PolicyDigest 'guard-policy-v0.1.json') + ':' + $contextDigest + ':' + $assessmentDigest + ':' + $modelDigest + ':' + $policyDigest)
$workspaces = if ($settings['LOCAL_MODEL_WORKSPACES']) { $settings['LOCAL_MODEL_WORKSPACES'] } else { 'auto' }
if ($workspaces -eq 'auto') { $workspaces = 'auto' }
if ($workspaces -ne 'auto' -and $workspaces -notmatch '^[a-zA-Z0-9_-]{1,100}(,[a-zA-Z0-9_-]{1,100})*$') { throw 'LOCAL_MODEL_WORKSPACES must be auto or a comma-separated list of workspace IDs.' }
if ($workspaces -ne 'auto' -and $workspaces.Split(',') -contains 'auto') { throw 'auto cannot be combined with an explicit workspace list.' }
if ($workspaces -eq 'auto') {
    $bootstrapToken = $runtimeDirectory + '/workspace-bootstrap-token'
    if (-not (Test-Path -LiteralPath $bootstrapToken)) {
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        [IO.File]::WriteAllText((Join-Path $PWD $bootstrapToken), [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant(), [Text.Encoding]::ASCII)
    }
    if ([IO.File]::ReadAllText((Join-Path $PWD $bootstrapToken)) -notmatch '^[a-f0-9]{64}$') { throw 'Invalid workspace bootstrap token.' }
    $adminPassword = $settings['KC_ADMIN_PASSWORD']
    if (-not $adminPassword) {
        Get-Content .env -Encoding UTF8 | ForEach-Object { if ($_ -match '^KC_ADMIN_PASSWORD=(.*)$') { $adminPassword=$Matches[1] } }
        if ($adminPassword.Length -ge 2 -and $adminPassword[0] -in @([char]34,[char]39) -and $adminPassword[0] -eq $adminPassword[$adminPassword.Length-1]) { $adminPassword=$adminPassword.Substring(1,$adminPassword.Length-2) }
    }
    if (-not $adminPassword) { throw 'Automatic workspace setup requires KC_ADMIN_PASSWORD in the local .env.' }
    $bootstrapAdminFile = $runtimeDirectory + '/workspace-keycloak-admin'
    [IO.File]::WriteAllText((Join-Path $PWD $bootstrapAdminFile), $adminPassword, (New-Object Text.UTF8Encoding $false))
    $adminPassword = $null
    $bootstrapDigest = Get-TextDigest ((Get-FileHash -Algorithm SHA256 -LiteralPath 'scripts/intelligence/workspace-bootstrap.py').Hash.ToLowerInvariant() + ':' + (Get-FileHash -Algorithm SHA256 -LiteralPath 'infra/keycloak/automatic-workspaces-policy.json').Hash.ToLowerInvariant())
    $extraRuntime += @("SSD_WORKSPACE_BOOTSTRAP_TOKEN_FILE=./$bootstrapToken", "SSD_WORKSPACE_KEYCLOAK_ADMIN_FILE=./$bootstrapAdminFile", "SSD_WORKSPACE_BOOTSTRAP_CODE_DIGEST=$bootstrapDigest")
}
$tokenRelativePath = $runtimeDirectory + '/classification-token'
$tokenPath = Join-Path $PWD $tokenRelativePath
if (-not (Test-Path -LiteralPath $tokenPath)) {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    [IO.File]::WriteAllText($tokenPath, [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant(), [Text.Encoding]::ASCII)
}
if (([IO.File]::ReadAllText($tokenPath)) -notmatch '^[a-f0-9]{64}$') { throw 'Local model token is invalid; restore it or remove only .local/model/classification-token to generate a new token.' }
$runtime = @(
    "SSD_LOCAL_MODEL_IMAGE=$($image.Id)", "SSD_LOCAL_MODEL_DIGEST=$modelDigest", "SSD_LOCAL_MODEL_POLICY_DIGEST=$policyDigest",
    "SSD_LOCAL_CODE_IMAGE=$codeImage", "SSD_LOCAL_ASSESSMENT_DIGEST=$assessmentDigest", "SSD_LOCAL_CONTEXT_DIGEST=$contextDigest",
    "SSD_LOCAL_CONTEXTUAL_DIGEST=$contextualDigest", "SSD_LOCAL_WORKSPACES=$workspaces",
    "SSD_LOCAL_BACKGROUND_WORKSPACES=$(if ($workspaces -eq 'auto') { if ($settings['SERVICE_DESK_BACKGROUND_WORKSPACES']) { $settings['SERVICE_DESK_BACKGROUND_WORKSPACES'] } else { 'lab-a' } } else { $workspaces })",
    "SSD_LOCAL_PROVIDER_LABEL=$providerLabel", "SSD_LOCAL_PROVIDER_ID=$providerId", "SSD_CLASSIFICATION_TIMEOUT_MS=$deadline",
    "SSD_AI_CLASSIFICATION_TOKEN_FILE=./$tokenRelativePath"
) + $extraRuntime
$runtimeFile = $runtimeDirectory + '/runtime.env'
[IO.File]::WriteAllLines((Join-Path $PWD $runtimeFile), [string[]]$runtime, (New-Object Text.UTF8Encoding $false))
$compose = @('compose', '--env-file', '.env', '--env-file', $runtimeFile, '-f', 'compose.yaml')
if (Test-Path compose.override.yaml) { $compose += @('-f', 'compose.override.yaml') }
$compose += @('-f', 'infra/compose/compose.triage.yaml', '-f', 'infra/compose/compose.hybrid.yaml')
if ($workspaces -eq 'auto') { $compose += @('-f', 'infra/compose/compose.workspaces-auto.yaml') }
if ($useApi) { $compose += @('-f', 'infra/compose/compose.api-assistance.yaml') }
$compose += @('-f', $overlay, '--profile', 'intelligence', '--profile', 'local-model')
$resolvedConfiguration = Invoke-Docker @compose config --format json
if ($LASTEXITCODE -ne 0) { throw 'Local model Compose configuration is invalid.' }
$resolvedProject = (($resolvedConfiguration -join "`n") | ConvertFrom-Json).name
if ($ValidateOnly) {
    Write-Host "Hybrid AI configuration valid: $runtimeFile. No application containers were changed and no upstream request was sent."
    return $runtimeFile
}
$requiredServices = if ($useApi -and -not $Api) { @($service, 'intelligence-api') } else { @($service) }
if ($workspaces -eq 'auto') { $requiredServices += 'workspace-bootstrap' }
& "$PSScriptRoot/stop-unused-model.ps1" -KeepService $requiredServices -ProjectName $resolvedProject
if ($useApi) {
    Invoke-Docker @compose build intelligence-api
    if ($LASTEXITCODE -ne 0) { throw 'Cannot build the API classification service.' }
}
Write-Host 'Starting the selected classification provider. GPU warmup may take several minutes...'
$existingModel = Invoke-Docker @compose ps -a -q $service
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the local model service.' }
if ($existingModel) {
    $health = Invoke-Docker inspect $existingModel --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}'
    if ($health -eq 'unhealthy') {
        Invoke-Docker @compose restart $service
        if ($LASTEXITCODE -ne 0) { throw 'Cannot restart the unhealthy local model worker.' }
    }
}
Invoke-Docker @compose up -d --no-build --wait --wait-timeout 300 @requiredServices
if ($LASTEXITCODE -ne 0) { throw 'The classification provider did not become ready. Check its configuration and restart; application data is preserved.' }
$previousPlatform = Invoke-Docker @compose ps -a -q platform
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the previous backend.' }
Invoke-Docker @compose up -d --build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { throw 'Application startup failed. Inspect docker compose logs platform; the selected model configuration is in .local/model/runtime.env.' }
if ($Reset) {
    & "$PSScriptRoot/reset-keycloak-realm.ps1"
    Invoke-Docker @compose up -d --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restore model configuration after realm reset.' }
}
Sync-GatewayUpstream -ComposeArgs $compose -PreviousPlatform $previousPlatform
Write-Host "Hybrid AI ready: $providerLabel + rules, factual priority and context/enrichment."
if ($useApi) { Write-Host 'API Q&A enabled. Healthcheck verifies configuration; no billable upstream probe is sent at startup.' }
