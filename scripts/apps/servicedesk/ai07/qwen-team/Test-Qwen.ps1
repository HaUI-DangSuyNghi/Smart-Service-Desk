$ErrorActionPreference = 'Stop'
$package = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'package.json') | ConvertFrom-Json
$raw = docker exec ai07-qwen-team-ai07-qwen-1 python /run/classify-client.py
if ($LASTEXITCODE -ne 0) { throw 'Private Qwen inference failed or exceeded its deadline' }
$report = $raw | ConvertFrom-Json
if ($report.httpStatus -ne 200) { throw 'Classification HTTP request failed' }
$result = $report.response
if ($result.modelDigest -ne $package.modelDigest -or $result.policyDigest -ne $package.policyDigest) { throw 'Inference identity mismatch' }
if ($result.operationalAuthority -ne $false -or $null -ne $result.observation.score -or $result.observation.evidenceStatus -ne 'UNVERIFIED_BY_MODEL') { throw 'Unexpected inference authority or score' }
if ($result.observation.decision -notin @('PROPOSE', 'ABSTAIN')) { throw 'Unexpected decision' }
$result | ConvertTo-Json -Depth 5
