"""test_fusion.py — Phase 3 bbox fusion unit tests (D-10).

Cases 1-7: pure helper functions (fusion_helpers.py)
Case 8:    _process_fusion_for_camera integration (added in Plan 02 Task 2)
"""
from __future__ import annotations

import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

AGENT_DIR = Path(__file__).resolve().parents[1]
if str(AGENT_DIR) not in sys.path:
    sys.path.insert(0, str(AGENT_DIR))

import scheduler  # noqa: E402
import fusion_helpers  # noqa: E402


# ──────────────────────────────────────────────
# Fixtures
# ──────────────────────────────────────────────

@pytest.fixture(autouse=True)
def reset_module_state():
    """매 테스트 전후 scheduler 모듈 상태 초기화.

    Plan 02 Task 2 이후 _fusion_buffer 가 정식 추가되었으므로 직접 참조.
    """
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    scheduler._fusion_buffer.clear()
    yield
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    scheduler._fusion_buffer.clear()


def _dr(x1, y1, x2, y2, conf=0.8, label="obj"):
    """Helper: create a DetectResult-like SimpleNamespace with bbox."""
    return SimpleNamespace(
        is_detected=True,
        confidence=float(conf),
        bbox=(float(x1), float(y1), float(x2), float(y2)),
        label=label,
        inference_ms=12.0,
    )


# ──────────────────────────────────────────────
# Case 1: iou_xyxy
# ──────────────────────────────────────────────

def test_iou_xyxy_identical_bbox():
    """동일 bbox → IoU = 1.0"""
    assert fusion_helpers.iou_xyxy((0, 0, 10, 10), (0, 0, 10, 10)) == 1.0


def test_iou_xyxy_no_overlap():
    """겹침 없는 bbox → IoU = 0.0"""
    assert fusion_helpers.iou_xyxy((0, 0, 5, 5), (10, 10, 20, 20)) == 0.0


def test_iou_xyxy_partial_overlap():
    """부분 겹침 → 0 < IoU < 1, 정확한 수치 검증"""
    # box_a: (0,0,10,10), box_b: (5,5,15,15)
    # intersection: (5,5,10,10) = 5×5 = 25
    # union: 100 + 100 - 25 = 175
    # IoU = 25/175 ≈ 0.1429
    result = fusion_helpers.iou_xyxy((0, 0, 10, 10), (5, 5, 15, 15))
    assert abs(result - 25 / 175) < 1e-6


def test_iou_xyxy_degenerate_union_zero():
    """degenerate bbox (width=0 또는 height=0) → union=0 → 0.0 (ZeroDivisionError 없음)"""
    assert fusion_helpers.iou_xyxy((0, 0, 0, 0), (0, 0, 0, 0)) == 0.0
    assert fusion_helpers.iou_xyxy((5, 5, 5, 10), (5, 5, 5, 10)) == 0.0  # width=0


# ──────────────────────────────────────────────
# Case 2: compute_fusion_collision — IoU > threshold → True
# ──────────────────────────────────────────────

def test_collision_iou_above_threshold():
    """IoU=0.35 > 0.3 한 쌍 → True (FUSION-01)"""
    # forklift: (0,0,10,10), person: (5,5,15,15) → IoU = 25/175 ≈ 0.143  — too low
    # Use fully overlapping scenario: forklift(0,0,10,10), person(0,0,9,9)
    # intersection=81, union=100+81-81=100, IoU=0.81 > 0.3 → True
    forklift = [_dr(0, 0, 10, 10, label="forklift_1")]
    person   = [_dr(0, 0, 9, 9, label="person")]
    assert fusion_helpers.compute_fusion_collision(forklift, person, 0.3) is True


# ──────────────────────────────────────────────
# Case 3: compute_fusion_collision — IoU ≤ threshold → False
# ──────────────────────────────────────────────

def test_collision_iou_below_threshold():
    """IoU=0.143 ≤ 0.3 → False"""
    forklift = [_dr(0, 0, 10, 10, label="forklift_1")]
    person   = [_dr(5, 5, 15, 15, label="person")]
    # IoU = 25/175 ≈ 0.143 < 0.3
    assert fusion_helpers.compute_fusion_collision(forklift, person, 0.3) is False


# ──────────────────────────────────────────────
# Case 4: compute_fusion_collision — empty list → False
# ──────────────────────────────────────────────

def test_collision_empty_forklifts():
    """forklift 리스트 빔 → False"""
    person = [_dr(0, 0, 10, 10, label="person")]
    assert fusion_helpers.compute_fusion_collision([], person, 0.3) is False


def test_collision_empty_persons():
    """person 리스트 빔 → False"""
    forklift = [_dr(0, 0, 10, 10, label="forklift_1")]
    assert fusion_helpers.compute_fusion_collision(forklift, [], 0.3) is False


