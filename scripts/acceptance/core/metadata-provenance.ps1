# External acceptance probe against unchanged local Core; no source/config edits.
# Creates one synthetic metadata record through the public API per successful run.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)
$checks = New-Object 'System.Collections.Generic.List[object]'
$evidence = [ordered]@{
    startedAt = [DateTimeOffset]::Now.ToString('o')
    baseline = 'c92b38733d00c77bd90b96318f8c70d7b19be5c0'
    coreSourceChanged = $false
    success = $false
    checks = $checks
    pending = @('Signed writer-only fixture and role ratification', 'Core MVC/transaction regression tests', 'BE review')
}
function Check($name, [bool]$condition) {
    $checks.Add([ordered]@{name=$name; passed=$condition})
    if (!$condition) { throw "FAIL $name" }
    Write-Host "PASS $name"
}
function Token($user, $password) {
    try {
        $body = @{grant_type='password'; client_id='core-probe'; username=$user; password=$password}
        return (Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/realms/dx-lab/protocol/openid-connect/token' -Body $body).access_token
    } catch { throw "Could not obtain local demo token for $user; check running baseline and .env." }
}
function Call($name, $method, $path, $token, $body, $expected) {
    $request = New-Object System.Net.Http.HttpRequestMessage
    $response = $null
    try {
        $request.Method = New-Object System.Net.Http.HttpMethod($method)
        $request.RequestUri = "http://localhost:9080/api/core/v1$path"
        if ($token) { $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token) }
        if ($null -ne $body) {
            $request.Content = New-Object System.Net.Http.StringContent(($body | ConvertTo-Json -Depth 10), [Text.Encoding]::UTF8, 'application/json')
        }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        Check "$name HTTP $expected" ([int]$response.StatusCode -eq $expected)
        $requestId = $null
        if ($response.Headers.Contains('X-Request-ID')) { $requestId = @($response.Headers.GetValues('X-Request-ID'))[0] }
        $result = $null
        if ($expected -ge 200 -and $expected -lt 300) {
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ($text) { $result = $text | ConvertFrom-Json }
        }
        return [pscustomobject]@{body=$result; requestId=$requestId}
    } finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }
}
try {
    $password = (Get-Content -LiteralPath (Join-Path $root '.env') | Where-Object { $_ -match '^DEMO_PASSWORD=' }) -replace '^DEMO_PASSWORD=', ''
    if (!$password) { throw 'Missing local DEMO_PASSWORD; run existing setup/start first.' }
    # Identity demo users (infra/keycloak/realm-template.json):
    # user2 = lab-a agent (may create), user1 = lab-a requester (read-only),
    # user5 = lab-b agent (must be denied access to lab-a).
    $agentA = Token 'user2' $password
    $requesterA = Token 'user1' $password
    $agentB = Token 'user5' $password
    $password = $null
    $me = (Call 'identity' GET '/me' $agentA $null 200).body
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $checksum = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes('AI-N1 synthetic provenance probe.'))).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
    $data = @{title='AI-N1 synthetic probe'; source='team-authored:ai-n1-provenance'; version='rev-1'; usageRights='CC0-1.0'; checksum=$checksum}
    $path = '/workspaces/lab-a/metadata'
    Call 'missing JWT' POST $path '' $data 401 | Out-Null
    Call 'invalid JWT' POST $path 'invalid' $data 401 | Out-Null
    Call 'META-04 reader write denied' POST $path $requesterA $data 403 | Out-Null
    Call 'META-05 foreign write denied' POST $path $agentB $data 403 | Out-Null
    $limits = @{title=200; source=1000; version=100; usageRights=200}
    foreach ($field in @('title', 'source', 'version', 'usageRights')) {
        $bad = $data.Clone(); $bad[$field] = '   '
        Call "META-08 blank $field" POST $path $agentA $bad 400 | Out-Null
        $bad = $data.Clone(); $bad.Remove($field)
        Call "META-08 missing $field" POST $path $agentA $bad 400 | Out-Null
        $bad = $data.Clone(); $bad[$field] = 'x' * ($limits[$field] + 1)
        Call "META-08 oversized $field" POST $path $agentA $bad 400 | Out-Null
    }
    $invalidChecksums = [ordered]@{
        short=('a' * 63); long=('a' * 65); uppercase=('A' * 64)
        nonhex=('g' * 64); blank='   '
    }
    foreach ($label in $invalidChecksums.Keys) {
        $bad = $data.Clone(); $bad.checksum = $invalidChecksums[$label]
        Call "META-09/10 checksum $label" POST $path $agentA $bad 400 | Out-Null
    }
    $bad = $data.Clone(); $bad.Remove('checksum')
    Call 'META-09 missing checksum' POST $path $agentA $bad 400 | Out-Null
    foreach ($field in @('owner', 'workspace', 'id', 'created_at', 'unexpected')) {
        $bad = $data.Clone(); $bad[$field] = 'client-controlled'
        Call "META-02/03/11 reject body $field" POST $path $agentA $bad 400 | Out-Null
    }
    $created = Call 'META-01 lab-a agent create' POST $path $agentA $data 201
    $record = $created.body
    # Save identifiers immediately so partial failures can still be inspected.
    $evidence.record = [ordered]@{id=$record.id; workspace=$record.workspace; owner=$record.owner; requestId=$created.requestId}
    $parsedId = [guid]::Empty
    Check 'server UUID' ([guid]::TryParse([string]$record.id, [ref]$parsedId) -and $parsedId -ne [guid]::Empty)
    Check 'META-02 JWT owner' ($record.owner -ceq $me.sub)
    Check 'META-03 route workspace' ($record.workspace -ceq 'lab-a')
    $createdAt = [DateTimeOffset]::MinValue
    Check 'server timestamp' ([DateTimeOffset]::TryParse([string]$record.created_at, [ref]$createdAt))
    $requestGuid = [guid]::Empty
    Check 'audit response request ID' ([guid]::TryParse([string]$created.requestId, [ref]$requestGuid))
    $read = (Call 'META-13 workspace reader round trip' GET "$path/$($record.id)" $requesterA $null 200).body
    $wireFields = @('id', 'workspace', 'title', 'source', 'version', 'usage_rights', 'checksum', 'owner', 'created_at')
    Check 'explicit expected wire field set' ((@($read.PSObject.Properties.Name | Sort-Object) -join ',') -ceq (@($wireFields | Sort-Object) -join ','))
    foreach ($field in $wireFields) {
        Check "META-13 create/read $field preserved" ($record.$field -ceq $read.$field)
    }
    foreach ($pair in @(@('title','title'), @('source','source'), @('version','version'), @('usageRights','usage_rights'), @('checksum','checksum'))) {
        Check "META-13 caller $($pair[0]) preserved" ($data[$pair[0]] -ceq $read.($pair[1]))
    }
    Call 'META-06 foreign direct read denied' GET "$path/$($record.id)" $agentB $null 403 | Out-Null
    Call 'META-07 scoped foreign ID hidden' GET "/workspaces/lab-b/metadata/$($record.id)" $agentB $null 404 | Out-Null
    Call 'unknown ID within workspace' GET "$path/$([guid]::NewGuid())" $requesterA $null 404 | Out-Null
    $evidence.success = $true
    Write-Host "PASS $($checks.Count) API assertions; role-policy regression remains pending."
} finally {
    $client.Dispose()
    $agentA = $null; $requesterA = $null; $agentB = $null
    $evidence.finishedAt = [DateTimeOffset]::Now.ToString('o')
    $directory = Join-Path $root '.local/ai-n1'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $destination = Join-Path $directory 'metadata-provenance.json'
    [IO.File]::WriteAllText($destination, ($evidence | ConvertTo-Json -Depth 10), (New-Object Text.UTF8Encoding($false)))
    Write-Host 'Evidence: .local/ai-n1/metadata-provenance.json (no credentials/tokens)'
}
