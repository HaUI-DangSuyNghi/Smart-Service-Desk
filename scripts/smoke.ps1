param([string]$ComposeProject)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)
$contractResponses = New-Object 'System.Collections.Generic.List[object]'
$password = (Get-Content .env | Where-Object { $_ -match '^DEMO_PASSWORD=' }) -replace '^DEMO_PASSWORD=', ''
function Token($user) {
    $body = @{grant_type='password'; client_id='core-probe'; username=$user; password=$password}
    (Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/realms/dx-lab/protocol/openid-connect/token' -Body $body).access_token
}
function Direct($path, $token, $expected) {
    $config = "url = `"http://localhost:8080/api/core/v1$path`"`n"
    if ($token) { $config += "header = `"Authorization: Bearer $token`"`n" }
    $reply = @($config | docker compose -p $ComposeProject exec -T platform curl -sS --config - -w '\n%{http_code}')
    if ($LASTEXITCODE -ne 0 -or [int]$reply[-1] -ne $expected) { throw "Direct backend $path expected $expected" }
    $text = $reply[0..($reply.Count - 2)] -join "`n"
    $contractResponses.Add(@{method='GET'; path=$path; status=$expected; bodyJson=$text; source='direct-backend'})
    Write-Host "PASS direct backend $path -> $expected"
}
function Upload($token, [byte[]]$bytes, $name, $type, $expected) {
    $request = New-Object System.Net.Http.HttpRequestMessage
    $request.Method = [System.Net.Http.HttpMethod]::Post
    $request.RequestUri = 'http://localhost:9080/api/core/v1/workspaces/lab-a/files'
    $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
    $form = New-Object System.Net.Http.MultipartFormDataContent
    $part = New-Object System.Net.Http.ByteArrayContent(,$bytes)
    $part.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue($type)
    $form.Add($part, 'file', $name)
    $request.Content = $form
    $response = $null
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().Result
        $status = [int]$response.StatusCode
        if ($status -ne $expected) { throw "Upload $name expected $expected got $status : $text" }
        $contractResponses.Add(@{method='POST'; path='/workspaces/lab-a/files'; status=$status; bodyJson=$text})
        Write-Host "PASS multipart $name -> $status"
        return ($text | ConvertFrom-Json)
    } finally {
        if ($response) { $response.Dispose() }
        $request.Dispose()
    }
}
function RaceComplete($path, $token, $body) {
    $requests = @()
    $pending = @()
    try {
        foreach ($attempt in 1..2) {
            $request = New-Object System.Net.Http.HttpRequestMessage
            $request.Method = [System.Net.Http.HttpMethod]::Post
            $request.RequestUri = "http://localhost:9080/api/core/v1$path"
            $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
            $request.Content = New-Object System.Net.Http.StringContent(($body | ConvertTo-Json), [Text.Encoding]::UTF8, 'application/json')
            $requests += $request
            $pending += $client.SendAsync($request)
        }
        $statuses = foreach ($operation in $pending) {
            $response = $operation.GetAwaiter().GetResult()
            [int]$response.StatusCode
            $response.Dispose()
        }
        if ((($statuses | Sort-Object) -join ',') -ne '204,409') { throw "Concurrent completion expected 204,409 got $statuses" }
        Write-Host 'PASS concurrent completion -> one 204 and one 409'
    } finally { foreach ($request in $requests) { $request.Dispose() } }
}
function Call($method, $path, $token, $body, $expected) {
    $request = New-Object System.Net.Http.HttpRequestMessage
    $request.Method = New-Object System.Net.Http.HttpMethod($method)
    $request.RequestUri = "http://localhost:9080/api/core/v1$path"
    if ($token) { $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token) }
    if ($null -ne $body) { $request.Content = New-Object System.Net.Http.StringContent(($body | ConvertTo-Json -Depth 10), [Text.Encoding]::UTF8, 'application/json') }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $status = [int]$response.StatusCode
    $request.Dispose()
    $response.Dispose()
    if ($status -ne $expected) { throw "$method $path expected $expected got $status : $text" }
    Write-Host "PASS $method $path -> $status"
    if ($text) {
        $parsed = $text | ConvertFrom-Json
        $contractResponses.Add(@{method=$method; path=$path; status=$status; bodyJson=$text})
        return $parsed
    }
}
try {
    # user1..user7: xem bảng tài khoản demo trong README
    $requesterA = Token 'user1'
    $agentA = Token 'user2'
    $coordinatorA = Token 'user3'
    $agentB = Token 'user5'
    $coordinatorB = Token 'user6'
    $platformAdmin = Token 'user7'
    if ($ComposeProject) {
        Direct '/me' '' 401
        Direct '/me' $requesterA 200
        Direct '/workspaces/lab-a/tasks?scope=candidate' $agentB 403
    }
    $unauthenticated = Call GET '/me' '' $null 401
    if ($unauthenticated.code -ne 'UNAUTHENTICATED' -or !$unauthenticated.requestId) { throw '401 body must carry code and requestId' }
    Call GET '/me' 'invalid' $null 401 | Out-Null
    $me = Call GET '/me' $requesterA $null 200
    if ($me.username -ne 'user1' -or $me.platformAdmin -or @($me.memberships).Count -ne 1 -or
        $me.memberships[0].workspace -ne 'lab-a' -or $me.memberships[0].role -ne 'requester') { throw 'Unexpected /me memberships' }
    $adminMe = Call GET '/me' $platformAdmin $null 200
    # Request ID: giữ X-Request-ID hợp lệ từ client/gateway, thay thế giá trị không hợp lệ
    $traceId = [guid]::NewGuid().ToString()
    foreach ($sent in @($traceId, 'not-a-uuid')) {
        $echo = Invoke-WebRequest -UseBasicParsing 'http://localhost:9080/api/core/v1/me' -Headers @{Authorization="Bearer $requesterA"; 'X-Request-ID'=$sent}
        $returned = [string]$echo.Headers['X-Request-ID']
        $returnedGuid = [guid]::Empty
        if (![guid]::TryParse($returned, [ref]$returnedGuid) -or (($sent -eq $traceId) -ne ($returned -eq $traceId))) { throw "Unexpected X-Request-ID $returned for $sent" }
    }
    Write-Host 'PASS X-Request-ID propagation'
    if (!$adminMe.platformAdmin -or @($adminMe.memberships).Count -ne 0) { throw 'Unexpected platform admin identity' }
    $sha = [Security.Cryptography.SHA256]::Create()
    $checksum = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes('Synthetic Core probe document.'))).Replace('-', '').ToLowerInvariant()
    $sha.Dispose()
    $data = @{title='Local demonstration'; source='team-authored:core-probe'; version='1'; usageRights='CC0-1.0'; checksum=$checksum}
    $denied = Call POST '/workspaces/lab-a/metadata' $requesterA $data 403
    if ($denied.code -ne 'FORBIDDEN') { throw '403 body must carry code FORBIDDEN' }
    Call POST '/workspaces/lab-a/metadata' $agentB $data 403 | Out-Null
    Call POST '/workspaces/lab-a/metadata' $agentA @{title='invalid'} 400 | Out-Null
    Call POST '/workspaces/lab-a/metadata' $agentA ($data + @{owner='user1'}) 400 | Out-Null
    $record = Call POST '/workspaces/lab-a/metadata' $agentA $data 201
    Call POST '/workspaces/lab-a/metadata' $coordinatorA $data 201 | Out-Null
    $read = Call GET "/workspaces/lab-a/metadata/$($record.id)" $requesterA $null 200
    if ($read.source -ne $data.source -or $read.version -ne '1' -or $read.workspace -ne 'lab-a') { throw 'Metadata round trip mismatch' }
    Call GET "/workspaces/lab-a/metadata/$($record.id)" $agentB $null 403 | Out-Null
    Call GET "/workspaces/lab-b/metadata/$($record.id)" $agentB $null 404 | Out-Null
    Call GET "/workspaces/lab-a/metadata/$($record.id)" $platformAdmin $null 403 | Out-Null
    $auditPath = '/workspaces/lab-a/audit'
    Call GET $auditPath $agentA $null 403 | Out-Null
    Call GET $auditPath $coordinatorB $null 403 | Out-Null
    Call GET "$auditPath`?size=101" $coordinatorA $null 400 | Out-Null
    Call GET $auditPath $platformAdmin $null 200 | Out-Null
    $agentSub = (Call GET '/me' $agentA $null 200).sub
    $created = @((Call GET "$auditPath`?action=metadata.create&actor=$agentSub&size=100" $coordinatorA $null 200).items | Where-Object { $_.resource -eq $record.id })
    if ($created.Count -ne 1 -or $created[0].resourceType -ne 'metadata' -or $created[0].workspace -ne 'lab-a' -or !$created[0].requestId) { throw 'metadata.create audit mismatch' }
    $payload = [Text.Encoding]::UTF8.GetBytes('Synthetic integration file; CC0-1.0.')
    $file = Upload $agentA $payload 'integration.txt' 'text/plain' 201
    Upload $agentA (New-Object byte[] 5242881) 'oversize.txt' 'text/plain' 413 | Out-Null
    Upload $agentA $payload 'blocked.exe' 'application/octet-stream' 415 | Out-Null
    Call GET "/workspaces/lab-a/files/$($file.id)" $requesterA $null 200 | Out-Null
    Call GET "/workspaces/lab-b/files/$($file.id)" $agentB $null 404 | Out-Null
    Call GET "/workspaces/lab-a/files/$($file.id)" $platformAdmin $null 403 | Out-Null
    $linked = Call POST '/workspaces/lab-a/metadata' $agentA @{
        title='Linked runtime file'; source='team-authored:integration'; version='1'; usageRights='CC0-1.0'; fileId=$file.id
    } 201
    if ($linked.fileId -ne $file.id -or $linked.checksum -ne $file.checksum -or $linked.PSObject.Properties.Name -contains 'file_id') { throw 'File-backed JSON contract mismatch' }
    $matches = Call GET '/workspaces/lab-a/metadata?q=Linked%20runtime%20file&size=100' $requesterA $null 200
    if ($linked.id -notin @($matches.items.id)) { throw 'Linked metadata missing from search' }
    $foreignMatches = Call GET '/workspaces/lab-b/metadata?q=Linked%20runtime%20file&size=100' $agentB $null 200
    if ($linked.id -in @($foreignMatches.items.id)) { throw 'Cross-workspace search leak' }
    $download = New-Object System.Net.Http.HttpRequestMessage
    $download.Method = [System.Net.Http.HttpMethod]::Get
    $download.RequestUri = "http://localhost:9080/api/core/v1/workspaces/lab-a/files/$($file.id)/content"
    $download.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $requesterA)
    $response = $client.SendAsync($download).GetAwaiter().GetResult()
    try {
        if ([int]$response.StatusCode -ne 200 -or $response.Content.Headers.ContentDisposition.DispositionType -ne 'attachment' -or
            ($response.Headers.GetValues('X-Content-Type-Options') -join '') -ne 'nosniff' -or
            [Convert]::ToBase64String($response.Content.ReadAsByteArrayAsync().Result) -ne [Convert]::ToBase64String($payload)) { throw 'File download headers/content mismatch' }
    } finally { $response.Dispose(); $download.Dispose() }
    foreach ($action in @('file.upload', 'file.link')) {
        $entries = @((Call GET "$auditPath`?action=$action&size=100" $coordinatorA $null 200).items | Where-Object { $_.resource -eq $file.id })
        if ($entries.Count -ne 1 -or $entries[0].actor -ne $agentSub) { throw "$action audit mismatch" }
    }
    Call GET '/process-definitions' $requesterA $null 200 | Out-Null
    foreach ($key in @('document-review','equipment-approval')) {
        $input = @{businessKey="probe-$([guid]::NewGuid())"; title='Core reuse probe'}
        $base = '/workspaces/lab-a'
        Call POST "$base/processes/$key/instances" $agentB $input 403 | Out-Null
        $instance = Call POST "$base/processes/$key/instances" $requesterA $input 201
        Call POST "$base/processes/$key/instances" $requesterA $input 409 | Out-Null
        $path = "$base/instances/$($instance.id)"
        $started = @((Call GET "$auditPath`?action=process.start&size=100" $coordinatorA $null 200).items | Where-Object { $_.resource -eq $instance.id })
        if ($started.Count -ne 1 -or $started[0].details.processKey -ne $key -or $started[0].actor -ne $me.sub) { throw 'process.start audit mismatch' }
        Call GET "$path/history" $agentA $null 404 | Out-Null
        Call GET "$path/tasks" $agentB $null 403 | Out-Null
        $steps = 0
        while ($true) {
            $tasks = @(Call GET "$path/tasks" $requesterA $null 200)
            if ($tasks.Count -eq 0) { break }
            if ($tasks.Count -ne 1 -or $steps -gt 3) { throw 'Unexpected workflow structure' }
            $task = $tasks[0]
            $complete = "$path/tasks/$($task.id)/complete"
            $assignee = if ($task.key -eq 'handover') { $agentA } else { $coordinatorA }
            $outcome = if ($task.key -eq 'handover') { 'delivered' } else { 'approved' }
            $candidates = Call GET "$base/tasks?scope=candidate" $assignee $null 200
            if ($task.id -notin @($candidates.items.id)) { throw 'Task missing from candidate inbox' }
            Call POST "$path/tasks/$($task.id)/claim" $requesterA $null 403 | Out-Null
            Call POST "$path/tasks/$($task.id)/claim" $assignee $null 200 | Out-Null
            $assigned = Call GET "$base/tasks?scope=assigned" $assignee $null 200
            if ($task.id -notin @($assigned.items.id)) { throw 'Claimed task missing from assigned inbox' }
            Call POST "$path/tasks/$($task.id)/claim" $assignee $null 409 | Out-Null
            Call POST $complete $assignee @{expectedTaskKey='wrong-step'; outcome=$outcome} 409 | Out-Null
            Call POST $complete $requesterA @{expectedTaskKey=$task.key; outcome=$outcome} 403 | Out-Null
            RaceComplete $complete $assignee @{expectedTaskKey=$task.key; outcome=$outcome}
            Call POST $complete $assignee @{expectedTaskKey=$task.key; outcome=$outcome} 409 | Out-Null
            $steps++
        }
        $history = Call GET "$path/history" $requesterA $null 200
        $expectedSteps = if ($key -eq 'document-review') { 1 } else { 2 }
        if (!$history.ended -or $history.tasks.Count -ne $expectedSteps -or $steps -ne $expectedSteps) { throw 'Engine history mismatch' }
        foreach ($task in $history.tasks) {
            $expectedActor = if ($task.key -eq 'handover') { $agentSub } else { (Call GET '/me' $coordinatorA $null 200).sub }
            if (!$task.endedAt -or $task.actor -ne $expectedActor) { throw 'Missing task actor/time' }
        }
    }
    Write-Host 'PASS headless identity, workspace roles, file/metadata linking, audit, isolation and two independent approval workflows'
} finally {
    New-Item -ItemType Directory -Path .local -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $PWD '.local/core-integration-http.json'), (ConvertTo-Json -InputObject $contractResponses.ToArray() -Depth 30))
    $client.Dispose()
}
