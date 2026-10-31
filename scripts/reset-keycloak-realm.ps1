# Xoa realm dx-lab trong Keycloak dang chay roi import lai tu .local/dx-lab-realm.json.
# Dung khi realm cu (thieu client dx-web, user, group moi): --import-realm bo qua realm da ton tai.
# Chi xoa realm dx-lab; realm master, du lieu Postgres cua core va volume khac giu nguyen.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. "$PSScriptRoot/docker-native.ps1"

# Sinh lai file realm tu infra/keycloak/realm-template.json
& "$PSScriptRoot/setup.ps1"

if (-not (Invoke-Docker compose ps -q keycloak)) { throw 'Keycloak is not running. Run scripts/start.ps1 first.' }

Write-Host 'Deleting realm dx-lab...'
$kcadm = '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password $KC_BOOTSTRAP_ADMIN_PASSWORD >/dev/null && /opt/keycloak/bin/kcadm.sh delete realms/dx-lab'
Invoke-Docker compose exec -T keycloak sh -c $kcadm
if ($LASTEXITCODE -ne 0) { Write-Host 'Realm dx-lab not found or not deleted; continuing with import.' }

Write-Host 'Restarting Keycloak to import realm...'
Invoke-Docker compose restart keycloak | Out-Null
Invoke-Docker compose up -d --wait --wait-timeout 300 keycloak
if ($LASTEXITCODE -ne 0) { throw 'Keycloak did not become healthy; inspect docker compose logs keycloak' }

if (-not (Invoke-Docker compose logs --since 5m keycloak | Select-String "Realm 'dx-lab' imported")) {
    throw "Realm dx-lab was not imported; inspect docker compose logs keycloak"
}

# Realm moi co khoa ky moi: khoi dong lai backend de nap lai JWKS
Invoke-Docker compose restart platform | Out-Null
Invoke-Docker compose up -d --wait --wait-timeout 300 platform
if ($LASTEXITCODE -ne 0) { throw 'Platform did not become healthy; inspect docker compose logs platform' }

Write-Host 'Realm dx-lab re-imported. Demo password: DEMO_PASSWORD in .env'
