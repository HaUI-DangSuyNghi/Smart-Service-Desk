# Dung: scripts/start.ps1           khoi dong toan bo he thong
#       scripts/start.ps1 -Reset    them buoc xoa va import lai realm Keycloak dx-lab (cung nhan --reset)
param([switch]$Reset)
$ErrorActionPreference = 'Stop'
# PowerShell khong co tham so dang --reset; nhan them de ai quen cu phap bash van dung duoc
if ($args -contains '--reset') { $Reset = $true }

& "$PSScriptRoot/setup.ps1"
docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose config' }
docker compose up -d --build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { throw 'Startup failed; inspect docker compose logs' }
if ($Reset) { & "$PSScriptRoot/reset-keycloak-realm.ps1" }
