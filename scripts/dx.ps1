# Loi vao ngan cho moi lenh van hanh: dx <lenh> (dx.cmd o goc repo goi file nay).
# dx hoac dx start: menu chon bang phim mui ten. Chi goi lai cac script san co, khong them logic khoi dong moi.
param([Parameter(Position = 0)][string]$Command = '', [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest = @())
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. "$PSScriptRoot/docker-native.ps1"

$project = 'dx-platform'
$qwenContainer = "$project-ai07-qwen-1"
$webUrl = 'http://localhost:5173'
$tick = [string][char]0x221A
# AI bat buoc: chi co Qwen local hoac API (khong chay chi bo luat / tat AI qua dx)
$aiModes = [ordered]@{ model = 'Model'; api = 'Api'; ask = 'AskApi' }
$aiSavedModes = @('model', 'api')

function Show-Help {
    Write-Host @'
Smart Service Desk

  dx start              Interactive menu (Up/Down, Enter, Esc)
  dx launch             Guided start: checks -> AI mode -> start -> open web
  dx doctor             Check Docker, disk, GPU, .env, ports, containers
  dx env                Create / update .env from .env.example
  dx up [mode]          Start; mode: model | model ask | api
  dx down               Stop all containers, keep data
  dx restart [mode]     down + up
  dx ps                 Container status
  dx logs [service]     Follow logs (default: platform keycloak gateway)
  dx qwen               Restart Qwen when AI is unavailable
  dx realm              Re-import the Keycloak realm from template
  dx schedule           Seed demo duty shifts (this week + next week)
  dx smoke              Smoke test the running stack
  dx check              Validate Compose config and database
  dx open               Open http://localhost:5173
  dx install            Add dx to PATH (done automatically on first run)

First run from the repo root: .\dx (PowerShell, cmd) or ./dx (Git Bash).
After that, in a new terminal: dx start
'@
}

# ---------- Tien ich ----------

function Read-Settings {
    $settings = @{}
    if (-not (Test-Path -LiteralPath '.env')) { return $settings }
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^([A-Z0-9_]+)=(.*)$') {
            $value = $Matches[2]
            if ($value.Length -ge 2 -and $value[0] -in @([char]34, [char]39) -and $value[0] -eq $value[$value.Length - 1]) { $value = $value.Substring(1, $value.Length - 2) }
            $settings[$Matches[1]] = $value
        }
    }
    return $settings
}

function Test-ApiKey($settings) {
    if (-not $settings['AI_API_PROVIDER'] -or -not $settings['AI_API_MODEL']) { return $false }
    if ($settings['AI_API_KEY']) { return $true }
    if ($settings['AI_API_KEY_FILE'] -and (Test-Path -LiteralPath $settings['AI_API_KEY_FILE'])) { return $true }
    $vendorKey = @{ gemini = 'GEMINI_API_KEY'; openai = 'OPENAI_API_KEY'; groq = 'GROQ_API_KEY' }[$settings['AI_API_PROVIDER']]
    return [bool]($vendorKey -and ($settings[$vendorKey] -or [Environment]::GetEnvironmentVariable($vendorKey)))
}

