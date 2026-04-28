"""ai_agent 진입점.

사용:
    python main.py                # 스냅샷(10분) + 쓰러짐(1분) + 일반감지(1분) 상시 실행
    python main.py --once         # 10분 스냅샷 사이클 1회만 실행 (디버깅)
    python main.py --once-fall    # 쓰러짐 감지 사이클 1회만 실행 (디버깅)
    python main.py --once-detect  # 일반 감지 4종 사이클 1회만 실행 (LP-2 확장 검증)
    python main.py --no-fall      # 쓰러짐 감지 비활성
    python main.py --no-detect    # 일반 감지 4종 비활성

모든 detector 는 시작 시 eager load — 가중치 경로 오류 등을 즉시 surface.
"""

from __future__ import annotations

import argparse
import logging
import sys

from config import load_settings
from detector_configs import DETECTOR_CONFIGS
from fall_detector import FallDetector
from scheduler import (
    build_scheduler,
    run_cycle,
    run_detection_cycle,
    run_fall_cycle,
)
from supabase_client import SupabaseBridge
from yolo_detector import GenericYoloDetector


def _configure_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level, logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


def _load_fall_detector(settings) -> FallDetector:
    return FallDetector(
        weights_path=settings.fall_model_weights,
        device=settings.fall_device,
        conf_thres=settings.fall_conf_thres,
        iou_thres=settings.fall_iou_thres,
        img_size=settings.fall_img_size,
    )


def _load_general_detectors(settings) -> dict[str, GenericYoloDetector]:
    """DETECTORS_ENABLED 에 포함된 detector 만 eager load. 일부 실패 시 경고 후 스킵."""
    log = logging.getLogger("ai_agent")
    detectors: dict[str, GenericYoloDetector] = {}
    enabled = settings.detectors_enabled
    if not enabled:
        return detectors

    for key in enabled:
        cfg = DETECTOR_CONFIGS.get(key)
        if cfg is None:
            log.warning("DETECTORS_ENABLED 의 '%s' 가 DETECTOR_CONFIGS 에 없음, 스킵", key)
            continue
        try:
            detectors[key] = GenericYoloDetector(
                name=key,
                weights_path=cfg["weights"],
                conf_thres=cfg.get("conf_thres", 0.25),
                iou_thres=cfg.get("iou_thres", 0.45),
                img_size=cfg.get("img_size", 640),
                target_classes=cfg.get("target_classes"),
                device=settings.detectors_device,
                framework=cfg.get("framework", "yolov8"),
            )
        except FileNotFoundError as e:
            log.error("[%s] eager load 실패 (가중치 부재): %s", key, e)
        except Exception as e:
            log.error("[%s] eager load 실패: %r", key, e)
    return detectors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="P.A.S.S. ai_agent 진입점")
    parser.add_argument(
        "--once",
        action="store_true",
        help="10분 스냅샷 사이클만 1회 실행 후 종료",
    )
    parser.add_argument(
        "--once-fall",
        action="store_true",
        help="쓰러짐 감지 사이클만 1회 실행 후 종료 (모델 로드 + 추론 검증용)",
    )
    parser.add_argument(
        "--once-detect",
        action="store_true",
        help="일반 감지(4종) 사이클만 1회 실행 후 종료 (LP-2 확장 검증용)",
    )
    parser.add_argument(
        "--no-fall",
        action="store_true",
        help="쓰러짐 감지 스케줄을 등록하지 않음",
    )
    parser.add_argument(
        "--no-detect",
        action="store_true",
        help="일반 감지(4종) 스케줄을 등록하지 않음",
    )
    args = parser.parse_args(argv)

    settings = load_settings()
    _configure_logging(settings.log_level)
    log = logging.getLogger("ai_agent")
    log.info(
        "설정 로드 완료: bucket=%s snapshot=%d분 fall=%d분(%s) detectors=%s(%s)",
        settings.captures_bucket,
        settings.snapshot_interval_min,
        settings.fall_interval_min,
        list(settings.fall_enabled_camera_ids),
        list(settings.detectors_enabled),
        f"{settings.detectors_interval_min}분",
    )
    log.warning(
        "쿨타임 상태는 메모리에만 유지됩니다 — agent 재시작 시 중복 감지 1건 가능",
    )

    bridge = SupabaseBridge(settings)
    detector = None
    detectors: dict[str, GenericYoloDetector] = {}
    try:
        # 로드 순서 주의 — yolov7_fork (FallDetector) 와 torch.hub yolov5 (일반 4종) 가
        # sys.path 의 'models'/'utils' 패키지 이름을 공유해 충돌함. 해결되기 전까지는
        # 단일 프로세스에서 동시 활성화는 권장 안 함.
        # --once-detect : fall 비활성, 일반 4종만
        # --once-fall   : 일반 비활성, fall 만
        # 상시 실행      : 둘 다 활성 시도 (충돌 가능성 — 후속 분리 예정)
        if args.once_detect:
            need_fall, need_detect = False, True
        elif args.once_fall:
            need_fall, need_detect = True, False
        else:
            need_fall = not args.no_fall
            need_detect = (
                not args.no_detect and len(settings.detectors_enabled) > 0
            )

        # yolov5 먼저 → fall 나중 (yolov7_fork prepend 가 yolov5 hub 보다 뒤에 가도록)
        if need_detect:
            log.info("일반 detector eager load 중… (활성: %s)", list(settings.detectors_enabled))
            detectors = _load_general_detectors(settings)

        if need_fall:
            log.info("FallDetector eager load 중…")
            detector = _load_fall_detector(settings)

        # 1회 실행 모드
        if args.once_fall:
            if detector is None:
                detector = _load_fall_detector(settings)
            run_fall_cycle(bridge, settings, detector)
            return 0
        if args.once_detect:
            if not detectors:
                detectors = _load_general_detectors(settings)
            if not detectors:
                log.error("로드된 detector 가 없어 --once-detect 종료")
                return 1
            run_detection_cycle(bridge, settings, detectors)
            return 0
        if args.once:
            run_cycle(bridge, settings)
            return 0

        # 스케줄러 상시 실행 — 최초 1회 즉시 실행
        run_cycle(bridge, settings)
        if detector is not None:
            run_fall_cycle(bridge, settings, detector)
        if detectors:
            run_detection_cycle(bridge, settings, detectors)

        scheduler = build_scheduler(
            settings, bridge, detector=detector, detectors=detectors
        )
        jobs = [j.id for j in scheduler.get_jobs()]
        log.info("스케줄러 시작. 등록된 잡: %s. Ctrl+C 로 종료.", jobs)
        try:
            scheduler.start()
        except (KeyboardInterrupt, SystemExit):
            log.info("종료 신호 수신 — 스케줄러 중단")
        return 0
    finally:
        bridge.close()


if __name__ == "__main__":
    sys.exit(main())
