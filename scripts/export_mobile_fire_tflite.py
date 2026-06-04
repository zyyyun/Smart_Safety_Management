from __future__ import annotations

import json
from pathlib import Path

from ultralytics import YOLO

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WEIGHTS = Path(
    r"D:\2025_산업안전\산업안전\모델 7종\화재 탐지\yolov26_fire_best.pt"
)
ASSETS_DIR = REPO_ROOT / "app/src/main/assets"
OUTPUT_TFLITE = REPO_ROOT / "app/src/main/assets/mobile_fire.tflite"
OUTPUT_CONTRACT = ASSETS_DIR / "mobile_fire_model_contract.json"


def export_fire_model(weights: Path = DEFAULT_WEIGHTS) -> Path:
    if not weights.exists():
        raise FileNotFoundError(f"fire model weights not found: {weights}")
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    model = YOLO(str(weights))
    exported = Path(
        model.export(
            format="tflite",
            imgsz=640,
            nms=True,
            int8=False,
            half=False,
            batch=1,
            device="cpu",
        )
    )
    OUTPUT_TFLITE.write_bytes(exported.read_bytes())
    OUTPUT_CONTRACT.write_text(
        json.dumps(
            {
                "model": "mobile_fire.tflite",
                "labels": ["fire", "smoke"],
                "input_width": 640,
                "input_height": 640,
                "input_channels": 3,
                "input_dtype": "float32",
                "input_normalization": "0_1",
                "output": "ultralytics_nms",
                "output_shape": "[1, max_detections, 6]",
                "box_format": "xywh_normalized",
                "row_format": ["x_center", "y_center", "width", "height", "score", "class_id"],
                "score_threshold": 0.50,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return OUTPUT_TFLITE


if __name__ == "__main__":
    path = export_fire_model()
    print(f"exported {path}")
