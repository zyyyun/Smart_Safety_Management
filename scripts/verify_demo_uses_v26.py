"""Verify demo_rtsp_real_camera.ps1 fire detection chain really loads
yolov26 weights through the production code path.

Chain:
  ps1 -> main.py --once-detect -> _load_general_detectors(settings)
      -> DETECTOR_CONFIGS['fire'] -> GenericYoloDetector(weights, framework, ...)
        -> ultralytics.YOLO(weights)

This script imports the exact same modules and instantiates the fire detector
the same way main.py does, then runs one real inference.
"""
from __future__ import annotations

import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "ai_agent"))

from detector_configs import DETECTOR_CONFIGS
from yolo_detector import GenericYoloDetector

print("=" * 80)
print("Step 1 - DETECTOR_CONFIGS['fire'] (read from detector_configs.py)")
print("=" * 80)
fire_cfg = DETECTOR_CONFIGS["fire"]
for k, v in fire_cfg.items():
    print(f"  {k:18s} : {v!r}")

print()
print("=" * 80)
print("Step 2 - weights file existence + size")
print("=" * 80)
weights_path = Path(fire_cfg["weights"])
print(f"  path   : {weights_path}")
print(f"  exists : {weights_path.exists()}")
if weights_path.exists():
    size_mb = weights_path.stat().st_size / (1024 * 1024)
    print(f"  size   : {size_mb:.2f} MB")

print()
print("=" * 80)
print("Step 3 - GenericYoloDetector instantiate (same args as main.py)")
print("=" * 80)
import logging
logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
det = GenericYoloDetector(
    name="fire",
    weights_path=fire_cfg["weights"],
    conf_thres=fire_cfg.get("conf_thres", 0.25),
    iou_thres=fire_cfg.get("iou_thres", 0.45),
    img_size=fire_cfg.get("img_size", 640),
    target_classes=fire_cfg.get("target_classes"),
    device="auto",
    framework=fire_cfg.get("framework", "yolov8"),
)
print(f"  detector.name         : {det.name}")
print(f"  detector.framework    : {det.framework}")
print(f"  detector.weights_path : {det.weights_path}")
print(f"  detector.conf_thres   : {det.conf_thres}")
print(f"  detector.class_names  : {det.class_names}")
print(f"  detector.targets      : {det.targets}")
print(f"  ultralytics model     : {type(det.model).__module__}.{type(det.model).__name__}")
ckpt = getattr(det.model, "ckpt_path", None) or getattr(det.model, "pt_path", None)
print(f"  ultralytics ckpt_path : {ckpt}")

print()
print("=" * 80)
print("Step 4 - real fire image inference (production chain)")
print("=" * 80)
import cv2
test_img = Path(r"D:\2026_산업안전\화재현상\01.원천데이터\화재현상\불꽃\0087\JPG\0087_FL_FWW_00144.jpg")
img = cv2.imread(str(test_img))
print(f"  test image : {test_img.name}")
print(f"  shape      : {img.shape}")

result = det.detect(img)
print(f"  is_detected   : {result.is_detected}")
print(f"  confidence    : {result.confidence}")
print(f"  label         : {result.label}")
print(f"  bbox          : {result.bbox}")
print(f"  inference_ms  : {result.inference_ms:.1f}")

all_results = det.detect_all(img)
print(f"  detect_all count : {len(all_results)}")
for i, r in enumerate(all_results[:5], 1):
    print(f"    {i}. {r.label} conf={r.confidence:.3f} bbox=({r.bbox[0]:.0f},{r.bbox[1]:.0f},{r.bbox[2]:.0f},{r.bbox[3]:.0f})")

print()
print("=" * 80)
print("VERDICT")
print("=" * 80)
v26 = "yolov26" in fire_cfg["weights"].lower()
fw_ok = fire_cfg["framework"] == "yolov8"
classes_ok = set(det.class_names.values()) == {"fire", "other", "smoke"}
detect_ok = result.is_detected and result.label == "fire" and result.confidence >= 0.25

checks = [
    ("weights path contains 'yolov26'", v26),
    ("framework == 'yolov8' (ultralytics)", fw_ok),
    ("class_names == {fire, other, smoke}", classes_ok),
    ("test image fire detected at conf >= 0.25", detect_ok),
]
for label, ok in checks:
    print(f"  [{'OK' if ok else 'FAIL'}] {label}")

all_ok = all(ok for _, ok in checks)
print()
print(f"  ===> demo_rtsp chain uses YOLOv26: {'YES' if all_ok else 'NO - see FAILs above'}")
sys.exit(0 if all_ok else 1)
