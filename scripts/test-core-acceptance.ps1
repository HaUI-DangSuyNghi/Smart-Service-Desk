param(
    [ValidatePattern('^acceptance-[a-z0-9-]{1,40}$')][string]$Project = "acceptance-$([guid]::NewGuid().ToString('N').Substring(0,12))",
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$restore = "$Project-restore"
$evidence = Join-Path $PWD ".local/$Project"
$results = [ordered]@{postgresql='NOT VERIFIED'; contracts='NOT VERIFIED'; cleanE2E='NOT VERIFIED'; ticketFoundation='NOT VERIFIED'; independentSecurityAndReuse='NOT VERIFIED'; uploadDisabled='NOT VERIFIED'; restart='NOT VERIFIED'; storageInventory='NOT VERIFIED'; backupRestore='NOT VERIFIED'}
function Run-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: $($args[0]) $($args[1])" }
}
function Run-Python {
    & $Python @args
    if ($LASTEXITCODE -ne 0) { throw "Python verification failed: $($args -join ' ')" }
}
foreach ($name in @($Project, $restore)) {
    if (Run-Docker ps -aq --filter "label=com.docker.compose.project=$name") { throw "Refusing existing project $name" }
    if (Run-Docker volume ls -q --filter "label=com.docker.compose.project=$name") { throw "Refusing existing volumes for $name" }
}
if (Test-Path -LiteralPath $evidence) { throw 'Evidence directory already exists' }
New-Item -ItemType Directory -Path $evidence | Out-Null
if ($env:OS -eq 'Windows_NT') {
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls $evidence /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
} else { & chmod 700 $evidence }
if ($LASTEXITCODE -ne 0) { throw 'Cannot protect backup directory' }
Start-Transcript -Path (Join-Path $evidence 'run.log') | Out-Null
$state = Join-Path $evidence 'state.json'
$stage = 'postgresql'
try {
    Run-Python -c 'import yaml, openapi_spec_validator, openapi_schema_validator'
    & "$PSScriptRoot/setup.ps1"
    Run-Docker build --target build -t dx-core-build:0.1.0 ./backend
    & "$PSScriptRoot/test-file.ps1"
    $results.postgresql = 'PASS'
    $stage = 'contracts'
    Run-Python -m openapi_spec_validator contracts/openapi/core.yaml
    Run-Python -m openapi_spec_validator contracts/openapi/core-target.yaml
    Run-Python -m openapi_spec_validator contracts/openapi/servicedesk.yaml
    $results.contracts = 'PASS'
    $stage = 'cleanE2E'
    Run-Docker compose -p $Project up -d --build --wait --wait-timeout 300
    & "$PSScriptRoot/smoke.ps1" -ComposeProject $Project
    Copy-Item -LiteralPath .local/core-integration-http.json -Destination (Join-Path $evidence 'smoke-http.json')
    Run-Python scripts/check-core-contract.py (Join-Path $evidence 'smoke-http.json')
    $results.cleanE2E = 'PASS'
    $stage = 'ticketFoundation'
    Run-Python scripts/acceptance/servicedesk.py (Join-Path $evidence 'servicedesk-http.json')
    Run-Python scripts/check-core-contract.py (Join-Path $evidence 'servicedesk-http.json') contracts/openapi/servicedesk.yaml
    $results.ticketFoundation = 'PASS'
    $stage = 'independentSecurityAndReuse'
    Run-Python scripts/acceptance/core/verify-runtime.py seed $state
    Run-Python scripts/check-core-contract.py (Join-Path $evidence 'seed-http.json')
    $results.independentSecurityAndReuse = 'PASS'
    $stage = 'uploadDisabled'
    $override = Join-Path $evidence 'uploads-disabled.yaml'
    [IO.File]::WriteAllText($override, "services:`n  core:`n    environment:`n      DX_ALLOW_UNSCANNED_UPLOAD: 'false'`n")
    $before = Run-Docker compose -p $Project exec -T postgres psql -U core -d core -Atc 'select count(*) from file_object'
    Run-Docker compose -p $Project -f compose.yaml -f $override up -d --wait --wait-timeout 300 --force-recreate core gateway
    Run-Python scripts/acceptance/core/verify-runtime.py upload-disabled $state
    $after = Run-Docker compose -p $Project exec -T postgres psql -U core -d core -Atc 'select count(*) from file_object'
    if ($before -ne $after) { throw 'Disabled upload persisted a file' }
    Run-Python scripts/check-core-contract.py (Join-Path $evidence 'upload-disabled-http.json')
    $results.uploadDisabled = 'PASS'
    $stage = 'restart'
    Run-Docker compose -p $Project stop
    Run-Docker compose -p $Project up -d --no-build --pull never --wait --wait-timeout 300
    Run-Python scripts/acceptance/core/verify-runtime.py verify $state
    Copy-Item -LiteralPath (Join-Path $evidence 'verify-http.json') -Destination (Join-Path $evidence 'restart-http.json')
    $results.restart = 'PASS'
    $stage = 'storageInventory'
    Run-Docker compose -p $Project stop gateway core keycloak
    $core = (Run-Docker compose -p $Project ps -aq core).Trim()
    $database = (Run-Docker compose -p $Project ps -q postgres).Trim()
    $inventory = Run-Docker exec $database psql -U core -d core -Atc "select coalesce(json_agg(files),'[]'::json) from (select storage_key::text,byte_size,checksum from file_object order by storage_key) files"
    [IO.File]::WriteAllText((Join-Path $evidence 'storage-inventory.json'), ($inventory -join "`n"))
    Run-Docker run --rm --network none --read-only --user 0 --volumes-from "${core}:ro" --mount "type=bind,source=$evidence,target=/backup" --entrypoint tar dx-core:0.1.0 -C /var/lib/dx/files -cpf /backup/files.tar .
    Run-Python scripts/acceptance/core/check-storage.py --self-test
    Run-Python scripts/acceptance/core/check-storage.py (Join-Path $evidence 'storage-inventory.json') (Join-Path $evidence 'files.tar')
    $results.storageInventory = 'PASS'
    $stage = 'backupRestore'
    foreach ($name in @('core', 'keycloak')) {
        Run-Docker exec $database pg_dump -U core -d $name -Fc -f "/tmp/$name.dump"
        Run-Docker cp "${database}:/tmp/$name.dump" (Join-Path $evidence "$name.dump")
    }
    $hashes = @{}
    foreach ($name in @('core.dump', 'keycloak.dump', 'files.tar')) { $hashes[$name] = (Get-FileHash -LiteralPath (Join-Path $evidence $name) -Algorithm SHA256).Hash }
    $hashes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'backup-sha256.json') -Encoding UTF8
    Run-Docker compose -p $Project stop postgres
    Run-Docker compose -p $restore up -d --wait --wait-timeout 120 postgres
    $restoreDatabase = (Run-Docker compose -p $restore ps -q postgres).Trim()
    foreach ($name in @('core', 'keycloak')) {
        if ((Get-FileHash -LiteralPath (Join-Path $evidence "$name.dump") -Algorithm SHA256).Hash -ne $hashes["$name.dump"]) { throw 'Backup checksum mismatch' }
        Run-Docker cp (Join-Path $evidence "$name.dump") "${restoreDatabase}:/tmp/$name.dump"
        Run-Docker exec $restoreDatabase pg_restore --exit-on-error --clean --if-exists --no-owner -U core -d $name "/tmp/$name.dump"
    }
    Run-Docker compose -p $restore create core
    $restoreCore = (Run-Docker compose -p $restore ps -aq core).Trim()
    if ((Get-FileHash -LiteralPath (Join-Path $evidence 'files.tar') -Algorithm SHA256).Hash -ne $hashes['files.tar']) { throw 'Blob archive checksum mismatch' }
    Run-Docker run --rm --network none --read-only --user 0 --volumes-from $restoreCore --mount "type=bind,source=$evidence,target=/backup,readonly" --entrypoint tar dx-core:0.1.0 -C /var/lib/dx/files -xpf /backup/files.tar
    Run-Docker compose -p $restore up -d --no-build --pull never --wait --wait-timeout 300
    Run-Python scripts/acceptance/core/verify-runtime.py verify $state
    Copy-Item -LiteralPath (Join-Path $evidence 'verify-http.json') -Destination (Join-Path $evidence 'restore-http.json')
    Run-Python scripts/acceptance/core/verify-runtime.py resume $state
    foreach ($name in @('restart', 'restore', 'resume')) { Run-Python scripts/check-core-contract.py (Join-Path $evidence "$name-http.json") }
    Run-Python scripts/acceptance/servicedesk.py (Join-Path $evidence 'restored-servicedesk-http.json') (Join-Path $evidence 'servicedesk-http.json')
    Run-Python scripts/check-core-contract.py (Join-Path $evidence 'restored-servicedesk-http.json') contracts/openapi/servicedesk.yaml
    Get-Content scripts/check-db.sql -Raw | & docker compose -p $restore exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'Restored DB consistency failed' }
    $results.backupRestore = 'PASS'
    $images = foreach ($image in @('dx-core:0.1.0', 'postgres:16.6-bookworm', 'quay.io/keycloak/keycloak:26.0.7', 'apache/apisix:3.11.0-debian')) {
        $inspection = (Run-Docker image inspect $image | ConvertFrom-Json)[0]
        if (!$inspection.Id) { throw "Missing image identity: $image" }
        $inspection | Select-Object Id,RepoTags,RepoDigests
    }
    $images | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'images.json') -Encoding UTF8
    Write-Host 'PASS automated functional gates; Core sign-off and production security are separate gates.'
} catch {
    $results[$stage] = 'FAIL'
    throw
} finally {
    [ordered]@{sourceCommit=(git rev-parse HEAD); workingTreeDirty=[bool](git status --porcelain); utc=[DateTime]::UtcNow.ToString('o'); gates=$results; coreAcceptance='PARTIAL'; humanSignoff='NOT VERIFIED'; productionSecurity='NOT VERIFIED'} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'summary.json') -Encoding UTF8
    foreach ($name in @($Project, $restore)) {
        & docker compose -p $name logs --no-color --tail 80 2>&1 | Out-File -LiteralPath (Join-Path $evidence "$name-containers.log") -Encoding UTF8
        & docker compose -p $name stop
    }
    Stop-Transcript | Out-Null
    Write-Host "Evidence and confidential backups retained at $evidence; no volumes deleted."
}
