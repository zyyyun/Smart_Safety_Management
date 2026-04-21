"""APScheduler 기반 주기 스냅샷 파이프라인.

10분 주기로 카메라 목록을 조회하고, 각 카메라에 대해:
  1. RTSP → JPEG 프레임 추출
  2. Supabase Storage 업로드
  3. system/camera_capture Edge Function 호출
  4. 임시 파일 정리
"""

from __future__ import annotations

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.interval import IntervalTrigger

from config import Settings
from snapshot import SnapshotError, capture
from supabase_client import SupabaseBridge


log = logging.getLogger(__name__)


def _process_single_camera(
    bridge: SupabaseBridge, settings: Settings, camera: dict[str, Any]
) -> str:
    camera_id = camera["camera_id"]
    rtsp_url = camera["live_url_detail"]

    tmp_name = f"snapshot_{camera_id}_{int(time.time() * 1000)}.jpg"
    tmp_path = settings.snapshot_tmp_dir / tmp_name

    try:
        capture(rtsp_url, tmp_path, ffmpeg_bin=settings.ffmpeg_bin)
        public_url, object_path = bridge.upload_snapshot(camera_id, tmp_path)
        response = bridge.register_periodic_capture(camera_id, public_url, object_path)
        deleted = response.get("retention", {}).get("deleted", 0)
        return f"[OK] camera_id={camera_id} url={public_url} retention.deleted={deleted}"
    except SnapshotError as e:
        return f"[SNAPSHOT_ERR] camera_id={camera_id}: {e}"
    except Exception as e:
        return f"[ERR] camera_id={camera_id}: {e!r}"
    finally:
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass


def run_cycle(bridge: SupabaseBridge, settings: Settings) -> None:
    """한 주기 실행 — 모든 활성 카메라에 대해 병렬 처리."""
    log.info("=== 스냅샷 사이클 시작 ===")
    try:
        cameras = bridge.fetch_active_cameras()
    except Exception as e:
        log.error("카메라 목록 조회 실패: %s", e)
        return

    if not cameras:
        log.info("활성 카메라가 없습니다.")
        return

    log.info("대상 카메라 %d개 처리 시작", len(cameras))
    with ThreadPoolExecutor(max_workers=min(8, len(cameras))) as pool:
        futures = [
            pool.submit(_process_single_camera, bridge, settings, cam)
            for cam in cameras
        ]
        for future in as_completed(futures):
            log.info(future.result())
    log.info("=== 스냅샷 사이클 종료 ===")


def build_scheduler(settings: Settings, bridge: SupabaseBridge) -> BlockingScheduler:
    """Interval 트리거로 run_cycle을 반복하는 scheduler를 구성해 반환.

    실제 start()는 호출자가 담당. 첫 주기 즉시 실행은 main.py에서 별도 처리.
    """
    scheduler = BlockingScheduler()
    scheduler.add_job(
        run_cycle,
        trigger=IntervalTrigger(minutes=settings.snapshot_interval_min),
        args=[bridge, settings],
        id="periodic_snapshot",
        max_instances=1,
        coalesce=True,
    )
    return scheduler
