param([string]$BuildImage = 'dx-core-build:0.1.0')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "file-test-$suffix"
$database = "file-db-$suffix"
$password = [guid]::NewGuid().ToString('N')
$networkCreated = $false
$databaseCreated = $false
try {
    docker network create --internal $network | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated network' }
    $networkCreated = $true
    docker run -d --name $database --network $network --network-alias database --tmpfs /var/lib/postgresql/data -e POSTGRES_DB=file_storage -e POSTGRES_USER=workflow -e "POSTGRES_PASSWORD=$password" postgres:16.6-bookworm | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start isolated database' }
    $databaseCreated = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $database pg_isready -h 127.0.0.1 -U workflow -d file_storage *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Database readiness timed out' }
    docker exec $database createdb -U workflow workflow_approval
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated workflow regression database' }
    docker run --rm --network $network --mount "type=bind,source=$root,target=/workspace" -w /workspace/backend -e FILE_TEST_JDBC_URL=jdbc:postgresql://database:5432/file_storage -e "FILE_TEST_DB_PASSWORD=$password" -e WORKFLOW_TEST_JDBC_URL=jdbc:postgresql://database:5432/workflow_approval -e "WORKFLOW_TEST_DB_PASSWORD=$password" $BuildImage mvn -B -o clean verify
    if ($LASTEXITCODE -ne 0) { throw 'File/regression tests failed' }
    $reports = @(Get-ChildItem -LiteralPath (Join-Path $root 'backend/target/surefire-reports') -Filter 'TEST-*.xml')
    if ($reports.Count -eq 0) { throw 'No test reports produced' }
    $total = 0
    foreach ($report in $reports) {
        $suite = ([xml](Get-Content -LiteralPath $report.FullName -Raw)).testsuite
        if ([int]$suite.skipped -ne 0 -or [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0) { throw "Non-passing tests in $($report.Name)" }
        $total += [int]$suite.tests
    }
    Write-Host "PASS $total tests; failures=0 errors=0 skipped=0 (PostgreSQL 16.6)"
} finally {
    if ($databaseCreated) { docker rm -f -v $database | Out-Null }
    if ($networkCreated) { docker network rm $network | Out-Null }
}
