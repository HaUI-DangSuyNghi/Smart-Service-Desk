param([string]$BuildImage = 'dx-core-build:0.1.0')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "workflow-test-$suffix"
$database = "workflow-db-$suffix"
$password = [guid]::NewGuid().ToString('N')
$networkCreated = $false
$databaseCreated = $false
try {
    docker network create --internal $network | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated network' }
    $networkCreated = $true
    docker run -d --name $database --network $network --network-alias database --tmpfs /var/lib/postgresql/data -e POSTGRES_DB=workflow_approval -e POSTGRES_USER=workflow -e "POSTGRES_PASSWORD=$password" postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start isolated database' }
    $databaseCreated = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $database pg_isready -h 127.0.0.1 -U workflow -d workflow_approval *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Database readiness timed out' }
    docker run --rm --network $network --mount "type=bind,source=$(Join-Path $root 'backend'),target=/work" --mount "type=bind,source=$(Join-Path $root 'contracts/intelligence/v1'),target=/contracts/intelligence/v1,readonly" -w /work -e WORKFLOW_TEST_JDBC_URL=jdbc:postgresql://database:5432/workflow_approval -e "WORKFLOW_TEST_DB_PASSWORD=$password" $BuildImage mvn -B -o clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Workflow tests failed' }
} finally {
    if ($databaseCreated) { docker rm -f -v $database | Out-Null }
    if ($networkCreated) { docker network rm $network | Out-Null }
}
