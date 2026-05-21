# ai_agent_service.ps1 — Smart Safety AI Agent Windows Service manager
#
# Plan ref: A.3 of C:\Users\ANNA\.claude\plans\tbm-linear-dragonfly.md
# Created : 2026-05-21
#
# What this does:
#   Wraps ai_agent/main.py as a Windows service via NSSM so the PC headlessly
#   runs YOLO inference + Supabase ingestion after reboot. Removes the
#   "user must launch PowerShell" operational burden in 검단/포천 pilot sites.
#
# Why TWO log files exist (intentional, do not consolidate):
#   logs/ai_agent.log
#     -> Python logging.RotatingFileHandler (set up in ai_agent/main.py).
#        Structured, level-tagged, app-internal events. 5MB x 5 rotation.
#   logs/ai_agent_service.stdout.log + .stderr.log
#     -> NSSM raw process stdout/stderr capture.
#        Catches Python import errors, torch CUDA init failures, dotenv parse
#        errors — events that happen BEFORE _configure_logging() runs.
#        If "service Running but ai_agent.log silent" symptom appears,
#        the answer is almost always in stderr.log.
#
# Usage:
#   # one-time install (admin required, will prompt for current-user password)
#   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Install
#
#   # check it actually works (no admin)
#   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Status
#   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Logs
#
#   # control (admin required)
#   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Restart
#   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Stop
#   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Start
#   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Uninstall
#
# Conventions:
#   English-only console output (matches scripts/demo_rtsp_real_camera.ps1).
#   PowerShell 5.1 + 7.x compatible (no ternary, no null-coalescing).

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("Install","Uninstall","Status","Logs","Restart","Stop","Start")]
    [string]$Action,

    # Account the service runs under. Default = current user.
    # CRITICAL: must be a user account, NOT LocalSystem — PyTorch/YOLO
    # needs the user session to see GPU via NVIDIA driver isolation.
    [string]$ServiceUser = "",

    # Python interpreter. Default matches existing demo_rtsp_real_camera.ps1.
    [string]$PythonPath = "C:\Users\ANNA\miniconda3\python.exe",

    # Extra args appended to "python -u main.py" (advanced).
    [string]$ExtraArgs = "",

    # Tail length for -Action Logs.
    [int]$LogTail = 50,

    # Skip the Tcpip/Dnscache dependency (debugging only — Supabase needs network).
    [switch]$SkipNetworkDeps
)

$ErrorActionPreference = "Stop"

# === Constants ===
$ServiceName = "SmartSafetyAiAgent"
$DisplayName = "Smart Safety AI Agent"
$ServiceDesc = "PC headless worker: RTSP capture + YOLO inference + Supabase ingestion. Plan A.3."

# === Resolve repo paths ===
$RepoRoot   = Split-Path -Parent $PSScriptRoot
$AiAgentDir = Join-Path $RepoRoot "ai_agent"
$MainPy     = Join-Path $AiAgentDir "main.py"
$LogsDir    = Join-Path $RepoRoot "logs"
$StdoutLog  = Join-Path $LogsDir "ai_agent_service.stdout.log"
$StderrLog  = Join-Path $LogsDir "ai_agent_service.stderr.log"
$PyAppLog   = Join-Path $LogsDir "ai_agent.log"

# === Helpers ===

function Assert-Admin {
    $current   = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($current)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Administrator rights required. Right-click PowerShell -> Run as administrator, then re-run."
    }
}

function Resolve-NssmPath {
    # 1) Already on PATH?
    $cmd = Get-Command nssm -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    # 2) Local sidecar
    $localNssm = Join-Path $PSScriptRoot "_nssm\nssm.exe"
    if (Test-Path $localNssm) { return $localNssm }

    # 3) winget install (Windows 11)
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "NSSM not found. Attempting: winget install NSSM.NSSM" -ForegroundColor Yellow
        try {
            winget install --id NSSM.NSSM --silent --accept-source-agreements --accept-package-agreements
        } catch {
            Write-Warning "winget invocation failed: $_"
        }
        # Refresh PATH for current session (winget adds entries we can't see otherwise)
        $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
        $userPath    = [Environment]::GetEnvironmentVariable("Path", "User")
        $env:Path    = "$machinePath;$userPath"
        $cmd = Get-Command nssm -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
        # Common winget Links location fallback
        $wingetLink = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Links\nssm.exe"
        if (Test-Path $wingetLink) { return $wingetLink }
    }

    throw @"
