"""YOLOv7-w6-pose 기반 쓰러짐 감지 모듈.

2025 레거시(D:\\2025_산업안전\\산업안전\\모델 7종\\쓰러짐 탐지\\) 의 main.py 및
realtime_fall_detection.py 로직을 ai_agent 에 이식.

핵심 구조
---------
1. YOLOv7-w6-pose 로 17-point COCO 키포인트 추정 (1프레임당 1회 forward)
2. rule-based classifier
   - shoulder ↔ hip 세로 길이를 기준(len_factor)으로 삼아
     * shoulder_y > foot_y - len_factor
     * hip_y > foot_y - len_factor/2
     * shoulder_y > hip_y - len_factor/2
     같은 조건, 또는 bbox height - width < 0 (누워 있음) 이면 FALL

체크포인트 호환성
-----------------
YOLOv7 .pt 파일은 피클 경로에 `models.yolo.Model` 등을 내장하므로
반드시 sys.path 에 ai_agent/yolov7_fork/ 를 prepend 한 상태에서 torch.load 해야 한다.
torch >= 2.4 에서는 weights_only=False 를 명시해 FutureWarning 회피.
"""

from __future__ import annotations

import logging
import math
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
import torch
from torchvision import transforms


_YOLOV7_FORK_DIR = Path(__file__).resolve().parent / "yolov7_fork"
if str(_YOLOV7_FORK_DIR) not in sys.path:
    sys.path.insert(0, str(_YOLOV7_FORK_DIR))

# yolov7_fork/utils 아래 upstream import — sys.path prepend 이후에 해야 함.
from utils.datasets import letterbox  # noqa: E402
from utils.general import non_max_suppression_kpt  # noqa: E402
from utils.plots import output_to_keypoint  # noqa: E402


log = logging.getLogger(__name__)


@dataclass
class FallResult:
    is_fall: bool
    confidence: float | None
    bbox: tuple[float, float, float, float] | None  # xmin, ymin, xmax, ymax (letterbox 좌표계)
    pose_count: int
    inference_ms: float

    def to_dict(self) -> dict:
        return {
            "is_fall": self.is_fall,
            "confidence": self.confidence,
            "bbox": list(self.bbox) if self.bbox else None,
            "pose_count": self.pose_count,
            "inference_ms": self.inference_ms,
        }


