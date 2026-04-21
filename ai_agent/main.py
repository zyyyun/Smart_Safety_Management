"""ai_agent 진입점.

사용:
    python main.py                # 스냅샷(10분) + 쓰러짐(1분) 스케줄러 상시 실행
    python main.py --once         # 10분 스냅샷 사이클 1회만 실행 (디버깅)
    python main.py --once-fall    # 쓰러짐 감지 사이클 1회만 실행 (디버깅)
    python main.py --no-fall      # 쓰러짐 감지 비활성 (스냅샷만)

쓰러짐 모델은 시작 시 eager load — 가중치 경로 오류 등을 즉시 surface.
"""

from __future__ import annotations

import argparse
import logging
import sys

from config import load_settings
from fall_detector import FallDetector
from scheduler import build_scheduler, run_cycle, run_fall_cycle
from supabase_client import SupabaseBridge


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
        "--no-fall",
        action="store_true",
        help="쓰러짐 감지 스케줄을 등록하지 않음 (스냅샷 잡만 실행)",
    )
    args = parser.parse_args(argv)

    settings = load_settings()
    _configure_logging(settings.log_level)
    log = logging.getLogger("ai_agent")
    log.info(
        "설정 로드 완료: bucket=%s snapshot=%d분 fall=%d분 fall_cameras=%s",
        settings.captures_bucket,
        settings.snapshot_interval_min,
        settings.fall_interval_min,
        list(settings.fall_enabled_camera_ids),
    )
    log.warning(
        "쓰러짐 쿨타임 상태는 메모리에만 유지됩니다 — agent 재시작 시 중복 감지 1건 가능",
    )

    bridge = SupabaseBridge(settings)
    detector = None
    try:
        # 쓰러짐 감지 사용 여부 결정
        need_detector = not args.no_fall or args.once_fall
        if need_detector:
            log.info("FallDetector eager load 중…")
            detector = _load_fall_detector(settings)

        # 1회 실행 모드
        if args.once_fall:
            if detector is None:
                detector = _load_fall_detector(settings)
            run_fall_cycle(bridge, settings, detector)
            return 0
        if args.once:
            run_cycle(bridge, settings)
            return 0

        # 스케줄러 상시 실행
        # 최초 1회 즉시 실행 (스케줄러가 다음 interval 까지 대기하지 않도록)
        run_cycle(bridge, settings)
        if detector is not None:
            run_fall_cycle(bridge, settings, detector)

        scheduler = build_scheduler(settings, bridge, detector=detector)
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
