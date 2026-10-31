# Tao lich truc mau (tuan nay + tuan sau) cho tai khoan demo: nguoi lam hanh chinh, nguoi truc ca tuan va vai buoi.
# Chay lai khong trung. Can stack dang chay ban backend moi (dx up) va realm co user8-user13 (dx realm).
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. "$PSScriptRoot/docker-native.ps1"

if (-not (Invoke-Docker compose ps -q postgres)) { throw 'Postgres is not running. Run dx up first.' }

# Bang cua migration V40 chi co khi backend ban moi da khoi dong (Flyway chay luc start)
$ready = Invoke-Docker compose exec -T postgres psql -U core -d core -At -c "select to_regclass('hpdi_agent_work_mode') is not null"
if ("$ready".Trim() -ne 't') {
    throw 'Database is missing migration V40. Rebuild and restart the backend first: docker compose build platform, then dx up.'
}

# Mot transaction: loi thi khong ghi do dang. Ma thoat cua psql co the mat qua pipe, nen soat them dong ERROR
# PowerShell 5.1 + 'Stop' bien moi dong stderr cua psql thanh loi dung script: tam dung 'Continue'
$ErrorActionPreference = 'Continue'
$output = Get-Content scripts/demo/schedule-demo.sql -Raw |
    docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1 --single-transaction 2>&1
$ErrorActionPreference = 'Stop'
$output | ForEach-Object { Write-Host "$_" }
if ($LASTEXITCODE -ne 0 -or ($output | Where-Object { "$_" -match '^(psql:.*)?ERROR:' })) {
    throw 'Could not seed the demo schedule.'
}
Write-Host 'Demo schedule ready: this week and next week, Monday to Friday.'
