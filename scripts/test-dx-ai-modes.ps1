# Exercise real dx/start scripts in an isolated project with Docker replaced by a recorder.
# Never starts/stops workstation containers, reads its .env, or changes its saved AI selection.
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$fixture = Join-Path $repository ('.local/dx-ai-mode-tests/' + [guid]::NewGuid().ToString('N'))
$utf8 = New-Object Text.UTF8Encoding $false
$shell = (Get-Process -Id $PID).Path
$oldNoPath = $env:DX_NO_PATH
$oldAppData = $env:APPDATA
$oldLocalAppData = $env:LOCALAPPDATA

function Assert-Mode([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Save-FixtureSettings([string]$Mode, [string]$Assistance) {
    [IO.File]::WriteAllLines((Join-Path $fixture '.env'), @("AI_MODE=$Mode", "AI_ASSISTANCE_MODE=$Assistance"), $utf8)
}
function Invoke-Fixture([string[]]$Options, [bool]$Success = $true) {
    $output = & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $fixture 'scripts/dx.ps1') up @Options
    Assert-Mode (($LASTEXITCODE -eq 0) -eq $Success) ("Unexpected dx exit code: $LASTEXITCODE`n" + ($output -join "`n"))
}

New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'scripts'),(Join-Path $fixture '.local') | Out-Null
foreach ($name in @('dx.ps1', 'start.ps1', 'stop-unused-model.ps1')) {
    Copy-Item -LiteralPath (Join-Path $repository "scripts/$name") -Destination (Join-Path $fixture "scripts/$name")
}
[IO.File]::WriteAllText((Join-Path $fixture 'scripts/setup.ps1'), '# Fixture already configured.', $utf8)
[IO.File]::WriteAllText((Join-Path $fixture 'scripts/docker-native.ps1'), @'
function Invoke-Docker {
    Add-Content -LiteralPath '.local/docker.log' -Value ($args -join ' ')
    Set-Variable -Name LASTEXITCODE -Scope 1 -Value 0
    if ($args -contains 'config') { '{"name":"dx-mode-test","services":{"platform":{"environment":{}}}}' }
}
function Sync-GatewayUpstream { }
'@, $utf8)
[IO.File]::WriteAllText((Join-Path $fixture '.env.example'), "AI_MODE=disabled`nAI_ASSISTANCE_MODE=disabled`n", $utf8)

$tokens = $null; $parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $repository 'scripts/dx.ps1'), [ref]$tokens, [ref]$parseErrors)
Assert-Mode ($parseErrors.Count -eq 0) 'dx.ps1 must parse on this PowerShell version.'
# Load only declarations, avoiding the entrypoint and PATH registration.
foreach ($node in $ast.EndBlock.Statements) {
    if (($node -is [Management.Automation.Language.FunctionDefinitionAst]) -or
        (($node -is [Management.Automation.Language.AssignmentStatementAst]) -and $node.Left.Extent.Text -in @('$aiModes', '$aiSavedModes'))) {
        Invoke-Expression $node.Extent.Text
    }
}

try {
    $env:DX_NO_PATH = '1'
    $env:APPDATA = $fixture
    $env:LOCALAPPDATA = $fixture
    Push-Location $fixture
    Save-FixtureSettings 'remote' 'api'
    $choices = @(Get-AiModeOptions)
    $noAi = @($choices | Where-Object { $_[0][0] -eq 'No AI' })
    Assert-Mode ($noAi.Count -eq 1 -and $noAi[0][1][0] -eq 'no-ai') 'Menu must offer No AI exactly once and dispatch no-ai.'
    Assert-Mode (-not (Test-AiModeNeedsKey @('no-ai') @{AI_MODE='remote';AI_ASSISTANCE_MODE='api'})) 'No AI must ignore stale API assistance settings.'
    Assert-Mode (-not (Test-AiModeNeedsKey @() @{AI_MODE='disabled';AI_ASSISTANCE_MODE='api'})) 'Saved disabled must not require an API key.'
    Assert-Mode (Test-AiModeNeedsKey @('api') @{}) 'API mode must still require a key.'
    Assert-Mode (Test-AiModeNeedsKey @('remote','ask') @{}) 'Ask AI must still require a key.'
    Assert-Mode (Test-AiModeNeedsKey @() @{AI_MODE='remote';AI_ASSISTANCE_MODE='api'}) 'Saved remote plus Ask AI must still require a key.'

    Invoke-Fixture @('no-ai')
    $settings = Read-Settings
    Assert-Mode ($settings['AI_MODE'] -eq 'disabled' -and $settings['AI_ASSISTANCE_MODE'] -eq 'disabled') 'Successful No AI startup must save both disabled settings.'
    $commands = [IO.File]::ReadAllText((Join-Path $fixture '.local/docker.log'))
    Assert-Mode ($commands.Contains('compose.ai-disabled.yaml') -and $commands.Contains('up -d --build --wait')) 'No AI must use the disabled overlay and start the base project.'
    Assert-Mode ($commands.Contains('compose.service=gpu-relay') -and $commands.Contains('stop intelligence')) 'No AI must clean up previous provider/rules services.'
    Assert-Mode (-not $commands.Contains('compose.hybrid.yaml') -and -not $commands.Contains('compose.model-remote.yaml')) 'No AI must not start GPU/remote model overlays.'
    Invoke-Fixture @()
    $settings = Read-Settings
    Assert-Mode ($settings['AI_MODE'] -eq 'disabled') 'Plain dx up must retain saved disabled mode.'
    $saved = @(Get-AiModeOptions)
    Assert-Mode ($saved[0][0][0] -eq 'Saved (disabled)') 'Saved disabled must be selectable in the menu.'

    $before = (Get-FileHash -Algorithm SHA256 -LiteralPath '.env').Hash
    Invoke-Fixture @('no-ai','model') $false
    Invoke-Fixture @('no-ai','ask') $false
    Assert-Mode ((Get-FileHash -Algorithm SHA256 -LiteralPath '.env').Hash -eq $before) 'Invalid combinations must not change saved mode.'

    # Doctor must allow choosing No AI despite an unavailable previous provider.
    function Test-DockerEngine { return $true }
    function Select-AiMode { throw 'Doctor must not auto-select AI.' }
    function Get-GpuFreeMiB { throw 'Disabled/preflight must not require a GPU.' }
    function Get-CimInstance { return $null }
    function Get-NetTCPConnection { return @() }
    function Invoke-Docker { Set-Variable -Name LASTEXITCODE -Scope 1 -Value 0 }
    Save-FixtureSettings 'remote' 'api'
    Invoke-Doctor -PreFlight
    Assert-Mode ($script:problems -eq 0) 'Preflight must not block the No AI choice because old GPU/API configuration is incomplete.'
    Save-FixtureSettings 'disabled' 'api'
    Invoke-Doctor
    Assert-Mode ($script:problems -eq 0) 'Doctor must accept disabled AI without a GPU or API key.'
    'PASS: No AI menu, CLI dispatch, disabled overlay, saved mode, cleanup, key checks, invalid combinations and doctor preflight.'
} finally {
    Pop-Location
    $env:DX_NO_PATH = $oldNoPath
    $env:APPDATA = $oldAppData
    $env:LOCALAPPDATA = $oldLocalAppData
}
