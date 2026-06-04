import importlib.util
import json
import sys
import types
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = REPO_ROOT / "scripts" / "export_mobile_fire_tflite.py"
LABELS_PATH = REPO_ROOT / "app" / "src" / "main" / "assets" / "mobile_fire_labels.txt"


def load_export_module():
    spec = importlib.util.spec_from_file_location("mobile_fire_export_under_test", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def test_export_fire_model_writes_android_assets(tmp_path, monkeypatch):
    module = load_export_module()
    weights = tmp_path / "fire.pt"
    exported = tmp_path / "exported.tflite"
    weights.write_bytes(b"weights")
    exported.write_bytes(b"tflite")
    calls = []

    class FakeYOLO:
        def __init__(self, model_path):
            calls.append(("init", model_path))

        def export(self, **kwargs):
            calls.append(("export", kwargs))
            return str(exported)

    fake_ultralytics = types.ModuleType("ultralytics")
    fake_ultralytics.YOLO = FakeYOLO
    monkeypatch.setitem(sys.modules, "ultralytics", fake_ultralytics)
    monkeypatch.setattr(module, "ASSETS_DIR", tmp_path)
    monkeypatch.setattr(module, "OUTPUT_TFLITE", tmp_path / "mobile_fire.tflite")
    monkeypatch.setattr(module, "OUTPUT_CONTRACT", tmp_path / "mobile_fire_model_contract.json")

    result = module.export_fire_model(weights)

    assert result == tmp_path / "mobile_fire.tflite"
    assert result.read_bytes() == b"tflite"
    assert not (tmp_path / "mobile_fire.tflite.tmp").exists()
    assert calls == [
        ("init", str(weights)),
        (
            "export",
            {
                "format": "tflite",
                "imgsz": 640,
                "nms": True,
                "int8": False,
                "half": False,
                "batch": 1,
                "device": "cpu",
            },
        ),
    ]

    contract = json.loads((tmp_path / "mobile_fire_model_contract.json").read_text(encoding="utf-8"))
    assert contract["model"] == "mobile_fire.tflite"
    assert contract["labels"] == ["fire", "smoke"]
    assert contract["input_width"] == 640
    assert contract["output"] == "ultralytics_nms"


def test_resolve_weights_prefers_cli_then_env_and_keeps_local_fallback(monkeypatch, tmp_path):
    module = load_export_module()
    env_weights = tmp_path / "env.pt"
    cli_weights = tmp_path / "cli.pt"

    monkeypatch.setenv(module.FIRE_WEIGHTS_ENV, str(env_weights))
    assert module.resolve_weights() == env_weights
    assert module.resolve_weights(cli_weights) == cli_weights

    monkeypatch.delenv(module.FIRE_WEIGHTS_ENV)
    assert module.resolve_weights() == module.DEFAULT_WEIGHTS
    assert str(module.DEFAULT_WEIGHTS) == r"D:\2025_산업안전\산업안전\모델 7종\화재 탐지\yolov26_fire_best.pt"


def test_missing_exported_file_raises(tmp_path, monkeypatch):
    module = load_export_module()
    weights = tmp_path / "fire.pt"
    weights.write_bytes(b"weights")

    class FakeYOLO:
        def __init__(self, model_path):
            self.model_path = model_path

        def export(self, **kwargs):
            return str(tmp_path / "missing.tflite")

    fake_ultralytics = types.ModuleType("ultralytics")
    fake_ultralytics.YOLO = FakeYOLO
    monkeypatch.setitem(sys.modules, "ultralytics", fake_ultralytics)

    try:
        module.export_fire_model(weights)
    except FileNotFoundError as exc:
        assert "exported TFLite file not found" in str(exc)
    else:
        raise AssertionError("expected FileNotFoundError for missing exported file")


def test_labels_file_contains_fire_and_smoke():
    labels = LABELS_PATH.read_text(encoding="utf-8").splitlines()
    assert labels == ["fire", "smoke"]
