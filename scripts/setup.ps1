$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

# .env.example is the source of defaults; preserve workstation values on every run.
$templatePath = Join-Path $PWD '.env.example'
if (-not (Test-Path -LiteralPath $templatePath -PathType Leaf)) { throw 'Missing .env.example; restore the template before running setup.' }
$template = @(Get-Content -LiteralPath $templatePath -Encoding UTF8)
$envExists = Test-Path -LiteralPath '.env' -PathType Leaf
$envLines = if ($envExists) { @(Get-Content .env -Encoding UTF8) } else { @($template) }
$knownNames = @{}
foreach ($line in $envLines) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $knownNames[$Matches[1]] = $Matches[2] }
}
$addedDefaults = @()
foreach ($line in $template) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$' -and -not $knownNames.ContainsKey($Matches[1])) {
        $knownNames[$Matches[1]] = $Matches[2]
        $addedDefaults += $line
    }
}
if ($addedDefaults.Count -gt 0) {
    $envLines += @('', '# Defaults added from .env.example; existing values are preserved.') + $addedDefaults
}

function Get-EnvValue([string]$Value) {
    if ($Value.Length -ge 2 -and (($Value[0] -eq [char]34 -and $Value[$Value.Length-1] -eq [char]34) -or ($Value[0] -eq [char]39 -and $Value[$Value.Length-1] -eq [char]39))) {
        return $Value.Substring(1, $Value.Length-2)
    }
    return $Value
}

# Generate only missing/empty internal credentials, never a provider API key.
# Existing database passwords must not change after the database has been created.
$secretKeys = @('POSTGRES_PASSWORD', 'KC_ADMIN_PASSWORD', 'DEMO_PASSWORD', 'DIRECTORY_CLIENT_SECRET', 'INTELLIGENCE_TRIAGE_TOKEN', 'BACKGROUND_CLIENT_SECRET')
$missing = @($secretKeys | Where-Object { [string]::IsNullOrWhiteSpace((Get-EnvValue $knownNames[$_])) })
if ($missing.Count -gt 0) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generated = @{}
    try {
        foreach ($key in $missing) {
            $bytes = New-Object byte[] 32
            $rng.GetBytes($bytes)
            $generated[$key] = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        }
    } finally { $rng.Dispose() }
    $envLines = @($envLines | ForEach-Object {
        if ($_ -match '^([A-Z][A-Z0-9_]*)=' -and $generated.ContainsKey($Matches[1])) {
            $Matches[1] + '=' + $generated[$Matches[1]]
        } else { $_ }
    })
    foreach ($key in $missing) {
        if (-not $knownNames.ContainsKey($key)) { $envLines += $key + '=' + $generated[$key] }
    }
}
if (-not $envExists -or $addedDefaults.Count -gt 0 -or $missing.Count -gt 0) {
    [IO.File]::WriteAllLines((Join-Path $PWD '.env'), [string[]]$envLines, (New-Object Text.UTF8Encoding $false))
}
if (-not $envExists) { Write-Host 'Created .env from .env.example.' }
if ($addedDefaults.Count -gt 0) { Write-Host "Added $($addedDefaults.Count) missing settings from .env.example." }
if ($missing.Count -gt 0) { Write-Host "Generated internal credentials in .env: $($missing -join ', ')" }

$config = @{}
foreach ($line in $envLines) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $config[$Matches[1]] = Get-EnvValue $Matches[2] }
}
foreach ($key in $secretKeys) {
    if (!$config[$key]) { throw "Missing $key in .env" }
}

# Template co chu tieng Viet (ten workspace): doc/ghi UTF-8, PowerShell 5.1 mac dinh doc theo ANSI
$realm = Get-Content -LiteralPath 'infra/keycloak/realm-template.json' -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($user in $realm.users) {
    # Service account khong dang nhap bang mat khau
    if ($user.serviceAccountClientId) { continue }
    $user | Add-Member -NotePropertyName credentials -NotePropertyValue @(@{type='password'; value=$config.DEMO_PASSWORD; temporary=$false})
}
foreach ($client in $realm.clients) {
    if ($client.clientId -eq 'dx-directory') { $client.secret = $config.DIRECTORY_CLIENT_SECRET }
    if ($client.clientId -eq 'dx-assessment-worker') { $client.secret = $config.BACKGROUND_CLIENT_SECRET }
}
New-Item -ItemType Directory -Path .local -Force | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding $false
[IO.File]::WriteAllText((Join-Path $PWD '.local/dx-lab-realm.json'), ($realm | ConvertTo-Json -Depth 20), $utf8)
Write-Host 'Local secrets and realm prepared. Existing Keycloak realms are not overwritten by import (use scripts/start.ps1 -Reset).'