function Test-DockerEngine {
    & docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

function Test-QwenImage($settings) {
    $image = if ($settings['LOCAL_MODEL_IMAGE']) { $settings['LOCAL_MODEL_IMAGE'] } else { 'ssd-ai07-qwen-full:a40d655f95f8ed6b' }
    & docker image inspect $image --format '{{.Id}}' 2>$null | Out-Null
    return @{ Name = $image; Present = ($LASTEXITCODE -eq 0) }
}

function Get-GpuFreeMiB {
    if (-not (Get-Command nvidia-smi -ErrorAction SilentlyContinue)) { return $null }
    # Doc het output roi moi lay dong dau: cat ngang pipeline lam sai ma thoat
    $lines = @(& nvidia-smi --query-gpu=memory.free --format=csv,noheader,nounits 2>$null)
    if ($LASTEXITCODE -ne 0 -or $lines.Count -eq 0) { return $null }
    $free = 0
    if (-not [int]::TryParse("$($lines[0])".Trim(), [ref]$free)) { return $null }
    return $free
}

# Moi container cua project, ke ca service AI nam o overlay rieng
function Get-ProjectContainers {
    Invoke-Docker ps -q --filter "label=com.docker.compose.project=$project"
}

# ---------- Lenh ----------

function Invoke-Up([string[]]$Options) {
    $switches = @{}
    foreach ($item in $Options) {
        if (-not $aiModes.Contains($item)) { Write-Host "Unknown option '$item'. See: dx help"; $script:ok = $false; return }
        $switches[$aiModes[$item]] = $true
    }
    if ($Options -contains 'ask' -and -not ($Options -contains 'model')) { Write-Host "'ask' goes with model: dx up model ask"; $script:ok = $false; return }
    $settings = Read-Settings
    # Chua co che do AI hop le (may moi, hoac da luu bo luat / tat): tu chon theo may
    if ($switches.Count -eq 0 -and $settings['AI_MODE'] -notin $aiSavedModes) {
        $auto = Select-AiMode $settings
        if ($auto.Count -eq 0) {
            Write-Host 'AI is required: need an NVIDIA GPU with the Qwen image, or an API key in .env (AI_API_PROVIDER, AI_API_MODEL, AI_API_KEY).' -ForegroundColor Red
            Write-Host 'Run: dx doctor'
            $script:ok = $false
            return
        }
        Write-Host "Picked AI mode '$($auto -join ' ')' for this machine. Change later: dx up model | model ask | api"
        foreach ($item in $auto) { $switches[$aiModes[$item]] = $true }
    }
    # start.ps1 nem loi khi that bai; toi day la da khoi dong xong
    & "$PSScriptRoot/start.ps1" @switches
}

# Che do AI may nay chay duoc; rong khi khong co GPU + image Qwen lan API key
function Select-AiMode($settings) {
    $hasKey = Test-ApiKey $settings
    $gpu = Get-GpuFreeMiB
    if ($null -ne $gpu -and (Test-DockerEngine) -and (Test-QwenImage $settings).Present) {
        if ($hasKey) { return @('model', 'ask') }
        return @('model')
    }
    if ($hasKey) { return @('api') }
    return @()
}

function Invoke-Down {
    $ids = @(Get-ProjectContainers)
    if ($ids.Count -eq 0) { Write-Host 'Nothing is running.'; return }
    Invoke-Docker stop @ids | Out-Null
    if ($LASTEXITCODE -ne 0) { $script:ok = $false; return }
    Write-Host "Stopped $($ids.Count) containers. Data is kept."
}

function Invoke-Qwen {
    Invoke-Docker restart $qwenContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "Container $qwenContainer not found. Run: dx up model"; $script:ok = $false; return }
    Write-Host 'Reloading the model on the GPU (about 1 minute)...'
    for ($i = 0; $i -lt 36; $i++) {
        Start-Sleep -Seconds 5
        $status = Invoke-Docker inspect $qwenContainer --format '{{.State.Health.Status}}'
        if ($status -eq 'healthy') { Write-Host 'Qwen is ready.'; return }
    }
    Write-Host 'Qwen is not ready after 3 minutes. Run: dx doctor'
    $script:ok = $false
}

function Invoke-Check {
    Invoke-Docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { $script:ok = $false; return }
    Get-Content scripts/check-db.sql -Raw | docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { $script:ok = $false }
}

