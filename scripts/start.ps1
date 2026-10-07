$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/setup.ps1"
docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose config' }
docker compose up -d --build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { throw 'Startup failed; inspect docker compose logs' }