# ──────────────────────────────────────────────
# Case 5: compute_fusion_helmet_missing — helmet in head area → False
# ──────────────────────────────────────────────

def test_helmet_worn():
    """person 1명 + helmet center 가 head 영역 내 → False (안전모 착용, FUSION-02)

    person bbox: (100, 100, 200, 300) — width=100, height=200
    head 영역 가로: center_x=150 ± 100/6 ≈ [133.3, 166.7]
    head 영역 세로: helmet_height default 사용, top 25% 영역 내
    helmet bbox: (140, 90, 160, 110) → center (150, 100) — head 영역 내 OK
    """
    # person: (x1=100, y1=100, x2=200, y2=300) → width=100, height=200
    # head x range: [100 + 100/2 - 100/6, 100 + 100/2 + 100/6] = [133.33, 166.67]
    # helmet: (140, 90, 160, 110) → center (150, 100)
    # hardhat_height = 110-90 = 20
    # head y1 = max(0, 100 - 20/2) = max(0, 90) = 90
    # head y2 = max(0, 300 - 200*3/4) = max(0, 150) = 150
    # helmet_cy = 100 ∈ [90, 150] ✓ and helmet_cx = 150 ∈ [133.33, 166.67] ✓ → True (착용)
    person = [_dr(100, 100, 200, 300, label="person")]
    helmet = [_dr(140, 90, 160, 110, label="head")]
    assert fusion_helpers.compute_fusion_helmet_missing(person, helmet, 640) is False


# ──────────────────────────────────────────────
# Case 6: compute_fusion_helmet_missing — no helmet → True
# ──────────────────────────────────────────────

def test_helmet_missing_no_helmet():
    """person 1명 + helmet 0개 → True (안전모 미착용, FUSION-02)"""
    person = [_dr(100, 100, 200, 300, label="person")]
    assert fusion_helpers.compute_fusion_helmet_missing(person, [], 640) is True


# ──────────────────────────────────────────────
# Case 7: compute_fusion_helmet_missing — partial match → True
# ──────────────────────────────────────────────

def test_helmet_missing_partial_match():
    """person 2명 + helmet 1개 (한 명만 매칭) → True (한 명 미착용, FUSION-02, D-08)"""
    # person1 (100,100,200,300): head area x=[133,167] y=[90,150]
    # person2 (300,100,400,300): head area x=[333,367] y=[90,150]
    # helmet center at (150, 100) → matches person1 only
    person1 = _dr(100, 100, 200, 300, label="person")
    person2 = _dr(300, 100, 400, 300, label="person")
    helmet  = _dr(140, 90, 160, 110, label="head")  # matches person1
    # person2 has no matching helmet → at least one unmatched → True
    assert fusion_helpers.compute_fusion_helmet_missing([person1, person2], [helmet], 640) is True


def test_helmet_missing_no_persons():
    """persons 리스트 빔 → False (사람 없음 = 알람 없음)"""
    helmet = [_dr(140, 90, 160, 110, label="head")]
    assert fusion_helpers.compute_fusion_helmet_missing([], helmet, 640) is False


# ──────────────────────────────────────────────
# Case 8: _process_fusion_for_camera — 3-cycle integration
# ──────────────────────────────────────────────

CAM_FUSION = {"camera_id": 4, "live_url_detail": "rtsp://fake/stream"}

SETTINGS_STUB = SimpleNamespace(
    snapshot_tmp_dir=Path("/tmp"),
    ffmpeg_bin="ffmpeg",
    detectors_demo_seek_sec=10,
    detectors_cooldown_min=10,
)


def make_bridge():
    bridge = MagicMock(name="bridge")
    bridge.upload_detection_snapshot.return_value = ("https://example.test/snap.jpg", "obj/path")
    bridge.register_ai_event.return_value = {"event_id": 999}
    return bridge


def make_detect_all_detector(sequences_by_call):
    """sequences_by_call: list of list[SimpleNamespace] — one list per detect_all() call."""
    iterator = iter(sequences_by_call)

    def _detect_all(_img):
        try:
            return next(iterator)
        except StopIteration:
            return []

    det = MagicMock(name="detector_all")
    det.detect_all.side_effect = _detect_all
    return det


def _forklift_bbox(iou_gt_03=True):
    """Synthetic forklift bbox that creates IoU > 0.3 with person_bbox() when iou_gt_03=True."""
    if iou_gt_03:
        return SimpleNamespace(
            is_detected=True, confidence=0.75,
            bbox=(0.0, 0.0, 10.0, 10.0),  # IoU with person (0,0,9,9) = 0.81
            label="forklift_1", inference_ms=10.0,
        )
    return SimpleNamespace(
        is_detected=True, confidence=0.75,
        bbox=(0.0, 0.0, 5.0, 5.0),  # IoU with person (10,10,20,20) = 0.0
        label="forklift_1", inference_ms=10.0,
    )


