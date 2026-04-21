"""ai_agent 진입점.

사용:
    python main.py          # 스케줄러 시작 (첫 주기 즉시 실행 후 10분 간격 반복)
    python main.py --once   # 1회만 실행하고 종료 (디버깅용)
"""

from __future__ import annotations

import argparse
import logging
import sys

from config import load_settings
from scheduler import build_scheduler, run_cycle
from supabase_client import SupabaseBridge


def _configure_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level, logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="P.A.S.S. CCTV 스냅샷 agent")
    parser.add_argument(
        "--once",
        action="store_true",
        help="1회만 실행하고 종료 (스케줄러 미사용)",
    )
    args = parser.parse_args(argv)

    settings = load_settings()
    _configure_logging(settings.log_level)
    log = logging.getLogger("ai_agent")
    log.info("설정 로드 완료: bucket=%s, interval=%d분",
             settings.captures_bucket, settings.snapshot_interval_min)

    bridge = SupabaseBridge(settings)
    try:
        if args.once:
            run_cycle(bridge, settings)
            return 0

        # 최초 1회 즉시 실행
        run_cycle(bridge, settings)

        scheduler = build_scheduler(settings, bridge)
        log.info(
            "스케줄러 시작 (interval=%d분). Ctrl+C로 종료.",
            settings.snapshot_interval_min,
        )
        try:
            scheduler.start()
        except (KeyboardInterrupt, SystemExit):
            log.info("종료 신호 수신 — 스케줄러 중단")
        return 0
    finally:
        bridge.close()


if __name__ == "__main__":
    sys.exit(main())