# Kiem tra may: moi muc in OK / CANH BAO / LOI kem cach sua; khong sua gi tu dong
# -PreFlight: chi in muc loi / canh bao, bo phan container (chua chay), khong in tong ket
function Invoke-Doctor([switch]$PreFlight) {
    $script:problems = 0
    function Report([string]$Level, [string]$Text, [string]$Fix = '') {
        if ($PreFlight -and $Level -in 'OK', 'INFO') { return }
        $color = @{ OK = 'Green'; WARN = 'Yellow'; FAIL = 'Red'; INFO = 'Gray' }[$Level]
        $tag = @{ OK = '[ OK ]'; WARN = '[WARN]'; FAIL = '[FAIL]'; INFO = '[ -- ]' }[$Level]
        Write-Host "$tag $Text" -ForegroundColor $color
        if ($Fix) { Write-Host "       -> $Fix" }
        if ($Level -eq 'FAIL') { $script:problems++ }
    }
    $settings = Read-Settings

    # Docker
    $engine = Test-DockerEngine
    if ($engine) { Report OK 'Docker is running' }
    else { Report FAIL 'Docker is not responding' 'Start Docker Desktop. Still HTTP 500: the Docker disk may be full (see disk checks)' }

    # O dia cua Docker va cua du an
    $dockerDir = "$env:LOCALAPPDATA\Docker\wsl"
    $store = "$env:APPDATA\Docker\settings-store.json"
    if (Test-Path -LiteralPath $store) {
        $custom = (Get-Content -LiteralPath $store -Raw | ConvertFrom-Json).CustomWslDistroDir
        if ($custom) { $dockerDir = $custom }
    }
    $drives = [ordered]@{}
    $drives[(Split-Path -Qualifier $dockerDir)] = 'Docker data'
    $repoDrive = Split-Path -Qualifier (Get-Location).Path
    if (-not $drives.Contains($repoDrive)) { $drives[$repoDrive] = 'project' }
    if (-not $drives.Contains($env:SystemDrive)) { $drives[$env:SystemDrive] = 'system' }
    foreach ($drive in $drives.Keys) {
        $disk = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='$drive'" -ErrorAction SilentlyContinue
        if (-not $disk) { continue }
        $freeGb = [math]::Round($disk.FreeSpace / 1GB, 1)
        $label = "Drive $drive ($($drives[$drive])): $freeGb GB free"
        if ($drives[$drive] -eq 'system') {
            if ($freeGb -lt 5) { Report WARN $label 'Free up space (Disk Cleanup)' } else { Report OK $label }
        }
        elseif ($freeGb -lt 5) { Report FAIL $label 'Docker fails when full. Free space or move it: Docker Desktop > Settings > Resources > Advanced > Disk image location' }
        elseif ($freeGb -lt 15) { Report WARN $label 'Qwen images need 8-10 GB; keep 15 GB free' }
        else { Report OK $label }
    }

    # .env
    if (-not (Test-Path -LiteralPath '.env')) { Report FAIL 'No .env file' 'Run: dx env' }
    else {
        $templateNames = @(Get-Content -LiteralPath '.env.example' -Encoding UTF8 | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object { ($_ -split '=', 2)[0] })
        $missing = @($templateNames | Where-Object { -not $settings.ContainsKey($_) })
        if ($missing.Count -gt 0) { Report WARN ".env is missing $($missing.Count) new keys ($($missing[0..([math]::Min(2, $missing.Count - 1))] -join ', ')...)" 'Run: dx env (keeps existing values)' }
        else { Report OK '.env is complete' }
    }

    # Cong bi chuong trinh khac chiem
    foreach ($port in 5173, 9080, 8081) {
        $owners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { (Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName } | Select-Object -Unique)
        $foreign = @($owners | Where-Object { $_ -and $_ -notmatch '^(com\.docker|wslrelay|vpnkit|docker)' })
        if ($foreign.Count -gt 0 -and $port -eq 5173 -and $foreign -contains 'node') { Report WARN "Port $port is used by 'node' (npm run dev?)" 'Fine for dev; stop npm run dev to use the frontend container' }
        elseif ($foreign.Count -gt 0) { Report FAIL "Port $port is used by '$($foreign -join ', ')'" 'Stop that program or change the port in compose' }
        elseif ($owners.Count -gt 0) { Report OK "Port $port is served by Docker" }
        else { Report OK "Port $port is free" }
    }

    # Che do AI
    $mode = if ($settings['AI_MODE']) { $settings['AI_MODE'] } else { 'disabled' }
    $ask = if ($settings['AI_ASSISTANCE_MODE']) { $settings['AI_ASSISTANCE_MODE'] } else { 'disabled' }
    Report INFO "Saved AI mode: triage '$mode', Ask AI '$ask'"
    $hasKey = Test-ApiKey $settings
    # AI bat buoc: may phai chay duoc it nhat mot che do co model
    if ($engine -and (Select-AiMode $settings).Count -eq 0) {
        Report FAIL 'No AI available: need an NVIDIA GPU with the Qwen image, or an API key' 'Fill AI_API_PROVIDER, AI_API_MODEL, AI_API_KEY in .env (or load the Qwen image)'
    }
    elseif ($mode -notin $aiSavedModes) { Report WARN "Saved AI mode '$mode' has no model" 'dx start will pick Qwen or API for this machine' }
    if ($mode -eq 'api' -or $ask -eq 'api') {
        if ($hasKey) { Report OK "API key set for '$($settings['AI_API_PROVIDER'])' (not shown)" }
        else { Report FAIL 'API mode but AI_API_PROVIDER / AI_API_MODEL / key is missing' 'Fill them in .env, then: dx up' }
    }
    $gpuFree = Get-GpuFreeMiB
    if ($mode -eq 'model') {
        if ($null -eq $gpuFree) { Report FAIL 'Qwen mode but no NVIDIA GPU found' 'Install the NVIDIA driver, or switch: dx up api' }
        else {
            $qwenRunning = (& docker inspect $qwenContainer --format '{{.State.Running}}' 2>$null) -eq 'true'
            if ($qwenRunning) { Report OK "NVIDIA GPU, $gpuFree MiB VRAM free (Qwen loaded)" }
            elseif ($gpuFree -lt 2048) { Report WARN "Only $gpuFree MiB VRAM free, Qwen needs about 2 GB" 'Close games or other GPU apps' }
            else { Report OK "NVIDIA GPU, $gpuFree MiB VRAM free" }
        }
        if ($engine) {
            $image = Test-QwenImage $settings
            if ($image.Present) { Report OK "Qwen image $($image.Name)" }
            else { Report FAIL "Qwen image $($image.Name) not found" 'docker load -i <team image .tar>, or build it (infra/compose/README.ai07-qwen-lora.md), then set LOCAL_MODEL_IMAGE' }
        }
    }

    if ($PreFlight) { return }

    # Container dang chay
    if ($engine) {
        # Ten container dang <project>-<service>-<so>; PowerShell 5.1 bo dau nhay nen khong dung {{.Label "..."}}
        $rows = @(Invoke-Docker ps -a --filter "label=com.docker.compose.project=$project" --format '{{.Names}}|{{.State}}|{{.Status}}')
        if ($rows.Count -eq 0) { Report INFO 'Not running. Run: dx start' }
        foreach ($row in $rows) {
            $name, $state, $status = $row -split '\|', 3
            $service = $name -replace "^$project-", '' -replace '-\d+$', ''
            if ($status -match 'unhealthy') {
                $fix = if ($service -eq 'ai07-qwen') { 'Qwen stopped after an error (often GPU idle clocks). Run: dx qwen' } else { "See logs: dx logs $service" }
                Report FAIL "$service : $status" $fix
            }
            elseif ($state -eq 'running') { Report OK "$service : $status" }
            elseif ($service -ne 'adminer') { Report WARN "$service : $status" 'Run: dx start' }
        }
    }

    Write-Host ''
    if ($script:problems -eq 0) { Write-Host 'No blocking issues. Ready: dx start' -ForegroundColor Green; return }
    Write-Host "$($script:problems) issue(s) to fix first." -ForegroundColor Red
    $script:ok = $false
}

