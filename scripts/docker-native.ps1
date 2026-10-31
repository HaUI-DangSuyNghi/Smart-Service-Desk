# PowerShell 5.1 otherwise turns Docker's normal stderr progress into a terminating
# error when the caller redirects logs with *>; use the actual native exit code.
Get-Command docker -ErrorAction Stop | Out-Null
function Invoke-Docker {
    $ErrorActionPreference = 'Continue'
    & docker @args 2>&1 | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) { Write-Host $_.ToString() }
        else { Write-Output $_ }
    }
    $nativeExit = $LASTEXITCODE
    Set-Variable -Name LASTEXITCODE -Scope 1 -Value $nativeExit
}

# APISIX can retain the previous backend DNS address when Compose replaces platform.
function Sync-GatewayUpstream {
    param([string[]]$ComposeArgs, [string]$PreviousPlatform)
    if (-not $PreviousPlatform) { return }
    $currentPlatform = Invoke-Docker @ComposeArgs ps -q platform
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the updated backend.' }
    if ($currentPlatform -and $currentPlatform -ne $PreviousPlatform) {
        Write-Host 'Refreshing gateway after backend replacement...'
        Invoke-Docker @ComposeArgs restart gateway
        if ($LASTEXITCODE -ne 0) { throw 'Cannot refresh gateway upstream.' }
        Invoke-Docker @ComposeArgs up -d --no-build --wait --wait-timeout 120 gateway
        if ($LASTEXITCODE -ne 0) { throw 'Gateway did not become ready after backend replacement.' }
    }
}
