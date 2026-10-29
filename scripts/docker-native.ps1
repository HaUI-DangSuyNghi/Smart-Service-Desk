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