# Them thu muc du an vao PATH cua tai khoan (khong can quyen admin); mo terminal moi de co hieu luc
function Install-Dx([switch]$Automatic) {
    $root = (Get-Location).Path
    $current = [Environment]::GetEnvironmentVariable('Path', 'User')
    $entries = @($current -split ';' | Where-Object { $_ })
    if ($entries | Where-Object { $_.TrimEnd([char]92) -ieq $root.TrimEnd([char]92) }) {
        if (-not $Automatic) { Write-Host "Already in PATH: $root" }
        return
    }
    [Environment]::SetEnvironmentVariable('Path', (($entries + $root) -join ';'), 'User')
    if ($Automatic) { Write-Host "Tip: dx was added to your PATH. In a new terminal just type: dx start" -ForegroundColor DarkGray }
    else { Write-Host "Added to PATH: $root"; Write-Host 'Open a new terminal, then run: dx start' }
}

# Lan dau chay dx tren may nay: tu them vao PATH mot lan (git khong cho chay lenh luc clone).
# Bo qua tren CI hoac khi dat DX_NO_PATH=1; lam mot lan nen go khoi PATH thi khong tu them lai.
function Register-DxOnce {
    $marker = '.local/dx-path-registered'
    if ($env:CI -or $env:DX_NO_PATH -or (Test-Path -LiteralPath $marker)) { return }
    try {
        Install-Dx -Automatic
        New-Item -ItemType Directory -Force -Path (Split-Path $marker) | Out-Null
        Set-Content -LiteralPath $marker -Value (Get-Date -Format o) -Encoding ASCII
    } catch { }
}

