# =============================================================================
# Register visible RTSP YOLO watch task
# =============================================================================
#
# Default install:
#   powershell -ExecutionPolicy Bypass -File scripts\install_rtsp_yolo_watch_task.ps1 -Action Install
#
# The task runs only when the current user is logged on, so Windows opens a
# visible PowerShell window. That keeps GPU/user-session behavior closer to
# manual demo runs and lets the operator inspect live logs.

[CmdletBinding()]
param(
    [ValidateSet("Install", "Uninstall", "Status", "RunNow")]
    [string]$Action = "Install",

    [string]$TaskName = "SmartSafetyRtspYoloWatch",
    [int]$DelaySeconds = 30,
    [string]$RtspUrl = "rtsp://192.168.0.13/live",
    [int]$WatchRetrySeconds = 15,
    [int]$WatchRestartDelaySeconds = 15,
    [string]$DetectorsEnabled = "",
    [switch]$FireOnly,
    [switch]$EnableFall,
    [string]$PowerShellPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$DemoScript = Join-Path $RepoRoot "scripts\demo_rtsp_real_camera.ps1"
$TaskUser = "$env:USERDOMAIN\$env:USERNAME"

function Get-WatchTaskArgument {
    $parts = @(
        "-NoExit",
        "-ExecutionPolicy Bypass",
        "-WindowStyle Normal",
        "-File `"$DemoScript`"",
        "-Watch",
        "-RtspUrl `"$RtspUrl`"",
        "-WatchRetrySeconds $WatchRetrySeconds",
        "-WatchRestartDelaySeconds $WatchRestartDelaySeconds"
    )
    if ($DetectorsEnabled) {
        $parts += "-DetectorsEnabled `"$DetectorsEnabled`""
    }
    if ($FireOnly) {
        $parts += "-FireOnly"
    }
    if ($EnableFall) {
        $parts += "-EnableFall"
    }
    return ($parts -join " ")
}

function Install-WatchTask {
    if (-not (Test-Path $PowerShellPath)) {
        throw "PowerShell executable not found: $PowerShellPath"
    }
    if (-not (Test-Path $DemoScript)) {
        throw "demo script not found: $DemoScript"
    }

    $action = New-ScheduledTaskAction `
        -Execute $PowerShellPath `
        -Argument (Get-WatchTaskArgument) `
        -WorkingDirectory $RepoRoot

    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $TaskUser
    $trigger.Delay = "PT${DelaySeconds}S"
    $principal = New-ScheduledTaskPrincipal -UserId $TaskUser -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet `
        -MultipleInstances IgnoreNew `
        -RestartCount 3 `
        -RestartInterval (New-TimeSpan -Minutes 1) `
        -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries

    Register-ScheduledTask `
        -TaskName $TaskName `
        -Action $action `
        -Trigger $trigger `
        -Principal $principal `
        -Settings $settings `
        -Description "Visible PowerShell RTSP YOLO watch. Waits for camera pairing and runs ai_agent continuously." `
        -Force | Out-Null

    Write-Host "Installed scheduled task: $TaskName" -ForegroundColor Green
    Write-Host "User       : $TaskUser"
    Write-Host "Trigger    : at logon + ${DelaySeconds}s delay"
    Write-Host "Visibility : PowerShell window shown (-NoExit -WindowStyle Normal)"
    Write-Host "Command    : $PowerShellPath $(Get-WatchTaskArgument)"
}

function Uninstall-WatchTask {
    $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if (-not $task) {
        Write-Host "Scheduled task not found: $TaskName" -ForegroundColor Yellow
        return
    }
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    Write-Host "Removed scheduled task: $TaskName" -ForegroundColor Green
}

function Show-WatchTaskStatus {
    $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if (-not $task) {
        Write-Host "Scheduled task not installed: $TaskName" -ForegroundColor Yellow
        return
    }
    $info = Get-ScheduledTaskInfo -TaskName $TaskName
    Write-Host "TaskName      : $TaskName"
    Write-Host "State         : $($task.State)"
    Write-Host "LastRunTime   : $($info.LastRunTime)"
    Write-Host "LastTaskResult: $($info.LastTaskResult)"
    Write-Host "NextRunTime   : $($info.NextRunTime)"
    Write-Host "Action        : $($task.Actions[0].Execute) $($task.Actions[0].Arguments)"
}

function Run-WatchTaskNow {
    $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if (-not $task) {
        throw "Scheduled task not installed: $TaskName"
    }
    Start-ScheduledTask -TaskName $TaskName
    Write-Host "Started scheduled task: $TaskName" -ForegroundColor Green
}

switch ($Action) {
    "Install" { Install-WatchTask }
    "Uninstall" { Uninstall-WatchTask }
    "Status" { Show-WatchTaskStatus }
    "RunNow" { Run-WatchTaskNow }
}
