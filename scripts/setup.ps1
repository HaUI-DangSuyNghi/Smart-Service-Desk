$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path .env)) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $lines = foreach ($key in @('POSTGRES_PASSWORD', 'KC_ADMIN_PASSWORD', 'DEMO_PASSWORD')) {
        $bytes = New-Object byte[] 32
        $rng.GetBytes($bytes)
        "$key=$([BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant())"
    }
    [IO.File]::WriteAllLines((Join-Path $PWD '.env'), $lines)
    $rng.Dispose()
}
$config = @{}
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.+)$') { $config[$Matches[1]] = $Matches[2] }
}
foreach ($key in @('POSTGRES_PASSWORD', 'KC_ADMIN_PASSWORD', 'DEMO_PASSWORD')) {
    if (!$config[$key]) { throw "Missing $key in .env" }
}
$realm = Get-Content -LiteralPath 'infra/keycloak/realm-template.json' -Raw | ConvertFrom-Json
foreach ($user in $realm.users) {
    $user | Add-Member -NotePropertyName credentials -NotePropertyValue @(@{type='password'; value=$config.DEMO_PASSWORD; temporary=$false})
}
New-Item -ItemType Directory -Path .local -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $PWD '.local/dx-lab-realm.json'), ($realm | ConvertTo-Json -Depth 20))
Write-Host 'Local secrets and realm prepared. Existing Keycloak realms are not overwritten by import.'