def _person_bbox(near=True):
    if near:
        return SimpleNamespace(
            is_detected=True, confidence=0.70,
            bbox=(0.0, 0.0, 9.0, 9.0),  # near forklift → IoU=0.81
            label="person", inference_ms=10.0,
        )
    return SimpleNamespace(
        is_detected=True, confidence=0.70,
        bbox=(10.0, 10.0, 20.0, 20.0),  # far from forklift → IoU=0
        label="person", inference_ms=10.0,
    )


FORKLIFT_CFG = {
    "event_name": "지게차 충돌 위험",
    "risk_level": "DANGER",
    "detectors_required": ["forklift", "person"],
    "camera_ids": [4],
    "rule": "iou_gt",
    "threshold": 0.3,
    "frames_required": 3,
    "storage_prefix": "forklift_collision",
}


def test_process_fusion_3cycles_triggers_alarm(monkeypatch):
    """3 cycle 연속 IoU>0.3 → 알람 1회 + buffer.clear (D-10 case 8, FUSION-01)"""
    monkeypatch.setattr(scheduler, "capture", lambda *a, **kw: None)
    fake_img = MagicMock(name="fake_img")
    fake_img.shape = (480, 640, 3)
    monkeypatch.setattr(scheduler.cv2, "imread", lambda _p: fake_img)

    bridge = make_bridge()
    # Each cycle: detect_all called twice (forklift, then person)
    forklift_det = make_detect_all_detector([
        [_forklift_bbox(True)],  # cycle 1
        [_forklift_bbox(True)],  # cycle 2
        [_forklift_bbox(True)],  # cycle 3
    ])
    person_det = make_detect_all_detector([
        [_person_bbox(True)],    # cycle 1
        [_person_bbox(True)],    # cycle 2
        [_person_bbox(True)],    # cycle 3
    ])
    detectors = {"forklift": forklift_det, "person": person_det}

    results = []
    for _ in range(3):
        msg = scheduler._process_fusion_for_camera(
            bridge, SETTINGS_STUB, "forklift_collision", FORKLIFT_CFG, CAM_FUSION, detectors
        )
        results.append(msg)

    # First 2 cycles: buffer accumulating, no alarm
    assert results[0].startswith("[no_fusion_yet]"), f"Expected [no_fusion_yet] got {results[0]}"
    assert results[1].startswith("[no_fusion_yet]"), f"Expected [no_fusion_yet] got {results[1]}"
    # 3rd cycle: N=3 consecutive True → alarm fires
    assert results[2].startswith("[FUSION]"), f"Expected [FUSION] got {results[2]}"
    assert bridge.register_ai_event.call_count == 1, "Alarm should fire exactly once"

    # After alarm: buffer cleared. Next cycle with cooldown active → fusion_skip_cooldown
    forklift_det2 = make_detect_all_detector([[_forklift_bbox(False)]])
    person_det2   = make_detect_all_detector([[_person_bbox(False)]])
    detectors2 = {"forklift": forklift_det2, "person": person_det2}
    # cooldown is now active (detectors_cooldown_min=10 minutes)
    msg_after = scheduler._process_fusion_for_camera(
        bridge, SETTINGS_STUB, "forklift_collision", FORKLIFT_CFG, CAM_FUSION, detectors2
    )
    # cooldown active after alarm → skip
    assert msg_after.startswith("[fusion_skip_cooldown]"), (
        f"After alarm + cooldown, expected [fusion_skip_cooldown] got {msg_after}"
    )
    assert bridge.register_ai_event.call_count == 1, "No additional alarm after cooldown"


def test_process_fusion_no_alarm_when_not_consecutive(monkeypatch):
    """N=3 중 하나가 False 이면 알람 미발사 (spike 흡수, D-12)"""
    monkeypatch.setattr(scheduler, "capture", lambda *a, **kw: None)
    fake_img = MagicMock(name="fake_img")
    fake_img.shape = (480, 640, 3)
    monkeypatch.setattr(scheduler.cv2, "imread", lambda _p: fake_img)

    bridge = make_bridge()
    # Cycle sequence: True, True, False — not 3 consecutive
    forklift_det = make_detect_all_detector([
        [_forklift_bbox(True)],
        [_forklift_bbox(True)],
        [_forklift_bbox(False)],  # IoU=0 → False
    ])
    person_det = make_detect_all_detector([
        [_person_bbox(True)],
        [_person_bbox(True)],
        [_person_bbox(False)],
    ])
    detectors = {"forklift": forklift_det, "person": person_det}

    results = []
    for _ in range(3):
        msg = scheduler._process_fusion_for_camera(
            bridge, SETTINGS_STUB, "forklift_collision", FORKLIFT_CFG, CAM_FUSION, detectors
        )
        results.append(msg)

    assert bridge.register_ai_event.call_count == 0, (
        f"Alarm should NOT fire with non-consecutive pattern. msgs={results}"
    )
