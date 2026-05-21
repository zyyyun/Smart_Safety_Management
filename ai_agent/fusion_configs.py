"""Fusion event 설정 (Phase 3, D-03).

각 항목은 두 detector 의 bbox 를 공간 매칭해 판정하는 fusion event 를 정의한다.
detector_configs.py 의 DETECTOR_CONFIGS 와 별도 — 의미 다름 (fusion vs single detector).

Keys per entry:
  event_name         str   — event_types 테이블 매핑 (register_ai_event 에 전달)
  risk_level         str   — DANGER / WARNING / CAUTION
  detectors_required list  — DETECTOR_CONFIGS 의 키 list (fusion 에 필요한 detector)
  camera_ids         list  — fusion 가 실행될 camera_id list
  rule               str   — iou_gt | hardhat_missing
  threshold          float — 룰별 임계 (iou_gt: IoU 임계; hardhat_missing: 미사용)
  frames_required    int   — N 연속 True 가 있어야 알람 (_fusion_buffer 기반, D-06)
  storage_prefix     str   — 스냅샷 Supabase Storage prefix
"""
from __future__ import annotations

FUSION_CONFIGS: dict[str, dict] = {
    # FUSION-01: 지게차+사람 IoU > 0.3 이 N=3 프레임 연속 → "지게차 충돌 위험" (D-07)
    # camera_ids = [4]: forklift detector 가 사용 중인 camera 재사용 (D-09)
    "forklift_collision": {
        "event_name": "지게차 충돌 위험",
        "risk_level": "DANGER",
        "detectors_required": ["forklift", "person"],
        "camera_ids": [4],
        "rule": "iou_gt",
        "threshold": 0.3,
        "frames_required": 3,
        "storage_prefix": "forklift_collision",
    },
    # FUSION-02: 사람 head 영역 내 helmet 미매칭 → "안전모 미착용" (D-08)
    # camera_ids = [5]: helmet detector 가 사용 중인 camera 재사용 (D-09)
    # helmet 단독 알람 경로는 D-04 에 의해 제거됨 — fusion 이 인수
    "helmet_missing": {
        "event_name": "안전모 미착용",
        "risk_level": "WARNING",
        "detectors_required": ["helmet", "person"],
        "camera_ids": [5],
        "rule": "hardhat_missing",
        "threshold": 0.0,   # hardhat_missing rule 은 threshold 미사용 — 형식용 0.0
        "frames_required": 3,
        "storage_prefix": "helmet_missing",
    },
}
