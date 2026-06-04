from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Optional, Sequence, Union

REPO_ROOT = Path(__file__).resolve().parents[1]
FIRE_WEIGHTS_ENV = "MOBILE_FIRE_WEIGHTS"
DEFAULT_WEIGHTS = Path(
    r"D:\2025_산업안전\산업안전\모델 7종\화재 탐지\yolov26_fire_best.pt"
)
ASSETS_DIR = REPO_ROOT / "app/src/main/assets"
OUTPUT_TFLITE = REPO_ROOT / "app/src/main/assets/mobile_fire.tflite"
OUTPUT_CONTRACT = ASSETS_DIR / "mobile_fire_model_contract.json"


def resolve_weights(weights: Optional[Union[str, Path]] = None) -> Path:
    if weights:
        return Path(weights).expanduser()
    env_weights = os.environ.get(FIRE_WEIGHTS_ENV)
    if env_weights:
        return Path(env_weights).expanduser()
    return DEFAULT_WEIGHTS


def atomic_write_bytes(path: Path, content: bytes) -> None:
    temp_path = path.with_name(f"{path.name}.tmp")
    temp_path.write_bytes(content)
    temp_path.replace(path)


def atomic_write_text(path: Path, content: str) -> None:
    temp_path = path.with_name(f"{path.name}.tmp")
    temp_path.write_text(content, encoding="utf-8")
    temp_path.replace(path)


def export_fire_model(weights: Optional[Union[str, Path]] = None) -> Path:
    weights_path = resolve_weights(weights)
    if not weights_path.exists():
        raise FileNotFoundError(f"fire model weights not found: {weights_path}")

    from ultralytics import YOLO

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    model = YOLO(str(weights_path))
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
    if not exported.exists():
        raise FileNotFoundError(f"exported TFLite file not found: {exported}")

    atomic_write_bytes(OUTPUT_TFLITE, exported.read_bytes())
    atomic_write_text(
        OUTPUT_CONTRACT,
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
    )
    return OUTPUT_TFLITE


def main(argv: Optional[Sequence[str]] = None) -> None:
    parser = argparse.ArgumentParser(description="Export the mobile fire detector to Android assets.")
    parser.add_argument(
        "--weights",
        help=f"Path to YOLO fire weights. Defaults to ${FIRE_WEIGHTS_ENV}, then {DEFAULT_WEIGHTS}.",
    )
    args = parser.parse_args(argv)
    path = export_fire_model(args.weights)
    print(f"exported {path}")


if __name__ == "__main__":
    main()
