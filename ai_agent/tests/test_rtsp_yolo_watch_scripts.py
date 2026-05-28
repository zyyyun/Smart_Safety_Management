from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEMO_SCRIPT = REPO_ROOT / "scripts" / "demo_rtsp_real_camera.ps1"
TASK_SCRIPT = REPO_ROOT / "scripts" / "install_rtsp_yolo_watch_task.ps1"


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
