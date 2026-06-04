from pathlib import Path


def test_export_script_points_to_android_assets():
    text = Path("scripts/export_mobile_fire_tflite.py").read_text(encoding="utf-8")
    assert 'app/src/main/assets/mobile_fire.tflite' in text
    assert 'mobile_fire_model_contract.json' in text
    assert 'nms=True' in text
    assert 'imgsz=640' in text


def test_labels_file_contains_fire_and_smoke():
    labels = Path("app/src/main/assets/mobile_fire_labels.txt").read_text(encoding="utf-8").splitlines()
    assert labels == ["fire", "smoke"]
