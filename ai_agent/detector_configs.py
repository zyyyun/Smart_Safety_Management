"""LP-2 확장 4종 detector 메타 설정.

각 항목 키는 detector 식별자 (소문자 영문). 값은:
- weights        : YOLOv5/v8 가중치 .pt 절대 경로
- event_name     : event_types 테이블에 들어갈 한국어 이름
- risk_level     : DANGER / WARNING / CAUTION / INFO
- camera_ids     : 이 detector 가 적용될 camera_id 리스트
- conf_thres     : confidence 임계 (NMS 후)
- iou_thres      : NMS IoU 임계 (기본 0.45)
- img_size       : 입력 letterbox size (보통 640)
- target_classes : 검출 대상 클래스 라벨 리스트. None 이면 전체.
- storage_prefix : Storage object key prefix (camera-captures/detection/{cam}/{prefix}_*.jpg)

DETECTORS_ENABLED 환경변수 (쉼표 구분) 로 활성/비활성 토글.
가중치 경로의 base 는 환경변수 DETECTOR_WEIGHTS_BASE 로 override 가능 — config.py 가
absolute path 로 합성.

쓰러짐 (yolov7-pose) 은 본 dict 에 포함되지 않음. 별도 fall_detector.py 를 사용.
"""

from __future__ import annotations

DETECTOR_CONFIGS: dict[str, dict] = {
    "fire": {
        # 2026-05-20 FIRE-ADV-01 적용 — yolov26-fire-detection (HuggingFace
        # SalahALHaismawi/yolov26-fire-detection) 모델 교체. Ultralytics
        # YOLOv26-S, Roboflow 8939 image 학습, MIT 라이센스.
        # 이전: yolov5 + fire_best.pt + conf 0.10 (D-19 fallback, v0.5 baseline)
        # 신규: yolov8 path (ultralytics YOLO) + yolov26 best.pt + conf 0.25 (default)
        # classes: {0: 'fire', 1: 'other', 2: 'smoke'} — smoke 자동 분리됨
        #   (FIRE-ADV-02 의 일부 자동 진행 — 단 risk_level 차등은 후속)
        "framework": "yolov8",
        "weights": r"D:\2025_산업안전\산업안전\모델 7종\화재 탐지\yolov26_fire_best.pt",
        "event_name": "화재",
        "risk_level": "DANGER",
        "camera_ids": [1],
        # YOLO26 default conf 0.25 — D-19 fallback (0.10) 해제, 운영 임계 정상화.
        # 검증 후 0.30~0.50 으로 조정 가능 (사용자 test).
        "conf_thres": 0.25,
        "iou_thres": 0.45,
        # Phase 2 MODEL-01 frames_required 5 유지 (YOLO26 안정성 검증 전).
        # 사용자 test 후 조정 가능 — 새 모델이 single-frame 에서 안정적이면 1~3 으로
        # 단축 검토. 단축 시 false positive 위험 ↑ → 시간축 fusion (FIRE-ADV-03) 추가
        # 도입 필요.
        "frames_required": 5,
        "img_size": 640,
        # target_classes=['fire','smoke'] — 'other' 클래스는 noise 가능 (Roboflow
        # dataset 의 ambiguous 라벨), 안전 위해 fire/smoke 만 알람 발사.
        # 'other' 도 detect_all() 결과에는 포함되지만 단독 알람 trigger X.
        "target_classes": ["fire", "smoke"],
        "storage_prefix": "fire",
    },
    # Phase 3 D-04: helmet 단독 알람 경로 → fusion 으로 대체 (ROADMAP Phase 3 SC #2).
    # disabled=True: run_detection_cycle 에서 helmet detector 를 단독 알람 루프에서 스킵.
    # target_classes=None: detect_all() 가 head + helmet 두 라벨 모두 반환하도록.
    # frames_required=1: 단독 알람 경로 제거 (fusion buffer 가 N=3 관리).
    # 이전 값: target_classes=['head'], frames_required=3, disabled 없음 (Phase 1/2 lock).
    "helmet": {
        "disabled": True,
        "framework": "yolov5",
        "weights": r"D:\2025_산업안전\산업안전\모델 7종\안전모 탐지\hard_hat_best.pt",
        "event_name": "안전모 미착용",
        "risk_level": "WARNING",
        "camera_ids": [5],
        # 운영급 임계 (Phase 1 / DATA-01 / D-04 정상). 안전모 미착용 = head 객체 검출 시 알람.
        # helmet_h0_demo.mp4 (H0/L2_D2023-08-31-09-08_001 시퀀스 × 3 loop, 30s 영상) 에서
        # 87% frame head 검출, max conf 0.871, seek=10s 시 head conf 0.697 검증.
        "conf_thres": 0.5,
        "iou_thres": 0.45,
        # D-04: 1 로 변경 — 단독 알람 경로 제거. fusion buffer (_fusion_buffer N=3) 가 대체.
        "frames_required": 1,
        "img_size": 640,
        "target_classes": None,  # D-04: head + helmet 두 라벨 모두 detect_all() 에 포함
        "storage_prefix": "helmet",
    },
    "forklift": {
        "framework": "yolov5",
        "weights": r"D:\2025_산업안전\산업안전\모델 7종\지게차 탐지\best.pt",
        "event_name": "지게차 진입",
        "risk_level": "WARNING",
        "camera_ids": [4],
        "conf_thres": 0.25,
        "iou_thres": 0.45,
        # Phase 2 MODEL-01 / D-04: 지게차 진입 1회 검출만으로도 알람 (시간 누적 불필요).
        "frames_required": 1,
        "img_size": 640,
        "target_classes": None,  # forklift_1 / forklift_2 둘 다 OK
        "storage_prefix": "forklift",
    },
    "person": {
        # legacy `last_hardhat_200_epochs.pt` 는 사실 안전모 모델이라 person 클래스 부재.
        # ultralytics 가 'yolov8x.pt' 를 자동 다운로드 (~130MB, 80 COCO 클래스 포함).
        "framework": "yolov8",
        "weights": "yolov8x.pt",
        "event_name": "혼잡도 경고",
        "risk_level": "CAUTION",
        "camera_ids": [3],
        "conf_thres": 0.30,
        "iou_thres": 0.45,
        # Phase 2 MODEL-01 / D-04: 사람 등장 1회 검출만으로도 알람.
        "frames_required": 1,
        "img_size": 640,
        "target_classes": ["person"],
        "storage_prefix": "person",
    },
}