# Chay mot lenh; that bai thi $script:ok = $false (output van hien ra man hinh)
function Invoke-Dx([string]$Name, [string[]]$Options = @()) {
    $script:ok = $true
    try {
        switch ($Name) {
            'env' { & "$PSScriptRoot/setup.ps1" }
            'doctor' { Invoke-Doctor }
            'up' { Invoke-Up $Options }
            'down' { Invoke-Down }
            'restart' { Invoke-Down; if ($script:ok) { Invoke-Up $Options } }
            'ps' { Invoke-Docker compose -p $project ps -a }
            'logs' {
                $services = if ($Options.Count -gt 0) { $Options } else { @('platform', 'keycloak', 'gateway') }
                & docker compose -p $project logs -f --tail 100 @services
            }
            'realm' { & "$PSScriptRoot/start.ps1" -Reset }
            'schedule' { & "$PSScriptRoot/seed-schedule-demo.ps1" }
            'qwen' { Invoke-Qwen }
            'smoke' { & "$PSScriptRoot/smoke.ps1" }
            'check' { Invoke-Check }
            'open' { Start-Process $webUrl }
            'install' { Install-Dx }
            'launch' { Start-Project }
            { $_ -in 'help', '-h', '--help', '/?' } { Show-Help }
            default { Write-Host "Unknown command: $Name`n"; Show-Help; $script:ok = $false }
        }
    } catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host 'Run dx doctor to check your machine.'
        $script:ok = $false
    }
}

# ---------- Menu ----------

