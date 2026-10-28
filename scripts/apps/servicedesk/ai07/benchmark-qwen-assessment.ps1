# Benchmark tổng hợp riêng tư; không cấp grant hay phê duyệt production.
param(
    [Parameter(Mandatory=$true)][string]$QwenKitDirectory,
    [ValidateRange(20,2000)][int]$Samples = 100,
    [ValidateRange(1,5)][int]$ColdCycles = 2,
    [string]$BuildImage = 'ai07-v19-maven:local',
    [string]$DatabaseImage = 'postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4'
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$kitRoot = (Resolve-Path -LiteralPath $QwenKitDirectory).Path
$package = Get-Content -LiteralPath (Join-Path $kitRoot 'package.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($package.schemaVersion -ne 'ai07-qwen-team-package@0.1.0') { throw 'Unexpected Qwen kit schema' }
$builderIdentity = docker image inspect $BuildImage --format '{{.Id}}'
if ($LASTEXITCODE -ne 0 -or $builderIdentity -notmatch '^sha256:[0-9a-f]{64}$') { throw 'Inspect the local Maven build image before benchmarking' }
foreach ($entry in $package.files.PSObject.Properties) {
    $artifact = [IO.Path]::GetFullPath((Join-Path $kitRoot $entry.Name))
    if (-not $artifact.StartsWith($kitRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Qwen package path escapes its root'
    }
    if ((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value) {
        throw "Qwen package checksum mismatch: $($entry.Name)"
    }
}
$runId = [guid]::NewGuid().ToString('N')
$project = "ai07-assessment-bench-$runId"
$network = "$project-client"
$database = "$project-db"
$receiptRoot = Join-Path $repoRoot ".local/ai07/review-performance/$runId"
New-Item -ItemType Directory -Path $receiptRoot | Out-Null
$randomBytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($randomBytes) } finally { $random.Dispose() }
$tokenPath = Join-Path $receiptRoot 'classification-token'
[IO.File]::WriteAllText($tokenPath, [BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant(), [Text.Encoding]::ASCII)
$password = [guid]::NewGuid().ToString('N')
$env:AI07_QWEN_IMAGE = $package.imageId
$env:AI07_QWEN_MODEL_DIGEST = $package.modelDigest
$env:AI07_QWEN_POLICY_DIGEST = $package.policyDigest
$env:AI07_QWEN_BUNDLE = Join-Path $kitRoot 'model'
$env:AI07_QWEN_APP_ROOT = Join-Path $kitRoot 'apps'
$env:AI07_QWEN_TOKEN_FILE = $tokenPath
$env:AI07_QWEN_TOKEN = [IO.File]::ReadAllText($tokenPath)
$env:AI07_QWEN_ENABLED = '1'
$env:AI07_LOAD_SAMPLES = [string]$Samples
$env:AI07_BENCHMARK_RUN_ID = $runId
$env:FILE_TEST_DB_PASSWORD = $password
$compose = @('compose', '-p', $project, '-f', (Join-Path $kitRoot 'compose.yaml'), '--profile', 'ai07-qwen')
$networkCreated = $false
$databaseCreated = $false
function SourceIdentity {
    $paths = @((Join-Path $repoRoot 'backend/src'), (Join-Path $repoRoot 'contracts'),
        (Join-Path $repoRoot 'backend/pom.xml'), (Join-Path $repoRoot 'backend/Dockerfile'), $PSCommandPath)
    $lines = @(Get-ChildItem -LiteralPath $paths -Recurse -File |
        Sort-Object FullName | ForEach-Object {
            $_.FullName.Substring($repoRoot.Length).Replace('\','/') + ':' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        })
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).Replace('-','').ToLowerInvariant() }
    finally { $hasher.Dispose() }
}
$sourceFingerprint = SourceIdentity
$env:AI07_SOURCE_FINGERPRINT = $sourceFingerprint
$env:AI07_ASSESSMENT_POLICY_DIGEST = (Get-FileHash -LiteralPath (Join-Path $repoRoot 'backend/src/main/resources/apps/servicedesk/intelligence/assessment-policy-v0.1.json') -Algorithm SHA256).Hash.ToLowerInvariant()
try {
    docker network create --internal $network | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create private benchmark network' }
    $networkCreated = $true
    docker run -d --name $database --network $network --network-alias database --tmpfs /var/lib/postgresql/data -e POSTGRES_USER=workflow -e POSTGRES_DB=file_storage -e "POSTGRES_PASSWORD=$password" $DatabaseImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start disposable benchmark database' }
    $databaseCreated = $true
    for ($cycle = 1; $cycle -le $ColdCycles; $cycle++) {
        if ((SourceIdentity) -ne $sourceFingerprint) { throw 'Source changed; start a new immutable benchmark run' }
        Write-Host "Cold cycle $cycle/$ColdCycles; samples=$Samples for concurrency 1, 2, 4; run=$runId"
        if ($cycle -gt 1) {
            docker @compose stop
            if ($LASTEXITCODE -ne 0) { throw 'Cannot stop benchmark model process' }
        }
        $startup = [Diagnostics.Stopwatch]::StartNew()
        docker @compose up -d --wait --wait-timeout 240
        if ($LASTEXITCODE -ne 0) { throw 'Private Qwen sidecar did not become ready' }
        $startup.Stop()
        $env:AI07_COLD_READY_MS = $startup.Elapsed.TotalMilliseconds.ToString([Globalization.CultureInfo]::InvariantCulture)
        $env:AI07_COLD_CYCLE = [string]$cycle
        $container = docker @compose ps -q ai07-qwen
        if ($cycle -eq 1) {
            docker network connect --alias intelligence-classification $network $container
            if ($LASTEXITCODE -ne 0) { throw 'Cannot attach private benchmark client network' }
        }
        $log = Join-Path $receiptRoot "cold-$cycle-java.log"
        $ErrorActionPreference = 'Continue'
        docker run --rm --network $network --mount "type=bind,source=$repoRoot,target=/workspace" --tmpfs /workspace/backend/target -w /workspace/backend `
            -e AI07_REAL_QWEN_E2E=true -e AI07_QWEN_BASE_URL=http://intelligence-classification:8003 `
            -e AI07_QWEN_MODEL_DIGEST -e AI07_QWEN_POLICY_DIGEST -e AI07_QWEN_TOKEN `
            -e AI07_LOAD_SAMPLES -e AI07_BENCHMARK_RUN_ID -e AI07_COLD_READY_MS -e AI07_COLD_CYCLE -e AI07_SOURCE_FINGERPRINT -e AI07_ASSESSMENT_POLICY_DIGEST `
            -e FILE_TEST_JDBC_URL=jdbc:postgresql://database:5432/file_storage -e FILE_TEST_DB_PASSWORD `
            $builderIdentity mvn -B -o '-Dtest=TicketAssessmentQwenLoadIT' test *> $log
        $result = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Get-Content -LiteralPath $log -Tail 8
        if ($result -ne 0) { throw "Assessment benchmark failed in cold cycle $cycle; retain $log" }
        if ((SourceIdentity) -ne $sourceFingerprint) { throw 'Source changed during measurement; receipt is not a final benchmark' }
    }
    $receipts = @(Get-ChildItem -LiteralPath $receiptRoot -Filter 'load-*.json' | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json })
    if ($receipts.Count -ne $ColdCycles) { throw 'Missing cold-cycle benchmark receipts' }
    $summary = @{schemaVersion='ai07-assessment-load@0.1.0'; runId=$runId; measuredAt=[DateTime]::UtcNow.ToString('o');
        modelImage=$package.imageId; builderImage=$builderIdentity; modelDigest=$package.modelDigest; samplesPerPhase=$Samples; sourceFingerprint=$sourceFingerprint;
        coldCycles=$receipts; sloDecision='NOT_EVALUATED_NO_APPROVED_SLO'; productionDecision='NO_GO'}
    [IO.File]::WriteAllText((Join-Path $receiptRoot 'summary.json'), ($summary | ConvertTo-Json -Depth 30), (New-Object Text.UTF8Encoding $false))
    Write-Host "Benchmark complete: $receiptRoot/summary.json (NO_GO; no approved SLO)."
} finally {
    docker @compose down | Out-Null
    if ($databaseCreated) { docker rm -f $database | Out-Null }
    if ($networkCreated) { docker network rm $network | Out-Null }
    Remove-Item Env:/AI07_QWEN_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:/FILE_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
}
