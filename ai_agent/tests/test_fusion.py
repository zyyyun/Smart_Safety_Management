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

    _fusion_buffer 는 Plan 02 Task 2 에서 추가됨 — getattr 로 forward-compat.
    """
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    getattr(scheduler, "_fusion_buffer", {}).clear()
    yield
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    getattr(scheduler, "_fusion_buffer", {}).clear()


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