NSSM not installed. Pick ONE install path:
  1) winget install NSSM.NSSM            (recommended on Windows 11)
  2) choco install nssm                  (if Chocolatey present)
  3) Download nssm.exe from https://nssm.cc/download (win64) and place at:
     $localNssm
Then re-run this script.
"@
}

function Format-StatusColor($status) {
    if ($status -eq "Running") { return "Green" }
    if ($status -eq "Stopped") { return "Red" }
    return "Yellow"
}

# === Action: Install ===

function Action-Install {
    Assert-Admin

    if (-not (Test-Path $MainPy))     { throw "ai_agent/main.py not found at: $MainPy" }
    if (-not (Test-Path $PythonPath)) { throw "Python interpreter not found at: $PythonPath  (override with -PythonPath)" }

    New-Item -Path $LogsDir -ItemType Directory -Force | Out-Null

    $nssm = Resolve-NssmPath
    Write-Host "Using NSSM: $nssm" -ForegroundColor Cyan

    # Pre-flight: existing service?
    $existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "Existing service '$ServiceName' detected -- stopping and removing first..." -ForegroundColor Yellow
        if ($existing.Status -eq "Running") {
            & $nssm stop $ServiceName confirm | Out-Null
            Start-Sleep -Seconds 2
        }
        & $nssm remove $ServiceName confirm | Out-Null
        Start-Sleep -Seconds 2
    }

    # Resolve service account
    if (-not $ServiceUser) {
        $ServiceUser = ".\$([Environment]::UserName)"
        Write-Host "ServiceUser default = $ServiceUser (current user)" -ForegroundColor Cyan
        Write-Host "  Reason: PyTorch/YOLO needs user session to see GPU. LocalSystem will silently fall back to CPU." -ForegroundColor Cyan
    }

    Write-Host "" -ForegroundColor Green
    Write-Host "Registering service '$ServiceName'..." -ForegroundColor Green
    # Two-step: install program only, then set AppParameters separately.
    # Survives spaces in paths and stores parameters as one inspectable registry value.
    & $nssm install $ServiceName $PythonPath
    if ($LASTEXITCODE -ne 0) { throw "nssm install failed (exit $LASTEXITCODE)" }

    $appParams = "-u `"$MainPy`""
    if ($ExtraArgs) { $appParams = "$appParams $ExtraArgs" }
    & $nssm set $ServiceName AppParameters $appParams             | Out-Null

    & $nssm set $ServiceName AppDirectory $AiAgentDir            | Out-Null
    & $nssm set $ServiceName DisplayName  $DisplayName            | Out-Null
    & $nssm set $ServiceName Description  $ServiceDesc            | Out-Null
    & $nssm set $ServiceName Start        SERVICE_AUTO_START      | Out-Null

    # === Service account (interactive password prompt) ===
    Write-Host "" -ForegroundColor Cyan
    Write-Host "Service account: $ServiceUser" -ForegroundColor Cyan
    Write-Host "Enter the Windows password for this account at the next prompt." -ForegroundColor Cyan
    Write-Host "(Required so the service inherits user session for GPU access.)" -ForegroundColor Cyan
    $cred     = Get-Credential -UserName $ServiceUser -Message "Password for service account $ServiceUser"
    $bstr     = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($cred.Password)
    $plainPwd = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    try {
        & $nssm set $ServiceName ObjectName $ServiceUser $plainPwd | Out-Null
    } finally {
        # Best-effort scrub
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        $plainPwd = $null
    }

    # === Environment ===
    # PYTHONUNBUFFERED=1 -> live stdout/stderr to NSSM logs
    # PYTHONIOENCODING=utf-8 -> Korean log lines render correctly
    & $nssm set $ServiceName AppEnvironmentExtra "PYTHONUNBUFFERED=1" "PYTHONIOENCODING=utf-8" | Out-Null

    # === stdout/stderr capture with rotation (NSSM-side, 5MB) ===
    & $nssm set $ServiceName AppStdout       $StdoutLog | Out-Null
    & $nssm set $ServiceName AppStderr       $StderrLog | Out-Null
    & $nssm set $ServiceName AppRotateFiles  1          | Out-Null
    & $nssm set $ServiceName AppRotateOnline 1          | Out-Null
    & $nssm set $ServiceName AppRotateBytes  5242880    | Out-Null

    # === Restart on failure ===
    # AppThrottle 10000  -> consider crashes within 10s as "throttled"
    # AppRestartDelay 15000 -> wait 15s before restart (lets network/file handles settle)
    & $nssm set $ServiceName AppExit Default Restart | Out-Null
    & $nssm set $ServiceName AppThrottle     10000    | Out-Null
    & $nssm set $ServiceName AppRestartDelay 15000    | Out-Null

    # === Network deps (avoid start-before-network race) ===
    if (-not $SkipNetworkDeps) {
        & $nssm set $ServiceName DependOnService Tcpip Dnscache | Out-Null
    }

    # === Korean path encoding sanity check ===
    Write-Host "" -ForegroundColor Cyan
    Write-Host "Registry sanity check (verify Korean path renders correctly):" -ForegroundColor Cyan
    $dumpLines = & $nssm dump $ServiceName 2>&1
    $appDirLine = $dumpLines | Where-Object { $_ -match "AppDirectory" } | Select-Object -First 1
    Write-Host "  $appDirLine"
    if ($appDirLine -match "\?\?") {
        Write-Warning "Possible mojibake in AppDirectory. Service may fail to find ai_agent/.env."
        Write-Warning "Workaround: use 8.3 short path. Run: (New-Object -ComObject Scripting.FileSystemObject).GetFolder('$AiAgentDir').ShortPath"
        Write-Warning "Then: nssm set $ServiceName AppDirectory <short_path>"
    } else {
        Write-Host "  AppDirectory looks good." -ForegroundColor Green
    }

    Write-Host "" -ForegroundColor Green
    Write-Host "Starting service..." -ForegroundColor Green
    & $nssm start $ServiceName | Out-Null
    Start-Sleep -Seconds 8

    Action-Status
}

# === Action: Uninstall ===

function Action-Uninstall {
    Assert-Admin
    $existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if (-not $existing) {
        Write-Host "Service '$ServiceName' is not installed. Nothing to do." -ForegroundColor Yellow
        return
    }
    $nssm = Resolve-NssmPath
    if ($existing.Status -eq "Running") {
        Write-Host "Stopping..." -ForegroundColor Yellow
        & $nssm stop $ServiceName confirm | Out-Null
        Start-Sleep -Seconds 2
    }
    Write-Host "Removing..." -ForegroundColor Yellow
    & $nssm remove $ServiceName confirm | Out-Null
    Write-Host "Removed: $ServiceName" -ForegroundColor Green
    Write-Host "Logs preserved at: $LogsDir" -ForegroundColor Cyan
}

# === Action: Status ===

function Action-Status {
    $svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if (-not $svc) {
        Write-Host "Service '$ServiceName' is NOT installed." -ForegroundColor Red
        Write-Host "Install via: powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Install" -ForegroundColor Yellow
        return
    }

    Write-Host "=== $ServiceName ==="
    Write-Host ("  Status:    {0}" -f $svc.Status) -ForegroundColor (Format-StatusColor $svc.Status)
    Write-Host ("  StartType: {0}" -f $svc.StartType)

    try {
        $svcWmi = Get-CimInstance Win32_Service -Filter "Name='$ServiceName'"
        Write-Host ("  RunAs:     {0}" -f $svcWmi.StartName)
        Write-Host ("  PID:       {0}" -f $svcWmi.ProcessId)
        if ($svcWmi.ProcessId -gt 0) {
            $proc = Get-Process -Id $svcWmi.ProcessId -ErrorAction SilentlyContinue
            if ($proc) {
                $uptime = (Get-Date) - $proc.StartTime
                Write-Host ("  Uptime:    {0:dd\.hh\:mm\:ss}" -f $uptime)
                Write-Host ("  Memory:    {0:N0} MB" -f ($proc.WorkingSet64 / 1MB))
            }
        }
    } catch {
        Write-Host "  (process detail unavailable: $_)" -ForegroundColor Yellow
    }

    # Health check: Python actually wrote to its app log recently
    Write-Host "  --- log health ---"
    if (Test-Path $PyAppLog) {
        $lw = (Get-Item $PyAppLog).LastWriteTime
        $age = [int]((Get-Date) - $lw).TotalSeconds
        $color = "Green"
        if ($age -gt 300) { $color = "Yellow" }
        if ($age -gt 1800) { $color = "Red" }
        Write-Host ("  ai_agent.log:  {0} ({1}s ago)" -f $lw, $age) -ForegroundColor $color
        if ($age -gt 300 -and $svc.Status -eq "Running") {
            Write-Warning "ai_agent.log silent > 5min while service Running. Python may be crashing pre-_configure_logging. Run -Action Logs."
        }
    } else {
        Write-Host "  ai_agent.log:  (NOT CREATED YET)" -ForegroundColor Red
        if ($svc.Status -eq "Running") {
            Write-Warning "Service is Running but Python never reached _configure_logging(). Check stderr.log via -Action Logs."
        }
    }
    if (Test-Path $StderrLog) {
        $size = (Get-Item $StderrLog).Length
        $color = "Green"
        if ($size -gt 0) { $color = "Yellow" }
        Write-Host ("  stderr.log:    {0:N0} bytes" -f $size) -ForegroundColor $color
    }
}

# === Action: Logs ===

function Action-Logs {
    Write-Host "=== ai_agent.log (last $LogTail lines -- Python structured) ===" -ForegroundColor Cyan
    if (Test-Path $PyAppLog) {
        Get-Content -Tail $LogTail -Path $PyAppLog
    } else {
        Write-Host "(not yet created)" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "=== ai_agent_service.stderr.log (last $LogTail lines -- NSSM raw stderr) ===" -ForegroundColor Cyan
    if (Test-Path $StderrLog) {
        Get-Content -Tail $LogTail -Path $StderrLog
    } else {
        Write-Host "(not yet created)" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "=== ai_agent_service.stdout.log (last 10 lines -- NSSM raw stdout) ===" -ForegroundColor Cyan
    if (Test-Path $StdoutLog) {
        Get-Content -Tail 10 -Path $StdoutLog
    } else {
        Write-Host "(not yet created)" -ForegroundColor Yellow
    }
}

# === Action: Restart / Stop / Start ===

function Action-Restart {
    Assert-Admin
    $nssm = Resolve-NssmPath
    Write-Host "Restarting $ServiceName..." -ForegroundColor Yellow
    & $nssm restart $ServiceName | Out-Null
    Start-Sleep -Seconds 6
    Action-Status
}

function Action-Stop {
    Assert-Admin
    $nssm = Resolve-NssmPath
    & $nssm stop $ServiceName confirm | Out-Null
    Action-Status
}

function Action-Start {
    Assert-Admin
    $nssm = Resolve-NssmPath
    & $nssm start $ServiceName | Out-Null
    Start-Sleep -Seconds 6
    Action-Status
}

# === Dispatch ===

switch ($Action) {
    "Install"   { Action-Install }
    "Uninstall" { Action-Uninstall }
    "Status"    { Action-Status }
    "Logs"      { Action-Logs }
    "Restart"   { Action-Restart }
    "Stop"      { Action-Stop }
    "Start"     { Action-Start }
}
