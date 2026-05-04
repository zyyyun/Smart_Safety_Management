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
        "framework": "yolov5",
        "weights": r"D:\2025_산업안전\산업안전\모델 7종\화재 탐지\fire_best.pt",
        "event_name": "화재",
        "risk_level": "DANGER",
        "camera_ids": [1],
        # 운영급 임계 (Phase 1 / DATA-02 / D-04). 신규 영상
        # `발표자료용 영상/detection(fire, helmet).mp4` 에서 fire conf >= 0.5 로 검출 검증.
        "conf_thres": 0.5,
        "iou_thres": 0.45,
        "img_size": 640,
        "target_classes": None,
        "storage_prefix": "fire",
    },
    "helmet": {
        "framework": "yolov5",
        "weights": r"D:\2025_산업안전\산업안전\모델 7종\안전모 탐지\hard_hat_best.pt",
        "event_name": "안전모 미착용",
        "risk_level": "WARNING",
        "camera_ids": [5],
        # 운영급 임계 (Phase 1 / DATA-01 / D-04). 안전모 미착용 = head 객체 검출 시 알람.
        # 신규 영상 `발표자료용 영상/detection(fire, helmet).mp4` 에 작업자 등장 → head 검출 검증.
        "conf_thres": 0.5,
        "iou_thres": 0.45,
        "img_size": 640,
        "target_classes": ["head"],
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
        "img_size": 640,
        "target_classes": ["person"],
        "storage_prefix": "person",
    },
}