# Chon mot muc bang phim mui ten (kieu create-vite): Up/Down di chuyen, Enter chon, Esc thoat.
# Tra ve vi tri muc da chon, -1 khi thoat.
function Select-Option([string]$Title, [object[]]$Options) {
    # Terminal khong doc duoc phim (Git Bash khong qua winpty, pipe): chon bang so
    if ([Console]::IsInputRedirected) { return Select-OptionByNumber $Title $Options }
    Write-Host "? " -ForegroundColor Green -NoNewline
    Write-Host $Title
    $selected = 0
    $width = [Math]::Max(20, [Console]::WindowWidth - 1)
    $cursor = [Console]::CursorVisible
    [Console]::CursorVisible = $false
    try {
        # In truoc mot lan de console cuon xong, roi moi lay dong bat dau
        foreach ($option in $Options) { Write-Host '' }
        $top = [Console]::CursorTop - $Options.Count
        while ($true) {
            [Console]::SetCursorPosition(0, $top)
            for ($i = 0; $i -lt $Options.Count; $i++) {
                $label, $hint = $Options[$i]
                $line = if ($i -eq $selected) { "> $label" } else { "  $label" }
                if ($hint) { $line = $line.PadRight(42) }
                $text = if ($hint) { "$line$hint" } else { $line }
                if ($text.Length -gt $width) { $text = $text.Substring(0, $width) }
                if ($i -eq $selected) {
                    Write-Host $line.Substring(0, [Math]::Min($line.Length, $width)) -ForegroundColor Cyan -NoNewline
                    if ($hint -and $line.Length -lt $width) { Write-Host $text.Substring($line.Length) -ForegroundColor DarkGray -NoNewline }
                    Write-Host (' ' * [Math]::Max(0, $width - $text.Length))
                } else {
                    Write-Host $text.PadRight($width)
                }
            }
            $key = [Console]::ReadKey($true)
            switch ($key.Key) {
                'UpArrow' { $selected = ($selected - 1 + $Options.Count) % $Options.Count }
                'DownArrow' { $selected = ($selected + 1) % $Options.Count }
                'Home' { $selected = 0 }
                'End' { $selected = $Options.Count - 1 }
                'Enter' {
                    # Thu gon danh sach thanh dong ket qua, giong create-vite
                    [Console]::SetCursorPosition(0, $top)
                    for ($i = 0; $i -lt $Options.Count; $i++) { Write-Host (' ' * $width) }
                    [Console]::SetCursorPosition(0, $top - 1)
                    Write-Host "$tick " -ForegroundColor Green -NoNewline
                    Write-Host "$Title " -NoNewline
                    Write-Host $Options[$selected][0] -ForegroundColor Cyan
                    return $selected
                }
                'Escape' {
                    [Console]::SetCursorPosition(0, $top + $Options.Count)
                    return -1
                }
            }
        }
    } finally {
        [Console]::CursorVisible = $cursor
    }
}

function Select-OptionByNumber([string]$Title, [object[]]$Options) {
    Write-Host "? " -ForegroundColor Green -NoNewline
    Write-Host $Title
    for ($i = 0; $i -lt $Options.Count; $i++) {
        $label, $hint = $Options[$i]
        $line = ('{0,3}. {1}' -f ($i + 1), $label)
        if ($hint) { Write-Host $line.PadRight(44) -NoNewline; Write-Host $hint -ForegroundColor DarkGray } else { Write-Host $line }
    }
    $answer = Read-Host 'Type a number and press Enter (blank to exit)'
    $index = 0
    if (-not [int]::TryParse("$answer".Trim(), [ref]$index) -or $index -lt 1 -or $index -gt $Options.Count) { return -1 }
    return $index - 1
}

# Muc chon che do AI: nhan, goi y, tuy chon cua dx up
function Get-AiModeOptions {
    $settings = Read-Settings
    $options = @()
    # Chi hien "Saved" khi che do da luu co AI
    if ($settings['AI_MODE'] -in $aiSavedModes) { $options += , @(@("Saved ($($settings['AI_MODE']))", 'Use the current setting'), @()) }
    $options += , @(@('Qwen local', 'GPU + rules'), @('model'))
    $options += , @(@('Qwen local + Ask AI', 'GPU + API key in .env'), @('model', 'ask'))
    $options += , @(@('API only', 'Gemini / OpenAI / Groq, no GPU'), @('api'))
    return $options
}

function Write-Step([string]$Number, [string]$Text) {
    Write-Host ''
    Write-Host "[$Number] " -ForegroundColor Cyan -NoNewline
    Write-Host $Text
}

function Confirm-Choice([string]$Question, [string]$Yes, [string]$No) {
    return (Select-Option $Question @(@($Yes, ''), @($No, ''))) -eq 0
}

