$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)
$password = (Get-Content .env | Where-Object { $_ -match '^DEMO_PASSWORD=' }) -replace '^DEMO_PASSWORD=', ''
function Token($user) {
    $body = @{grant_type='password'; client_id='core-probe'; username=$user; password=$password}
    (Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/realms/dx-lab/protocol/openid-connect/token' -Body $body).access_token
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
    if ($text) { return ($text | ConvertFrom-Json) }
}
try {
    $alice = Token 'alice'
    $bob = Token 'bob'
    $mallory = Token 'mallory'
    Call GET '/me' '' $null 401 | Out-Null
    Call GET '/me' 'invalid' $null 401 | Out-Null
    $me = Call GET '/me' $alice $null 200
    $sha = [Security.Cryptography.SHA256]::Create()
    $checksum = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes('Synthetic Core probe document.'))).Replace('-', '').ToLowerInvariant()
    $sha.Dispose()
    $data = @{title='Local demonstration'; source='team-authored:core-probe'; version='1'; usageRights='CC0-1.0'; checksum=$checksum}
    Call POST '/workspaces/lab-a/metadata' $bob $data 403 | Out-Null
    Call POST '/workspaces/lab-a/metadata' $mallory $data 403 | Out-Null
    Call POST '/workspaces/lab-a/metadata' $alice @{title='invalid'} 400 | Out-Null
    Call POST '/workspaces/lab-a/metadata' $alice ($data + @{owner='bob'}) 400 | Out-Null
    $record = Call POST '/workspaces/lab-a/metadata' $alice $data 201
    $read = Call GET "/workspaces/lab-a/metadata/$($record.id)" $bob $null 200
    if ($read.source -ne $data.source -or $read.version -ne '1' -or $read.workspace -ne 'lab-a') { throw 'Metadata round trip mismatch' }
    Call GET "/workspaces/lab-a/metadata/$($record.id)" $mallory $null 403 | Out-Null
    Call GET "/workspaces/lab-b/metadata/$($record.id)" $mallory $null 404 | Out-Null
    foreach ($key in @('document-review','equipment-approval')) {
        $input = @{businessKey="probe-$([guid]::NewGuid())"; title='Core reuse probe'}
        $base = '/workspaces/lab-a'
        Call POST "$base/processes/$key/instances" $bob $input 403 | Out-Null
        $instance = Call POST "$base/processes/$key/instances" $alice $input 201
        Call POST "$base/processes/$key/instances" $alice $input 409 | Out-Null
        $path = "$base/instances/$($instance.id)"
        Call GET "$path/history" $bob $null 404 | Out-Null
        Call GET "$path/tasks" $mallory $null 403 | Out-Null
        $steps = 0
        while ($true) {
            $tasks = @(Call GET "$path/tasks" $alice $null 200)
            if ($tasks.Count -eq 0) { break }
            if ($tasks.Count -ne 1 -or $steps -gt 3) { throw 'Unexpected workflow structure' }
            $task = $tasks[0]
            $complete = "$path/tasks/$($task.id)/complete"
            Call POST $complete $alice @{expectedTaskKey='wrong-step'} 409 | Out-Null
            Call POST $complete $bob @{expectedTaskKey=$task.key} 404 | Out-Null
            RaceComplete $complete $alice @{expectedTaskKey=$task.key}
            Call POST $complete $alice @{expectedTaskKey=$task.key} 409 | Out-Null
            $steps++
        }
        $history = Call GET "$path/history" $alice $null 200
        $expectedSteps = if ($key -eq 'document-review') { 1 } else { 2 }
        if (!$history.ended -or $history.tasks.Count -ne $expectedSteps -or $steps -ne $expectedSteps) { throw 'Engine history mismatch' }
        foreach ($task in $history.tasks) {
            if (!$task.endedAt -or $task.actor -ne $me.sub) { throw 'Missing task actor/time' }
        }
    }
    Write-Host 'PASS headless identity, metadata, isolation and two real workflow definitions'
} finally { $client.Dispose() }
