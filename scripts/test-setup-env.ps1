# Isolated setup regressions: never read the workstation .env or contact Docker.
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$testRoot = Join-Path $repository ('.local/setup-env-tests/' + [guid]::NewGuid().ToString('N'))
$utf8 = New-Object Text.UTF8Encoding $false
$internalKeys = @('POSTGRES_PASSWORD','KC_ADMIN_PASSWORD','DEMO_PASSWORD','DIRECTORY_CLIENT_SECRET','INTELLIGENCE_TRIAGE_TOKEN','BACKGROUND_CLIENT_SECRET')

function Assert-Setup([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function New-Fixture([string]$Name, [switch]$WithoutTemplate) {
    $directory = Join-Path $testRoot $Name
    New-Item -ItemType Directory -Force -Path (Join-Path $directory 'scripts'),(Join-Path $directory 'infra/keycloak') | Out-Null
    Copy-Item -LiteralPath (Join-Path $repository 'scripts/setup.ps1') -Destination (Join-Path $directory 'scripts/setup.ps1')
    Copy-Item -LiteralPath (Join-Path $repository 'infra/keycloak/realm-template.json') -Destination (Join-Path $directory 'infra/keycloak/realm-template.json')
    if (-not $WithoutTemplate) { Copy-Item -LiteralPath (Join-Path $repository '.env.example') -Destination (Join-Path $directory '.env.example') }
    return $directory
}

function Invoke-Fixture([string]$Directory) {
    Push-Location $Directory
    try { & (Join-Path $Directory 'scripts/setup.ps1') | Out-Null }
    finally { Pop-Location }
}

function Read-Settings([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            Assert-Setup (-not $values.ContainsKey($Matches[1])) 'Setup must not duplicate environment keys.'
            $values[$Matches[1]] = $Matches[2]
        }
    }
    return $values
}

function Assert-Complete([string]$Directory) {
    $template = Read-Settings (Join-Path $Directory '.env.example')
    $values = Read-Settings (Join-Path $Directory '.env')
    foreach ($key in $template.Keys) { Assert-Setup ($values.ContainsKey($key)) 'Every template key must be present in .env.' }
    return $values
}

$fresh = New-Fixture 'fresh'
Invoke-Fixture $fresh
$values = Assert-Complete $fresh
$defaults = Read-Settings (Join-Path $fresh '.env.example')
foreach ($key in $defaults.Keys) {
    if ($key -notin $internalKeys) { Assert-Setup ($values[$key] -ceq $defaults[$key]) 'Fresh non-secret defaults must match the template.' }
}
foreach ($key in $internalKeys) { Assert-Setup ($values[$key] -cmatch '^[a-f0-9]{64}$') 'Missing internal credentials must be generated.' }
Assert-Setup (@($internalKeys | ForEach-Object { $values[$_] } | Sort-Object -Unique).Count -eq $internalKeys.Count) 'Internal credentials must be distinct.'
Assert-Setup ($values['AI_API_KEY'] -eq '') 'Setup cannot fabricate a provider API key.'
$contents = [IO.File]::ReadAllText((Join-Path $fresh '.env'))
foreach ($line in Get-Content (Join-Path $fresh '.env.example') -Encoding UTF8) {
    if ($line.StartsWith('#')) { Assert-Setup ($contents.Contains($line)) 'Fresh .env must retain template comments.' }
}
$bytes = [IO.File]::ReadAllBytes((Join-Path $fresh '.env'))
Assert-Setup (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191)) 'Generated .env must be UTF-8 without BOM.'
$realm = Get-Content (Join-Path $fresh '.local/dx-lab-realm.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Setup ((@($realm.users | Where-Object username -eq 'user1')[0]).credentials[0].value -ceq $values['DEMO_PASSWORD']) 'Realm password must match the generated environment.'
$freshHash = (Get-FileHash -Algorithm SHA256 (Join-Path $fresh '.env')).Hash
Invoke-Fixture $fresh
Assert-Setup ((Get-FileHash -Algorithm SHA256 (Join-Path $fresh '.env')).Hash -ceq $freshHash) 'Repeated setup must not rotate secrets or rewrite .env.'

$existing = New-Fixture 'existing'
$original = @(
    '# Cấu hình riêng giữ nguyên', 'POSTGRES_PASSWORD=SYNTHETIC_EXISTING_DATABASE_PASSWORD',
    'KC_ADMIN_PASSWORD=SYNTHETIC_EXISTING_ADMIN_PASSWORD', 'DEMO_PASSWORD="SYNTHETIC_QUOTED_DEMO_PASSWORD"',
    'DIRECTORY_CLIENT_SECRET=SYNTHETIC_DIRECTORY_SECRET', 'BACKGROUND_CLIENT_SECRET=SYNTHETIC_BACKGROUND_SECRET',
    'INTELLIGENCE_TRIAGE_TOKEN=SYNTHETIC_EXISTING_TRIAGE_TOKEN_123456',
    'AI_MODE=model', 'AI_ASSISTANCE_MODE=api', 'AI_API_PROVIDER=openai',
    'AI_API_MODEL=synthetic-custom-model', 'AI_API_KEY=SYNTHETIC_EXISTING_API_KEY',
    'AI_API_KEY_FILE=', 'AI_API_MAX_TOKENS=', 'AI_API_RESPONSE_FORMAT=json_schema',
    'LOCAL_MODEL_IMAGE=synthetic-custom-image:dev', 'LOCAL_MODEL_WORKSPACES=lab-a,lab-b',
    'CUSTOM_SETTING_2=giữ nguyên'
)
[IO.File]::WriteAllLines((Join-Path $existing '.env'), [string[]]$original, $utf8)
$previous = Read-Settings (Join-Path $existing '.env')
Invoke-Fixture $existing
$merged = Assert-Complete $existing
foreach ($key in $previous.Keys) { Assert-Setup ($merged[$key] -ceq $previous[$key]) 'Existing values, including blank optional values and unknown keys, must survive migration.' }
$updated = @(Get-Content (Join-Path $existing '.env') -Encoding UTF8)
for ($index=0; $index -lt $original.Count; $index++) { Assert-Setup ($updated[$index] -ceq $original[$index]) 'Existing comments and UTF-8 lines must be retained.' }
$existingRealm = Get-Content (Join-Path $existing '.local/dx-lab-realm.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Setup ((@($existingRealm.users | Where-Object username -eq 'user1')[0]).credentials[0].value -ceq $merged['DEMO_PASSWORD'].Trim('"')) 'Quoted credentials must be decoded correctly for realm configuration without changing .env.'

# A new template variable must be picked up without a hardcoded setup change.
$templatePath = Join-Path $existing '.env.example'
[IO.File]::AppendAllText($templatePath, "`r`n# Future template entry`r`nFUTURE_AI_SETTING_2=enabled`r`n", $utf8)
Invoke-Fixture $existing
$evolved = Assert-Complete $existing
Assert-Setup ($evolved['FUTURE_AI_SETTING_2'] -ceq 'enabled') 'New template entries must propagate automatically.'
foreach ($key in $previous.Keys) { Assert-Setup ($evolved[$key] -ceq $previous[$key]) 'Template updates must not replace workstation values.' }
$existingHash = (Get-FileHash -Algorithm SHA256 (Join-Path $existing '.env')).Hash
Invoke-Fixture $existing
Assert-Setup ((Get-FileHash -Algorithm SHA256 (Join-Path $existing '.env')).Hash -ceq $existingHash) 'Migrated setup must remain idempotent.'

$empty = New-Fixture 'empty-internal-secret'
[IO.File]::WriteAllLines((Join-Path $empty '.env'), [string[]]@('DEMO_PASSWORD=""', 'AI_API_KEY=', 'AI_MODE=rules'), $utf8)
Invoke-Fixture $empty
$filled = Assert-Complete $empty
Assert-Setup ($filled['DEMO_PASSWORD'] -cmatch '^[a-f0-9]{64}$') 'An empty quoted internal secret must be generated.'
Assert-Setup ($filled['AI_API_KEY'] -eq '' -and $filled['AI_MODE'] -eq 'rules') 'Optional provider key and existing mode must not be changed.'

$noTemplate = New-Fixture 'missing-template' -WithoutTemplate
[IO.File]::WriteAllText((Join-Path $noTemplate '.env'), 'AI_MODE=model', $utf8)
$noTemplateHash = (Get-FileHash -Algorithm SHA256 (Join-Path $noTemplate '.env')).Hash
$failed = $false
try { Invoke-Fixture $noTemplate } catch { $failed = $true }
Assert-Setup $failed 'Missing template must fail explicitly.'
Assert-Setup ((Get-FileHash -Algorithm SHA256 (Join-Path $noTemplate '.env')).Hash -ceq $noTemplateHash) 'Missing template must not modify the existing .env.'
Assert-Setup (-not (Test-Path (Join-Path $noTemplate '.local/dx-lab-realm.json'))) 'Missing template must not write realm configuration.'

'PASS: fresh template, defaults/comments, credential generation, rerun stability, existing settings, future template keys, blank secrets and missing-template protection.'
