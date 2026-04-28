"""범용 YOLO 검출기 — ultralytics 패키지 기반.

YOLOv5 / YOLOv8 가중치 모두 지원 (ultralytics 8.x 가 두 포맷 자동 감지).
LP-2 1단계 쓰러짐 (YOLOv7-pose) 은 yolov7_fork + fall_detector.py 의 특수 처리를
유지하고, 본 모듈은 일반 객체 검출 4종 (화재·안전모·지게차·사람) 을 통합 처리한다.

설계 의도:
- 단일 클래스 GenericYoloDetector + DETECTOR_CONFIGS dict 4 항목으로 코드 중복 제거.
- detect() 호출 한 번이 단일 frame BGR ndarray 를 받아 DetectResult 1건 반환.
  여러 객체가 검출되어도 confidence 가 가장 높은 것을 대표로 (1 frame = 1 event 정책).
- 룰은 단순 conf threshold + (선택적) target class 화이트리스트.
  복잡한 룰 (5프레임 연속, bbox 겹침 등) 은 후속 세션 범위.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np


log = logging.getLogger(__name__)


@dataclass
class DetectResult:
    """검출 결과 단건.

    is_detected=False 인 경우 confidence/bbox/label 은 None.
    """

    is_detected: bool
    confidence: float | None = None
    bbox: tuple[float, float, float, float] | None = None  # x1, y1, x2, y2
    label: str | None = None
    inference_ms: float = 0.0


class GenericYoloDetector:
    """ultralytics 기반 범용 YOLO 검출기 싱글톤 래퍼.

    init 시 가중치를 즉시 로드 (eager). 가중치 경로 오류는 즉시 surface.
    """

    def __init__(
        self,
        name: str,
        weights_path: str,
        conf_thres: float = 0.25,
        iou_thres: float = 0.45,
        img_size: int = 640,
        target_classes: Iterable[str] | None = None,
        device: str = "auto",
        framework: str = "yolov8",  # "yolov5" | "yolov8"
    ) -> None:
        # YOLOv5 가중치는 ultralytics 8.x 와 forwards-incompatible →
        #   yolov5 : torch.hub.load('ultralytics/yolov5', 'custom', ...)
        #   yolov8 : ultralytics.YOLO(...)
        weights = Path(weights_path)
        # yolov8 자동 다운로드 (예: 'yolov8x.pt') 는 파일 존재 검증 스킵
        is_local_path = ("/" in weights_path) or ("\\" in weights_path) or weights_path.endswith(".pt") and len(weights_path) > 20
        if is_local_path and not weights_path.startswith("yolov") and not weights.exists():
            raise FileNotFoundError(
                f"YOLO 가중치 파일이 없습니다: {weights_path}"
            )

        self.name = name
        self.framework = framework
        self.weights_path = str(weights)
        self.conf_thres = conf_thres
        self.iou_thres = iou_thres
        self.img_size = img_size
        self.targets: set[str] | None = (
            {c.lower() for c in target_classes} if target_classes else None
        )
        self._device = device if device != "auto" else None

        t0 = time.time()
        if framework == "yolov5":
            import platform
            import pathlib
            import torch
            # Linux 에서 학습된 가중치 pickle 안에 PosixPath 가 박혀있을 수 있음.
            # Windows 의 pathlib 가 PosixPath 인스턴스화를 거부하므로 임시 alias.
            if platform.system() == "Windows":
                pathlib.PosixPath = pathlib.WindowsPath  # type: ignore[misc]
            self.model = torch.hub.load(
                "ultralytics/yolov5", "custom", path=str(weights), _verbose=False
            )
            self.model.conf = conf_thres
            self.model.iou = iou_thres
            names = self.model.names
            if isinstance(names, dict):
                self.class_names = {int(k): str(v) for k, v in names.items()}
            else:
                self.class_names = {i: str(n) for i, n in enumerate(names)}
        elif framework == "yolov8":
            from ultralytics import YOLO
            self.model = YOLO(str(weights))
            self.class_names = {int(k): str(v) for k, v in (self.model.names or {}).items()}
        else:
            raise ValueError(f"unknown framework: {framework}")

        load_ms = (time.time() - t0) * 1000.0
        log.info(
            "GenericYoloDetector loaded name=%s framework=%s weights=%s classes=%s (%.0fms)",
            name,
            framework,
            weights.name if is_local_path else weights_path,
            list(self.class_names.values()),
            load_ms,
        )

        if self.targets is not None:
            available = {n.lower() for n in self.class_names.values()}
            missing = self.targets - available
            if missing:
                log.warning(
                    "[%s] target_classes %s 가 모델 라벨 %s 에 없음. "
                    "전체 클래스 사용으로 폴백 (false positive 가능).",
                    name,
                    sorted(missing),
                    sorted(available),
                )

    def detect(self, image_bgr: np.ndarray) -> DetectResult:
        """단일 frame 추론. 가장 높은 confidence 검출 1건을 반환."""
        if image_bgr is None or image_bgr.size == 0:
            return DetectResult(is_detected=False)

        t0 = time.time()
        if self.framework == "yolov5":
            # torch.hub yolov5 model 은 BGR 입력 OK, BGR→RGB 알아서 처리
            results = self.model(image_bgr, size=self.img_size)
            # results.xyxy[0]: tensor [N, 6] = (x1, y1, x2, y2, conf, cls)
            preds = results.xyxy[0].cpu().numpy() if hasattr(results.xyxy[0], "cpu") else results.xyxy[0]
            cls_ids = [int(row[5]) for row in preds]
            confs = [float(row[4]) for row in preds]
            xyxys = [[float(row[0]), float(row[1]), float(row[2]), float(row[3])] for row in preds]
        else:  # yolov8
            predict_kwargs = dict(
                conf=self.conf_thres,
                iou=self.iou_thres,
                imgsz=self.img_size,
                verbose=False,
            )
            if self._device:
                predict_kwargs["device"] = self._device
            results = self.model.predict(image_bgr, **predict_kwargs)
            if not results:
                return DetectResult(is_detected=False, inference_ms=(time.time()-t0)*1000)
            boxes = results[0].boxes
            if boxes is None or len(boxes) == 0:
                return DetectResult(is_detected=False, inference_ms=(time.time()-t0)*1000)
            cls_ids = boxes.cls.tolist() if hasattr(boxes.cls, "tolist") else list(boxes.cls)
            confs = boxes.conf.tolist() if hasattr(boxes.conf, "tolist") else list(boxes.conf)
            xyxys = (
                boxes.xyxy.tolist() if hasattr(boxes.xyxy, "tolist") else [list(b) for b in boxes.xyxy]
            )
        inference_ms = (time.time() - t0) * 1000.0

        best: tuple[float, int, list[float], str] | None = None
        for cls_id, conf, xyxy in zip(cls_ids, confs, xyxys):
            label = self.class_names.get(int(cls_id), str(int(cls_id)))
            if self.targets is not None and label.lower() not in self.targets:
                continue
            if best is None or conf > best[0]:
                best = (float(conf), int(cls_id), list(xyxy), label)

        if best is None:
            return DetectResult(is_detected=False, inference_ms=inference_ms)

        conf, _cls_id, xyxy, label = best
        return DetectResult(
            is_detected=True,
            confidence=conf,
            bbox=(float(xyxy[0]), float(xyxy[1]), float(xyxy[2]), float(xyxy[3])),
            label=label,
            inference_ms=inference_ms,
        )
