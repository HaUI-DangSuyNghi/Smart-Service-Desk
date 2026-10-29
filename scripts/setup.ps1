$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

# Moi bi mat sinh ngau nhien vao .env (Git bo qua). May da co .env thi chi bo sung bien con thieu,
# khong doi bien da co (doi mat khau Postgres sau khi tao DB se lam backend mat ket noi).
# INTELLIGENCE_TRIAGE_TOKEN: backend dung de goi service AI noi bo (start.ps1 chay kem AI)
$secretKeys = @('POSTGRES_PASSWORD', 'KC_ADMIN_PASSWORD', 'DEMO_PASSWORD', 'DIRECTORY_CLIENT_SECRET', 'INTELLIGENCE_TRIAGE_TOKEN', 'BACKGROUND_CLIENT_SECRET')
$existing = if (Test-Path .env) { @(Get-Content .env -Encoding UTF8) } else { @() }
$present = @{}
foreach ($line in $existing) {
    if ($line -match '^([A-Z_]+)=(.+)$') { $present[$Matches[1]] = $true }
}
$missing = @($secretKeys | Where-Object { -not $present[$_] })
if ($missing.Count -gt 0) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    # Bo dong "KEY=" de trong (vd copy tu .env.example) truoc khi ghi gia tri moi
    $kept = @($existing | Where-Object { -not ($_ -match '^([A-Z_]+)=$' -and $missing -contains $Matches[1]) })
    $added = foreach ($key in $missing) {
        $bytes = New-Object byte[] 32
        $rng.GetBytes($bytes)
        "$key=$([BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant())"
    }
    $rng.Dispose()
    [IO.File]::WriteAllLines((Join-Path $PWD '.env'), [string[]]($kept + $added), (New-Object Text.UTF8Encoding $false))
    Write-Host "Generated in .env: $($missing -join ', ')"
}

$config = @{}
Get-Content .env -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.+)$') { $config[$Matches[1]] = $Matches[2] }
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
