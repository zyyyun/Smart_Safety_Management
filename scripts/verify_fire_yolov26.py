"""FIRE-ADV-01 yolov26-fire-detection 모델 검출률 종합 측정.

사용자 데이터셋 (화재현상/01.원천데이터/화재현상/) 의 3 시나리오 폴더를
모두 추론해서 운영 지표를 출력한다:
- 불꽃 (fire positive)  → recall on fire
- 연기 (smoke positive) → recall on smoke
- 정상 (negative)       → false positive rate (운영에서 가장 중요)

각 폴더 360장 × 3 폴더 = 1080장. 25ms/image 기준 ~30초.

출력:
- 종합 표 (conf 0.10 / 0.25 / 0.40 × ALL / fire+smoke filter)
- 각 폴더 missed sample 5장씩 (positive miss) + FP 5장씩 (negative miss)
  → scripts/_fire_verify_out/{불꽃,연기,정상}/
"""
from __future__ import annotations

import statistics
import sys
import time
from collections import Counter
from pathlib import Path

import cv2
import numpy as np

WEIGHTS = Path(r"D:\2025_산업안전\산업안전\모델 7종\화재 탐지\yolov26_fire_best.pt")
ROOT = Path(r"D:\2026_산업안전\화재현상\01.원천데이터\화재현상")
OUT_DIR = Path(__file__).parent / "_fire_verify_out"
TARGET_CLASSES = {"fire", "smoke"}  # detector_configs.py 와 동일

# (label, folder_name, positive_class, scenario_type)
SCENARIOS = [
    ("flame",  "불꽃", "fire",   "positive"),
    ("smoke",  "연기", "smoke",  "positive"),
    ("normal", "정상", None,     "negative"),  # 어떤 fire/smoke 검출도 false positive
]

THRESHOLDS = [0.10, 0.25, 0.40]


