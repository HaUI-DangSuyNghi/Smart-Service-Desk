param(
    [string]$Maven = 'mvn',
    [string]$BuildImage,
    [string]$Tests,
    [switch]$PackagedProof,
    [ValidateSet('focused','core-only','full','second-domain')][string]$Profile = 'focused'
)
$ErrorActionPreference = 'Stop'
if ($PackagedProof -and !$BuildImage) { throw 'Packaged proof requires BuildImage' }
$root = Split-Path $PSScriptRoot -Parent
$name = 'fresh-admin-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$evidence = Join-Path $root ".local/$name"
New-Item -ItemType Directory -Path $evidence | Out-Null
@{ sha=(git -C $root rev-parse HEAD); branch=(git -C $root branch --show-current);
    worktree=@(git -C $root status --porcelain); profile=$Profile; tests=$Tests;
    packaged=[bool]$PackagedProof } | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $evidence 'source.json') -Encoding UTF8
$password = [guid]::NewGuid().ToString('N')
$secret = [guid]::NewGuid().ToString('N')
$realm = Get-Content -LiteralPath (Join-Path $root 'infra/keycloak/realm-template.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$realm.realm = 'fresh-admin-proof'
foreach ($user in $realm.users) {
    if (!$user.serviceAccountClientId) {
        $user | Add-Member credentials @(@{type='password'; value=$password; temporary=$false}) -Force
    }
}
foreach ($client in $realm.clients) {
    if ($client.clientId -eq 'dx-directory') { $client.secret = $secret }
}
$import = Join-Path $evidence 'realm.json'
[IO.File]::WriteAllText($import, ($realm | ConvertTo-Json -Depth 40), (New-Object Text.UTF8Encoding $false))
$saved = @{}
foreach ($key in @('FRESH_ADMIN_TEST_URL','FRESH_ADMIN_TEST_PASSWORD','FRESH_ADMIN_TEST_DIRECTORY_SECRET',
    'FILE_TEST_JDBC_URL','WORKFLOW_TEST_JDBC_URL','BACKGROUND_TEST_JDBC_URL','FILE_TEST_DB_PASSWORD','WORKFLOW_TEST_DB_PASSWORD')) {
    $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
$created = $false
$networkCreated = $false
$databaseCreated = $false
$networkArguments = @()
try {
    if ($BuildImage) {
        docker network create --internal "$name-network" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated network' }
        $networkCreated = $true
        $networkArguments = @('--network', "$name-network", '--network-alias', 'keycloak')
    }
    docker run -d --name $name -p '127.0.0.1::8080' `
        @networkArguments `
        --mount "type=bind,source=$import,target=/opt/keycloak/data/import/realm.json,readonly" `
        -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e "KC_BOOTSTRAP_ADMIN_PASSWORD=$password" `
        'quay.io/keycloak/keycloak:26.0.8@sha256:09a381c715ab0b111835b70f2905955274843a219c6f27efb348e4d9f4086858' `
        start-dev --import-realm | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start isolated Keycloak' }
    $created = $true
    if ($BuildImage) { $env:FRESH_ADMIN_TEST_URL = 'http://keycloak:8080' }
    else {
        $address = (docker port $name 8080/tcp).Trim()
        $env:FRESH_ADMIN_TEST_URL = "http://$address"
    }
    $env:FRESH_ADMIN_TEST_PASSWORD = $password
    $env:FRESH_ADMIN_TEST_DIRECTORY_SECRET = $secret
    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        try {
            if ($BuildImage) {
                $probe = 'exec 3<>/dev/tcp/localhost/8080; printf ''GET /realms/fresh-admin-proof/.well-known/openid-configuration HTTP/1.0\r\nHost: keycloak:8080\r\n\r\n'' >&3; IFS= read -r status <&3; [[ $status == *200* ]]'
                docker exec $name bash -c $probe *> $null
                if ($LASTEXITCODE -ne 0) { throw 'Not ready' }
            } else {
                Invoke-WebRequest -UseBasicParsing -Uri "$env:FRESH_ADMIN_TEST_URL/realms/fresh-admin-proof/.well-known/openid-configuration" -TimeoutSec 2 | Out-Null
            }
            $ready = $true
            break
        } catch { Start-Sleep -Seconds 2 }
    }
    if (!$ready) { throw 'Keycloak readiness timeout' }
    $arguments = @('-B','-f',(Join-Path $root 'backend/pom.xml'),'clean','verify')
    if ($Profile -eq 'focused') {
        $arguments += '-Pcore-only'
        if (!$Tests) { $Tests = 'KeycloakFreshAdminAuthorizationTest,KeycloakFreshAdminIntegrationTest' }
    } elseif ($Profile -ne 'full') { $arguments += "-P$Profile" }
    if ($Tests) { $arguments += "-Dtest=$Tests" }
    $arguments += @('-Dservice-desk.background.scheduled=false','-Dservice-desk.sla.scheduled=false',
        '-Dspring.datasource.hikari.maximum-pool-size=3')
    if ($BuildImage) {
        $arguments += '-o'
        docker run -d --name "$name-pg" --network "$name-network" --network-alias database `
            --tmpfs /var/lib/postgresql/data -e POSTGRES_DB=file_storage -e POSTGRES_USER=workflow `
            -e "POSTGRES_PASSWORD=$password" `
            'postgres:16.6-bookworm@sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot start PostgreSQL' }
        $databaseCreated = $true
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            docker exec "$name-pg" pg_isready -U workflow -d file_storage *> $null
            if ($LASTEXITCODE -eq 0) { break }
            Start-Sleep -Seconds 1
        }
        foreach ($database in @('workflow_approval','background_assessment','duty_upgrade')) {
            docker exec "$name-pg" createdb -U workflow $database
            if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare test database' }
        }
        $env:FILE_TEST_JDBC_URL = 'jdbc:postgresql://database:5432/file_storage'
        $env:WORKFLOW_TEST_JDBC_URL = 'jdbc:postgresql://database:5432/workflow_approval'
        $env:BACKGROUND_TEST_JDBC_URL = 'jdbc:postgresql://database:5432/background_assessment'
        $env:FILE_TEST_DB_PASSWORD = $password
        $env:WORKFLOW_TEST_DB_PASSWORD = $password
        $env:FRESH_ADMIN_TEST_URL = 'http://keycloak:8080'
        $arguments[2] = '/work/backend/pom.xml'
        $command = 'tar -C /source --exclude=backend/target --exclude=backend/.local -cf - backend contracts docs/architecture | tar -C /work -xf -; mvn "$@"; result=$?; cp /work/backend/target/surefire-reports/TEST-*.xml /evidence/; '
        if ($PackagedProof) {
            if ($Profile -ne 'full' -or $Tests) { throw 'Packaged proof requires unfiltered Full' }
            $command += 'if [ "$result" = 0 ]; then mkdir /evidence/packaged; sha256sum /work/backend/target/dx-platform-0.1.0-SNAPSHOT.jar > /evidence/packaged/backend-jar.sha256; mvn -B -o -Dtest=SettingsHttpAcceptanceTest -Dsettings.jar=/work/backend/target/dx-platform-0.1.0-SNAPSHOT.jar test; result=$?; cp /work/backend/target/surefire-reports/TEST-vn.haui.dx.hpdi.settings.SettingsHttpAcceptanceTest.xml /evidence/packaged/; fi; '
        }
        $command += 'for proof in /work/backend/target/settings-http-*; do [ ! -d "$proof" ] || cp -r "$proof" /evidence/; done; exit $result'
    }
    $ErrorActionPreference = 'Continue'
    if ($BuildImage) {
        docker run --rm --network "$name-network" -w /work/backend `
            --mount "type=bind,source=$root,target=/source,readonly" `
            --mount "type=bind,source=$evidence,target=/evidence" `
            -e FRESH_ADMIN_TEST_URL -e FRESH_ADMIN_TEST_PASSWORD -e FRESH_ADMIN_TEST_DIRECTORY_SECRET `
            -e FILE_TEST_JDBC_URL -e WORKFLOW_TEST_JDBC_URL -e BACKGROUND_TEST_JDBC_URL `
            -e FILE_TEST_DB_PASSWORD -e WORKFLOW_TEST_DB_PASSWORD `
            --entrypoint sh $BuildImage -c $command runner @arguments *> (Join-Path $evidence 'maven.log')
    } else { & $Maven @arguments *> (Join-Path $evidence 'maven.log') }
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Get-Content (Join-Path $evidence 'maven.log') -Tail 20
    if (!$BuildImage) { Copy-Item (Join-Path $root 'backend/target/surefire-reports/TEST-*.xml') $evidence }
    if ($exitCode -ne 0) { throw "Fresh admin tests failed: $evidence" }
    $summary = @{tests=0; failures=0; errors=0; skipped=0}
    foreach ($report in Get-ChildItem $evidence -Filter 'TEST-*.xml') {
        [xml]$xml = Get-Content $report.FullName -Raw -Encoding UTF8
        foreach ($field in @('tests','failures','errors','skipped')) { $summary[$field] += [int]$xml.testsuite.$field }
    }
    $summary | ConvertTo-Json | Set-Content (Join-Path $evidence 'summary.json') -Encoding UTF8
    if ($summary.tests -eq 0 -or $summary.failures -or $summary.errors -or $summary.skipped) {
        throw 'Fresh admin proof requires zero failures/errors/skips'
    }
    Write-Output "PASS real Keycloak 26.0.8; $($summary.tests) tests. Evidence: $evidence"
} finally {
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
    if ($created) { docker rm -f $name | Out-Null }
    if ($databaseCreated) { docker rm -f -v "$name-pg" | Out-Null }
    if ($networkCreated) { docker network rm "$name-network" | Out-Null }
    Remove-Item -LiteralPath $import -ErrorAction SilentlyContinue
}
