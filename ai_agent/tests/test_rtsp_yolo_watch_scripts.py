from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEMO_SCRIPT = REPO_ROOT / "scripts" / "demo_rtsp_real_camera.ps1"
TASK_SCRIPT = REPO_ROOT / "scripts" / "install_rtsp_yolo_watch_task.ps1"
SERVICE_SCRIPT = REPO_ROOT / "scripts" / "ai_agent_service.ps1"


def test_demo_script_has_watch_mode_contract():
    src = DEMO_SCRIPT.read_text(encoding="utf-8")

    assert "[switch]$Watch" in src
    assert "[int]$WatchRetrySeconds" in src
    assert "[int]$WatchRestartDelaySeconds" in src
    assert "function Start-WatchMode" in src
    assert "function Test-TcpReachable" in src
    assert "function Write-WatchLog" in src
    assert "if ($Watch) {" in src
    assert "Start-WatchMode" in src


def test_watch_mode_keeps_console_process_alive_and_restarts_yolo():
    src = DEMO_SCRIPT.read_text(encoding="utf-8")

    assert "& $python -u $mainPy" in src
    assert "while ($true)" in src
    assert "WatchRetrySeconds" in src
    assert "WatchRestartDelaySeconds" in src
    assert "Start-Sleep -Seconds $WatchRetrySeconds" in src
    assert "Start-Sleep -Seconds $WatchRestartDelaySeconds" in src


def test_watch_log_write_is_resilient_to_missing_log_directory():
    src = DEMO_SCRIPT.read_text(encoding="utf-8")

    assert "function Ensure-WatchLogDirectory" in src

    write_start = src.index("function Write-WatchLog")
    write_end = src.index("function Test-TcpReachable")
    write_src = src[write_start:write_end]

    assert "Ensure-WatchLogDirectory" in write_src
    assert "Add-Content -LiteralPath $WatchLogPath" in write_src
    assert "catch" in write_src
    assert "Write-Warning" in write_src

    watch_start = src.index("function Start-WatchMode")
    watch_end = src.index("if ($Watch) {")
    watch_src = src[watch_start:watch_end]

    assert "Ensure-WatchLogDirectory" in watch_src
    assert "Set-Content -LiteralPath $lockPath" in watch_src
    assert "$lockEnabled" in watch_src
    assert "continuing without duplicate guard" in watch_src


def test_task_installer_registers_visible_logon_task():
    src = TASK_SCRIPT.read_text(encoding="utf-8")

    assert "Register-ScheduledTask" in src
    assert "New-ScheduledTaskTrigger -AtLogOn" in src
    assert "New-ScheduledTaskPrincipal" in src
    assert "LogonType Interactive" in src
    assert "-NoExit" in src
    assert "-WindowStyle Normal" in src
    assert "-Watch" in src
    assert "demo_rtsp_real_camera.ps1" in src


def test_ai_agent_service_uninstall_does_not_require_nssm_binary():
    src = SERVICE_SCRIPT.read_text(encoding="utf-8")
    uninstall_start = src.index("function Action-Uninstall")
    uninstall_end = src.index("# === Action: Status ===")
    uninstall_src = src[uninstall_start:uninstall_end]

    assert "sc.exe delete $ServiceName" in uninstall_src
    assert "Stop-Service -Name $ServiceName" in uninstall_src
    assert "Resolve-NssmPath" not in uninstall_src
    assert "& $nssm remove" not in uninstall_src
