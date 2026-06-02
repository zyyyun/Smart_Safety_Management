from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MAIN = REPO_ROOT / "ai_agent" / "main.py"
YOLO_DETECTOR = REPO_ROOT / "ai_agent" / "yolo_detector.py"
DEMO_SCRIPT = REPO_ROOT / "scripts" / "demo_rtsp_real_camera.ps1"


def test_continuous_agent_only_loads_fall_when_enabled_and_handles_missing_weights():
    src = MAIN.read_text(encoding="utf-8")

    assert "len(settings.fall_enabled_camera_ids) > 0" in src
    assert "FallDetector eager load skipped" in src
    assert "except FileNotFoundError as e" in src
    assert "continuing without fall detection" in src


def test_watch_script_clears_fall_env_when_fall_cycle_is_disabled():
    src = DEMO_SCRIPT.read_text(encoding="utf-8")

    assert 'if (-not $EnableFall) {' in src
    assert '$env:FALL_ENABLED_CAMERA_IDS = ""' in src
    assert "FALL cycle DISABLED" in src


def test_yolo_detector_logs_effective_targets_instead_of_all_model_classes():
    src = YOLO_DETECTOR.read_text(encoding="utf-8")

    assert "target_classes=%s" in src
    assert "model_class_count=%d" in src
    assert 'YOLOv5_AUTOINSTALL", "false"' in src
    assert "list(self.class_names.values())" not in src