def main() -> int:
    if not WEIGHTS.exists():
        print(f"[FAIL] weights not found: {WEIGHTS}", file=sys.stderr)
        return 2

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"[+] weights : {WEIGHTS.name}")
    print(f"[+] root    : {ROOT}")

    from ultralytics import YOLO
    t0 = time.time()
    model = YOLO(str(WEIGHTS))
    class_names = {int(k): str(v) for k, v in (model.names or {}).items()}
    print(f"[+] model loaded in {(time.time()-t0)*1000:.0f}ms")
    print(f"[+] class_names: {class_names}")
    print()

    # Per-scenario result dict
    scenario_results: dict[str, dict] = {}

    for slug, folder, positive_class, sc_type in SCENARIOS:
        img_dir = ROOT / folder / next(
            (sub.name for sub in (ROOT / folder).iterdir() if sub.is_dir()),
            ""
        ) / "JPG"
        if not img_dir.exists():
            # fallback: try direct JPG under the first subfolder
            print(f"[!] {folder}: JPG dir not found, skipping")
            continue
        images = sorted(p for p in img_dir.iterdir() if p.suffix.lower() in {".jpg", ".jpeg", ".png"})
        print(f"[+] {folder:6s} ({sc_type:8s}) : {len(images):3d} images @ {img_dir}")

        rows = []
        infer_ms = []
        for p in images:
            img = cv2.imread(str(p))
            if img is None:
                rows.append({"path": p, "boxes": [], "img": None})
                continue
            t1 = time.time()
            res = model.predict(img, conf=0.05, iou=0.45, imgsz=640, verbose=False)
            infer_ms.append((time.time() - t1) * 1000.0)
            boxes = res[0].boxes if res else None
            bx = []
            if boxes is not None and len(boxes) > 0:
                for cls_id, conf, xyxy in zip(
                    boxes.cls.tolist(), boxes.conf.tolist(), boxes.xyxy.tolist()
                ):
                    bx.append({
                        "cls_id": int(cls_id),
                        "label": class_names.get(int(cls_id), str(int(cls_id))),
                        "conf": float(conf),
                        "xyxy": [float(v) for v in xyxy],
                    })
            rows.append({"path": p, "boxes": bx, "img": img})

        scenario_results[slug] = {
            "folder": folder,
            "positive_class": positive_class,
            "type": sc_type,
            "rows": rows,
            "infer_ms": infer_ms,
            "count": len(images),
        }
        print(f"             avg inference {statistics.mean(infer_ms):.0f} ms/image")

    # ---- Aggregate table ----
    print()
    print("=" * 100)
    print("DETECTION RATE TABLE (target filter = fire+smoke, same as production)")
    print("=" * 100)
    print()
    header = f"{'scenario':10s} {'type':9s} {'N':>4s}"
    for t in THRESHOLDS:
        header += f"  conf>={t:.2f}"
    print(header)
    print("-" * len(header))

    for slug, data in scenario_results.items():
        line = f"{slug:10s} {data['type']:9s} {data['count']:4d}"
        for thr in THRESHOLDS:
            img_with_target = 0
            for r in data["rows"]:
                if any(b["conf"] >= thr and b["label"].lower() in TARGET_CLASSES for b in r["boxes"]):
                    img_with_target += 1
            pct = 100.0 * img_with_target / data["count"]
            line += f"  {img_with_target:3d}/{data['count']:3d} ({pct:5.1f}%)"
        # Add label interpretation
        if data["type"] == "positive":
            line += "   ← recall (higher = better)"
        else:
            line += "   ← false positive rate (LOWER = better)"
        print(line)

    print()
    print("=" * 100)
    print("BY-CLASS BREAKDOWN at operational threshold conf>=0.25")
    print("=" * 100)
    for slug, data in scenario_results.items():
        cls_ctr: Counter = Counter()
        confs: list[float] = []
        for r in data["rows"]:
            for b in r["boxes"]:
                if b["conf"] >= 0.25:
                    cls_ctr[b["label"]] += 1
                    confs.append(b["conf"])
        print(f"  {slug:10s} ({data['folder']}, {data['type']}): total_boxes={sum(cls_ctr.values())}")
        if cls_ctr:
            print(f"    class_dist: {dict(cls_ctr.most_common())}")
        if confs:
            print(f"    conf p[min/25/50/75/max] = "
                  f"{min(confs):.3f} / {np.percentile(confs,25):.3f} / "
                  f"{np.percentile(confs,50):.3f} / {np.percentile(confs,75):.3f} / "
                  f"{max(confs):.3f}")

    # ---- Save samples ----
    def annotate(r, out_path: Path) -> None:
        if r["img"] is None:
            return
        img = r["img"].copy()
        for b in r["boxes"]:
            if b["conf"] < 0.05:
                continue
            x1, y1, x2, y2 = (int(v) for v in b["xyxy"])
            color = (0, 0, 255) if b["label"].lower() == "fire" else (
                (200, 200, 0) if b["label"].lower() == "smoke" else (128, 128, 128)
            )
            cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)
            cv2.putText(img, f"{b['label']} {b['conf']:.2f}",
                        (x1, max(y1 - 6, 14)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2)
        cv2.imwrite(str(out_path), img)

    print()
    print(f"[+] saving samples per scenario (conf>=0.25 threshold)")
    for slug, data in scenario_results.items():
        sub = OUT_DIR / slug
        sub.mkdir(parents=True, exist_ok=True)
        # 정렬: 가장 높은 target conf
        def best_target_conf(r) -> float:
            kept = [b["conf"] for b in r["boxes"] if b["label"].lower() in TARGET_CLASSES]
            return max(kept) if kept else 0.0
        rows_sorted = sorted(data["rows"], key=best_target_conf, reverse=True)

        if data["type"] == "positive":
            # Detected (best) + Missed (no target detection at conf>=0.25)
            detected = [r for r in rows_sorted if best_target_conf(r) >= 0.25][:5]
            missed = [r for r in rows_sorted if best_target_conf(r) < 0.25][:5]
            for i, r in enumerate(detected, 1):
                annotate(r, sub / f"detected_{i:02d}_{r['path'].stem}.jpg")
            for i, r in enumerate(missed, 1):
                annotate(r, sub / f"MISSED_{i:02d}_{r['path'].stem}.jpg")
            print(f"  {slug}: detected={len(detected)}, missed={len(missed)}")
        else:
            # negative: any detection at conf>=0.25 is a FP
            fps = [r for r in rows_sorted if best_target_conf(r) >= 0.25][:10]
            for i, r in enumerate(fps, 1):
                annotate(r, sub / f"FP_{i:02d}_{r['path'].stem}.jpg")
            print(f"  {slug}: false_positives_saved={len(fps)}")

    print()
    print("DONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