class FallDetector:
    """YOLOv7-w6-pose 모델 래퍼.

    싱글톤처럼 한 프로세스당 1회 로드해 여러 프레임에 재사용.
    """

    # rule 의 fallback confidence — rule-based 는 이진 결과라 대략 값을 부여한다.
    # 운영자 UI 에 % 로 표시되므로 0.5~1.0 구간에 고정.
    _BASE_CONFIDENCE = 0.85

    def __init__(
        self,
        weights_path: str | Path,
        device: str = "auto",
        conf_thres: float = 0.25,
        iou_thres: float = 0.65,
        img_size: int = 960,
        stride: int = 64,
    ) -> None:
        self.weights_path = Path(weights_path)
        if not self.weights_path.exists():
            raise FileNotFoundError(
                f"FallDetector: 가중치 파일이 없습니다 -> {self.weights_path}. "
                "FALL_MODEL_WEIGHTS 환경변수 확인."
            )

        self.device = self._resolve_device(device)
        self.conf_thres = conf_thres
        self.iou_thres = iou_thres
        self.img_size = img_size
        self.stride = stride
        self._fp16 = self.device.type == "cuda"

        t0 = time.time()
        # torch.load 는 피클 unpickling 에서 models.yolo 등의 경로를 resolve 하므로
        # sys.path 설정이 이 시점에 완료되어 있어야 한다.
        checkpoint = torch.load(
            str(self.weights_path),
            map_location=self.device,
            weights_only=False,
        )
        model = checkpoint["model"]
        model.float().eval()
        if self._fp16:
            model = model.half().to(self.device)
        else:
            model = model.to(self.device)
        self.model = model
        log.info(
            "FallDetector loaded weights=%s device=%s fp16=%s (%.1fs)",
            self.weights_path.name,
            self.device,
            self._fp16,
            time.time() - t0,
        )

    @staticmethod
    def _resolve_device(name: str) -> torch.device:
        name = (name or "auto").lower()
        if name == "auto":
            return torch.device("cuda:0" if torch.cuda.is_available() else "cpu")
        if name.startswith("cuda") and not torch.cuda.is_available():
            log.warning("CUDA 요청됐으나 사용 불가 — CPU 로 폴백")
            return torch.device("cpu")
        return torch.device(name)

    # ────────────────────────────────────────────
    # public API
    # ────────────────────────────────────────────
    def detect_fall(self, image_bgr: np.ndarray) -> FallResult:
        """단일 BGR 프레임에 대해 쓰러짐 여부 판정."""
        if image_bgr is None or image_bgr.size == 0:
            raise ValueError("detect_fall: 빈 이미지")

        t0 = time.time()
        poses = self._run_pose(image_bgr)
        is_fall, bbox, _reason_idx = self._classify(poses) if poses.size else (False, None, None)

        return FallResult(
            is_fall=is_fall,
            confidence=self._BASE_CONFIDENCE if is_fall else None,
            bbox=bbox,
            pose_count=int(poses.shape[0]) if poses.size else 0,
            inference_ms=(time.time() - t0) * 1000.0,
        )

    # ────────────────────────────────────────────
    # internal
    # ────────────────────────────────────────────
    def _run_pose(self, image_bgr: np.ndarray) -> np.ndarray:
        """단일 프레임 → output_to_keypoint 결과 배열 (shape (N, 58))."""
        # letterbox + ToTensor (레거시 main.py:87-100 과 동일)
        image_lb = letterbox(image_bgr, self.img_size, stride=self.stride, auto=True)[0]
        tensor = transforms.ToTensor()(image_lb)  # 0-1 float tensor, CHW
        tensor = torch.tensor(np.array([tensor.numpy()]))
        if self._fp16:
            tensor = tensor.half().to(self.device)
        else:
            tensor = tensor.float().to(self.device)

        with torch.no_grad():
            output, _ = self.model(tensor)

        output = non_max_suppression_kpt(
            output,
            self.conf_thres,
            self.iou_thres,
            nc=self.model.yaml["nc"],
            nkpt=self.model.yaml["nkpt"],
            kpt_label=True,
        )
        with torch.no_grad():
            poses = output_to_keypoint(output)  # numpy.ndarray
        return poses

    @staticmethod
    def _classify(
        poses: np.ndarray,
    ) -> tuple[bool, Optional[tuple[float, float, float, float]], Optional[int]]:
        """레거시 main.py::fall_detection 규칙 포팅.

        pose layout (`output_to_keypoint` 결과, 각 row length=58):
            [img_idx, class, cx, cy, w, h, conf, kpt0_x, kpt0_y, kpt0_conf, ...]
            - pose[2..5]: bbox cx, cy, w, h
            - pose[22]=l_shoulder_x, pose[23]=l_shoulder_y
            - pose[26]=r_shoulder_y
            - pose[40]=l_hip_x, pose[41]=l_hip_y
            - pose[44]=r_hip_y
            - pose[53]=l_ankle_y
            - pose[56]=r_ankle_y
        """
        for idx, pose in enumerate(poses):
            xmin = pose[2] - pose[4] / 2
            ymin = pose[3] - pose[5] / 2
            xmax = pose[2] + pose[4] / 2
            ymax = pose[3] + pose[5] / 2

            left_shoulder_y = pose[23]
            left_shoulder_x = pose[22]
            right_shoulder_y = pose[26]

            left_hip_y = pose[41]
            left_hip_x = pose[40]
            right_hip_y = pose[44]

            left_ankle_y = pose[53]
            right_ankle_y = pose[56]

            len_factor = math.sqrt(
                (left_shoulder_y - left_hip_y) ** 2
                + (left_shoulder_x - left_hip_x) ** 2
            )

            dx = int(xmax) - int(xmin)
            dy = int(ymax) - int(ymin)
            lying_down = (dy - dx) < 0

            cond_left = (
                left_shoulder_y > left_ankle_y - len_factor
                and left_hip_y > left_ankle_y - (len_factor / 2)
                and left_shoulder_y > left_hip_y - (len_factor / 2)
            )
            cond_right = (
                right_shoulder_y > right_ankle_y - len_factor
                and right_hip_y > right_ankle_y - (len_factor / 2)
                and right_shoulder_y > right_hip_y - (len_factor / 2)
            )

            if cond_left or cond_right or lying_down:
                return True, (float(xmin), float(ymin), float(xmax), float(ymax)), idx
        return False, None, None
