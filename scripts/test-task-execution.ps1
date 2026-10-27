param(
    [string]$Maven = 'mvn',
    [string]$BuildImage,
    [string]$Test,
    [ValidateSet('execution', 'full', 'core-only', 'second-domain')][string]$Profile = 'execution'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $root ".local/task-execution-$Profile"
if ($Test) { $evidence += '-targeted' }
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$container = 'task-execution-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$password = [guid]::NewGuid().ToString('N')
$created = $false
$networkCreated = $false
$network = "$container-network"
$saved = @{}
foreach ($name in @('FILE_TEST_JDBC_URL', 'FILE_TEST_DB_PASSWORD', 'WORKFLOW_TEST_JDBC_URL', 'WORKFLOW_TEST_DB_PASSWORD', 'BACKGROUND_TEST_JDBC_URL')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $networkArguments = @('-p', '127.0.0.1::5432')
    if ($BuildImage) {
        docker network create --internal $network | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create private test network' }
        $networkCreated = $true
        $networkArguments = @('--network', $network, '--network-alias', 'database')
    }
    docker run -d --name $container --label codex.task=ticket-execution --tmpfs /var/lib/postgresql/data `
        @networkArguments -e POSTGRES_DB=file_storage -e POSTGRES_USER=workflow -e "POSTGRES_PASSWORD=$password" `
        postgres:16.6-bookworm@sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create disposable PostgreSQL' }
    $created = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        docker exec $container pg_isready -U workflow -d file_storage *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL readiness timeout' }
    docker exec $container psql -U workflow -d file_storage -Atc 'select version()' | Set-Content -LiteralPath (Join-Path $evidence 'postgres-version.txt')
    docker exec $container createdb -U workflow workflow_approval
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create workflow test database' }
    docker exec $container createdb -U workflow background_assessment
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create assessment test database' }
    docker exec $container createdb -U workflow duty_upgrade
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create duty upgrade test database' }
    $address = 'database:5432'
    if (-not $BuildImage) { $address = (docker port $container 5432/tcp).Trim() }
    $env:FILE_TEST_JDBC_URL = "jdbc:postgresql://$address/file_storage"
    $env:WORKFLOW_TEST_JDBC_URL = "jdbc:postgresql://$address/workflow_approval"
    $env:BACKGROUND_TEST_JDBC_URL = "jdbc:postgresql://$address/background_assessment"
    $env:FILE_TEST_DB_PASSWORD = $password
    $env:WORKFLOW_TEST_DB_PASSWORD = $password
    $arguments = @('-B', '-o', '-f', (Join-Path $root 'backend/pom.xml'), 'clean', 'test')
    $arguments += '-Dservice-desk.background.scheduled=false'
    $arguments += '-Dservice-desk.sla.scheduled=false'
    $arguments += '-Dspring.datasource.hikari.maximum-pool-size=3'
    if ($Test) { $arguments += "-Dtest=$Test" }
    if ($Profile -eq 'execution') { $arguments += '-Dtest=WorkflowApprovalTest,WorkflowExecutionCompatibilityTest,WorkExecutionTest' }
    elseif ($Profile -ne 'full') { $arguments += "-P$Profile" }
    if ($Profile -eq 'second-domain') {
        $arguments += '-Dtest=AgentDutyIntegrationTest,RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest,HttpClassificationTransportTest,WorkExecutionTest'
    }
    $reports = Join-Path $root 'backend/target/surefire-reports'
    $ErrorActionPreference = 'Continue'
    if ($BuildImage) {
        $reports = Join-Path $evidence $container
        New-Item -ItemType Directory -Path $reports -ErrorAction Stop | Out-Null
        $arguments[3] = '/work/backend/pom.xml'
        $command = 'tar -C /source --exclude=backend/target --exclude=backend/.local -cf - backend contracts docs/architecture | tar -C /work -xf -; mvn "$@"; result=$?; cp /work/backend/target/surefire-reports/TEST-*.xml /evidence/; for evidence in servicedesk-http-evidence.json background-assessment-http-evidence.json r2-assignment-http-evidence.json r5-sla-http-evidence.json; do if [ -f /work/backend/target/$evidence ]; then cp /work/backend/target/$evidence /evidence/; fi; done; exit $result'
        docker run --rm --network $network -w /work/backend --mount "type=bind,source=$root,target=/source,readonly" `
            --mount "type=bind,source=$HOME/.m2/repository,target=/root/.m2/repository,readonly" `
            --mount "type=bind,source=$reports,target=/evidence" `
            -e FILE_TEST_JDBC_URL -e WORKFLOW_TEST_JDBC_URL -e BACKGROUND_TEST_JDBC_URL -e FILE_TEST_DB_PASSWORD -e WORKFLOW_TEST_DB_PASSWORD `
            --entrypoint sh $BuildImage -c $command runner @arguments *> (Join-Path $evidence 'maven.log')
    } else {
        & $Maven @arguments *> (Join-Path $evidence 'maven.log')
    }
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Get-Content -LiteralPath (Join-Path $evidence 'maven.log') -Tail 18
    $summary = @{ profile = $Profile; tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($report in Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml') {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Encoding UTF8
        foreach ($field in @('tests', 'failures', 'errors', 'skipped')) { $summary[$field] += [int]$document.testsuite.$field }
    }
    $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'summary.json') -Encoding UTF8
    if ($exitCode -ne 0) { throw "Maven failed; see $evidence/maven.log" }
    if ($summary.tests -eq 0 -or $summary.skipped -ne 0 -or $summary.failures -ne 0 -or $summary.errors -ne 0) {
        throw 'Required tests must run without failures, errors or skips'
    }
    Write-Output "Verified $($summary.tests) tests, zero skipped. Evidence: $evidence"
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    if ($created) { docker rm -f -v $container | Out-Null }
    if ($networkCreated) { docker network rm $network | Out-Null }
}
