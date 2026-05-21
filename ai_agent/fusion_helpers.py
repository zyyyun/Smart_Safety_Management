"""Fusion helper functions — pure math, no side effects (Phase 3, D-02).

Functions:
  iou_xyxy                  — IoU of two (x1,y1,x2,y2) bboxes
  hardhat_is_on             — helmet center point in person head region
  compute_fusion_collision  — any forklift-person pair IoU > threshold
  compute_fusion_helmet_missing — any person without matching helmet

Source: ported from
  D:\\2025_산업안전\\산업안전\\모델 7종\\사람 탐지\\utils\\bbox_utils.py:10-29
  with y2 clamp bug fix (original: `if y2 < 0: y1 = 0` → corrected: `y2 = max(0.0, ...)`)
"""
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from yolo_detector import DetectResult


def iou_xyxy(bbox_a: tuple, bbox_b: tuple) -> float:
    """IoU of two (x1,y1,x2,y2) bboxes. Returns 0.0 if no overlap or degenerate."""
    ix1 = max(bbox_a[0], bbox_b[0])
    iy1 = max(bbox_a[1], bbox_b[1])
    ix2 = min(bbox_a[2], bbox_b[2])
    iy2 = min(bbox_a[3], bbox_b[3])
    inter_w = max(0.0, ix2 - ix1)
    inter_h = max(0.0, iy2 - iy1)
    intersection = inter_w * inter_h
    area_a = max(0.0, bbox_a[2] - bbox_a[0]) * max(0.0, bbox_a[3] - bbox_a[1])
    area_b = max(0.0, bbox_b[2] - bbox_b[0]) * max(0.0, bbox_b[3] - bbox_b[1])
    union = area_a + area_b - intersection
    return 0.0 if union == 0 else intersection / union


def hardhat_is_on(person_bbox: tuple, helmet_bbox: tuple, frame_width: int) -> bool:
    """helmet bbox 중심점이 person 의 head 영역 안에 있으면 True.

    Head 영역 정의 (D-08, 레거시 bbox_utils.py 이식):
      가로: person bbox 중심 ± person_width/6  (frame 경계 clamp)
      세로: [person_top - hardhat_height/2, person_bottom - person_height*3/4]  (≥ 0 clamp)

    Args:
        person_bbox:  (x1, y1, x2, y2)
        helmet_bbox:  (x1, y1, x2, y2)
        frame_width:  이미지 가로 px (clamp 상한)
    """
    fw = max(1, frame_width)
    person_height = person_bbox[3] - person_bbox[1]
    person_width  = person_bbox[2] - person_bbox[0]
    hardhat_height = helmet_bbox[3] - helmet_bbox[1]

    center_x = person_bbox[0] + person_width / 2
    x1 = max(0.0, center_x - person_width / 6)
    x2 = min(float(fw), center_x + person_width / 6)

    y1 = max(0.0, person_bbox[1] - hardhat_height / 2)
    # 원본 레거시 버그 수정: `if y2 < 0: y1 = 0` → `y2 = max(0.0, ...)`
    y2 = max(0.0, person_bbox[3] - person_height * 3 / 4)

    helmet_cx = (helmet_bbox[0] + helmet_bbox[2]) / 2
    helmet_cy = (helmet_bbox[1] + helmet_bbox[3]) / 2
    return x1 <= helmet_cx <= x2 and y1 <= helmet_cy <= y2


def compute_fusion_collision(
    forklifts: "list[DetectResult]",
    persons: "list[DetectResult]",
    iou_thres: float,
) -> bool:
    """지게차-사람 bbox 쌍 중 IoU > iou_thres 인 쌍이 1개 이상이면 True (FUSION-01, D-07).

    빈 list 입력 시 False. v1.0 brute-force O(N×M).
    """
    if not forklifts or not persons:
        return False
    for f in forklifts:
        for p in persons:
            if f.bbox is not None and p.bbox is not None:
                if iou_xyxy(f.bbox, p.bbox) > iou_thres:
                    return True
    return False


def compute_fusion_helmet_missing(
    persons: "list[DetectResult]",
    helmets: "list[DetectResult]",
    frame_width: int,
) -> bool:
    """한 명이라도 안전모 미착용이면 True (FUSION-02, D-08).

    판정 기준: person bbox head 영역 (top 25% × ±width/6) 안에
    helmet bbox 중심점이 없으면 미착용.

    persons 가 비면 False (사람 없음 = 알람 없음).
    helmets 가 비면 즉시 True (사람 있고 helmet 0개 = 전원 미착용).
    """
    if not persons:
        return False
    if not helmets:
        return True  # persons > 0 이고 helmets == 0 → 전원 미착용
    for p in persons:
        if p.bbox is None:
            continue
        matched = any(
            h.bbox is not None and hardhat_is_on(p.bbox, h.bbox, frame_width)
            for h in helmets
        )
        if not matched:
            return True  # 한 명이라도 미착용 → True
    return False