# Quy trinh khoi dong day du: kiem may -> chon AI -> chay -> in dia chi, tai khoan -> mo web
function Start-Project {
    Write-Step '1/4' 'Checking your machine'
    if (-not (Test-Path -LiteralPath '.env')) {
        Write-Host '  No .env yet, creating one with random passwords...'
        & "$PSScriptRoot/setup.ps1"
    }
    Invoke-Doctor -PreFlight
    if ($script:problems -eq 0) { Write-Host "  $tick No blocking issues" -ForegroundColor Green }
    # Muc dau ("dung lai") la mac dinh khi co loi
    elseif (Confirm-Choice 'Some checks failed. Continue anyway?' 'No, stop here' 'Yes, continue') {
        Write-Host '  Stopped. Fix the items above, then run: dx start'
        $script:ok = $false
        return
    }

    Write-Step '2/4' 'Choose AI mode'
    $modes = Get-AiModeOptions
    $mode = Select-Option 'AI mode' ($modes | ForEach-Object { , $_[0] })
    if ($mode -lt 0) { Write-Host '  Cancelled.'; return }
    $options = $modes[$mode][1]
    $settings = Read-Settings
    $needsKey = $options -contains 'api' -or $options -contains 'ask' -or ($options.Count -eq 0 -and ($settings['AI_MODE'] -eq 'api' -or $settings['AI_ASSISTANCE_MODE'] -eq 'api'))
    if ($needsKey -and -not (Test-ApiKey $settings)) {
        Write-Host '  ! This mode needs AI_API_PROVIDER, AI_API_MODEL and a key in .env' -ForegroundColor Yellow
        if (Confirm-Choice 'Continue without a key?' 'No, I will fill .env first' 'Yes, continue') { Write-Host '  Stopped. Edit .env, then run: dx start'; return }
    }

    Write-Step '3/4' 'Starting services (first run can take a few minutes)'
    Invoke-Up $options
    if (-not $script:ok) { return }

    Write-Step '4/4' 'Ready'
    Write-Host "  Web app    $webUrl"
    Write-Host '  API        http://localhost:9080'
    Write-Host '  Keycloak   http://localhost:8081'
    Write-Host '  Accounts   user1 requester, user2 agent, user3 coordinator (lab-a), superadmin all roles'
    Write-Host '  Password   DEMO_PASSWORD in .env'
    if (Confirm-Choice 'Open the web app now?' 'Yes' 'No') { Start-Process $webUrl }
}

function Show-Menu {
    $actions = @(
        @('Start project', 'Checks, AI mode, launch'),
        @('Stop', 'Keep data'),
        @('Doctor', 'Docker, disk, GPU, ports'),
        @('Status', ''),
        @('Logs', 'Backend, Ctrl+C to exit'),
        @('Restart Qwen', 'When AI is unavailable'),
        @('Open web app', $webUrl),
        @('Setup .env', ''),
        @('Exit', '')
    )
    Write-Host ''
    Write-Host ' Smart Service Desk ' -ForegroundColor Black -BackgroundColor Cyan
    Write-Host ''
    $choice = Select-Option 'What do you want to do?' $actions
    # Muc cuoi la Exit
    if ($choice -lt 0 -or $choice -eq $actions.Count - 1) { return }
    switch ($choice) {
        0 { Invoke-Dx 'launch' }
        1 { Invoke-Dx 'down' }
        2 { Invoke-Dx 'doctor' }
        3 { Invoke-Dx 'ps' }
        4 { Invoke-Dx 'logs' @('platform') }
        5 { Invoke-Dx 'qwen' }
        6 { Invoke-Dx 'open' }
        7 { Invoke-Dx 'env' }
    }
}

Register-DxOnce
if (-not $Command -or $Command -eq 'start') {
    Show-Menu
    if (-not $script:ok) { exit 1 }
    exit 0
}
Invoke-Dx $Command $Rest
if (-not $script:ok) { exit 1 }
