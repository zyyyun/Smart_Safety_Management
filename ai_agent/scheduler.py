"""APScheduler 기반 주기 파이프라인.

잡 3종을 하나의 BlockingScheduler 에 등록한다.

1) periodic_snapshot (10분)
   - 활성 카메라 전체에 대해 RTSP → JPEG → Storage → camera_captures(PERIODIC)
2) fall_detection (1분)
   - FALL_ENABLED_CAMERA_IDS 로 지정된 카메라만 대상
   - RTSP → JPEG → FallDetector(YOLOv7-pose) → is_fall 이면 Storage → detection_events
   - 카메라당 FALL_COOLDOWN_MIN 분 내 중복 방지 (메모리 상태)
3) general_detection (1분, LP-2 확장)
   - DETECTORS_ENABLED 로 지정된 detector 들이 자기 camera_ids 만 대상
   - RTSP → JPEG → GenericYoloDetector → is_detected 이면 Storage → detection_events
   - (camera_id, event_key) 별 DETECTORS_COOLDOWN_MIN 분 쿨타임
"""

from __future__ import annotations

import logging
import time
from collections import deque
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Optional

import cv2
from apscheduler.executors.pool import ThreadPoolExecutor as APSThreadPoolExecutor
from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.interval import IntervalTrigger

from config import Settings
from detector_configs import DETECTOR_CONFIGS
from fall_detector import FallDetector
from snapshot import SnapshotError, capture
from supabase_client import SupabaseBridge
from yolo_detector import GenericYoloDetector


log = logging.getLogger(__name__)

# 카메라별 마지막 쓰러짐 감지 타임스탬프 — agent 프로세스 메모리에 유지.
# 재시작 시 초기화 (plan D7 허용).
_fall_cooldown: dict[int, float] = {}

# (camera_id, event_key) 별 마지막 일반 detector 감지 타임스탬프.
_detection_cooldown: dict[tuple[int, str], float] = {}

# (camera_id, event_key) 별 최근 N 사이클 is_detected 결과 buffer (Phase 2 / MODEL-02).
# - maxlen = 5 (DETECTOR_CONFIGS 의 max frames_required = fire 5).
# - cooldown skip cycle 에서는 push 하지 않음 (D-07).
# - 알람 발사 시 buffer.clear() + cooldown 갱신 (D-02).
# - 1프로세스 PoC 휘발성 OK — 재시작 시 자동 reset (D-06).
_detection_buffer: dict[tuple[int, str], deque] = {}


# ──────────────────────────────────────────────
# periodic snapshot (기존 Next-4)
# ──────────────────────────────────────────────
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
    """10분 주기 스냅샷 — 모든 활성 카메라 병렬 처리."""
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


# ──────────────────────────────────────────────
# fall detection (Next-3 / LP-2)
# ──────────────────────────────────────────────
def _process_fall_for_camera(
    bridge: SupabaseBridge,
    settings: Settings,
    detector: FallDetector,
    camera: dict[str, Any],
) -> str:
    camera_id = int(camera["camera_id"])
    rtsp_url = camera["live_url_detail"]

    # 쿨타임 체크 — 마지막 감지 이후 FALL_COOLDOWN_MIN 분 이내면 스킵
    last_ts = _fall_cooldown.get(camera_id)
    now = time.time()
    if last_ts and (now - last_ts) < settings.fall_cooldown_min * 60:
        return f"[fall_skip_cooldown] camera_id={camera_id}"

    tmp_name = f"fall_probe_{camera_id}_{int(now * 1000)}.jpg"
    tmp_path = settings.snapshot_tmp_dir / tmp_name
    try:
        capture(
            rtsp_url,
            tmp_path,
            ffmpeg_bin=settings.ffmpeg_bin,
            seek_seconds=settings.fall_demo_seek_sec,
        )
        img = cv2.imread(str(tmp_path))
        if img is None:
            return f"[FALL_ERR] camera_id={camera_id}: cv2.imread failed"

        result = detector.detect_fall(img)
        if not result.is_fall:
            return (
                f"[no_fall] camera_id={camera_id} poses={result.pose_count} "
                f"inf={result.inference_ms:.0f}ms"
            )

        # 감지 → Storage 업로드 + detection_events 등록
        public_url, _object_path = bridge.upload_fall_snapshot(camera_id, tmp_path)
        event = bridge.register_ai_event(
            camera_id=camera_id,
            event_name="쓰러짐",
            risk_level="WARNING",
            accuracy=float(result.confidence or 0.0),
            image_url=public_url,
        )
        _fall_cooldown[camera_id] = now
        event_id = event.get("event_id")
        return (
            f"[FALL] camera_id={camera_id} conf={result.confidence:.2f} "
            f"event_id={event_id} url={public_url}"
        )
    except SnapshotError as e:
        return f"[FALL_SNAPSHOT_ERR] camera_id={camera_id}: {e}"
    except Exception as e:
        return f"[FALL_ERR] camera_id={camera_id}: {e!r}"
    finally:
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass


def run_fall_cycle(
    bridge: SupabaseBridge,
    settings: Settings,
    detector: FallDetector,
) -> None:
    """1분 주기 쓰러짐 감지 사이클."""
    enabled_ids = settings.fall_enabled_camera_ids
    if not enabled_ids:
        log.debug("FALL_ENABLED_CAMERA_IDS 비어있음 — 감지 스킵")
        return

    log.info("=== 쓰러짐 감지 사이클 시작 (대상 %s) ===", list(enabled_ids))
    try:
        all_cams = bridge.fetch_active_cameras()
    except Exception as e:
        log.error("카메라 목록 조회 실패: %s", e)
        return

    targets = [c for c in all_cams if int(c["camera_id"]) in enabled_ids]
    if not targets:
        log.info("활성 대상 카메라 없음")
        return

    # 1분 사이클이므로 직렬 실행 (CPU/GPU 경합 방지). 대상 5개 이하 가정.
    for cam in targets:
        msg = _process_fall_for_camera(bridge, settings, detector, cam)
        # no_fall / skip_cooldown 은 DEBUG, 실제 감지는 INFO 이상
        if msg.startswith(("[no_fall]", "[fall_skip_cooldown]")):
            log.debug(msg)
        else:
            log.info(msg)
    log.info("=== 쓰러짐 감지 사이클 종료 ===")


# ──────────────────────────────────────────────
# general detection (LP-2 확장 — 화재·안전모·지게차·사람)
# ──────────────────────────────────────────────
def _process_detection_for_camera(
    bridge: SupabaseBridge,
    settings: Settings,
    event_key: str,
    detector: GenericYoloDetector,
    cfg: dict[str, Any],
    camera: dict[str, Any],
) -> str:
    camera_id = int(camera["camera_id"])
    rtsp_url = camera["live_url_detail"]

    cooldown_key = (camera_id, event_key)
    last_ts = _detection_cooldown.get(cooldown_key)
    now = time.time()
    if last_ts and (now - last_ts) < settings.detectors_cooldown_min * 60:
        return f"[detect_skip_cooldown] camera_id={camera_id} event={event_key}"

    tmp_name = f"detect_{event_key}_{camera_id}_{int(now * 1000)}.jpg"
    tmp_path = settings.snapshot_tmp_dir / tmp_name
    try:
        capture(
            rtsp_url,
            tmp_path,
            ffmpeg_bin=settings.ffmpeg_bin,
            seek_seconds=settings.detectors_demo_seek_sec,
        )
        img = cv2.imread(str(tmp_path))
        if img is None:
            return f"[DETECT_ERR] camera_id={camera_id} event={event_key}: cv2.imread failed"

        result = detector.detect(img)

        # Phase 2 MODEL-02: buffer push (cooldown skip 이 아닌 모든 detect 결과를 누적).
        # maxlen 은 DETECTOR_CONFIGS 의 max frames_required (현재 fire 5).
        # buffer 미존재 시 lazy 생성. is_detected=False 도 push (연속성 깨뜨리는 신호).
        frames_required = int(cfg.get("frames_required", 1))
        buffer = _detection_buffer.get(cooldown_key)
        if buffer is None:
            buffer = deque(maxlen=max(5, frames_required))
            _detection_buffer[cooldown_key] = buffer
        buffer.append(bool(result.is_detected))

        if not result.is_detected:
            return (
                f"[no_detect] camera_id={camera_id} event={event_key} "
                f"inf={result.inference_ms:.0f}ms"
            )

        # Phase 2 MODEL-02: N 연속 검사 — 최근 N 결과가 모두 True 일 때만 알람 발사.
        # forklift/person 의 N=1 도 동일 코드 경로 (D-04, 분기 없이 즉시 통과).
        recent = list(buffer)[-frames_required:]
        if len(buffer) < frames_required or not all(recent):
            return (
                f"[no_alert_yet] camera_id={camera_id} event={event_key} "
                f"frames={sum(recent)}/{frames_required} "
                f"conf={result.confidence:.2f}"
            )

        public_url, _path = bridge.upload_detection_snapshot(
            camera_id, cfg.get("storage_prefix", event_key), tmp_path
        )
        event = bridge.register_ai_event(
            camera_id=camera_id,
            event_name=cfg["event_name"],
            risk_level=cfg["risk_level"],
            accuracy=float(result.confidence or 0.0),
            image_url=public_url,
        )
        # Phase 2 MODEL-02: 알람 발사 후 buffer reset (D-02).
        # 다음 알람은 cooldown 만료 + buffer 재누적 N 연속 둘 다 충족 시에만 발생.
        buffer.clear()
        _detection_cooldown[cooldown_key] = now
        event_id = event.get("event_id")
        return (
            f"[DETECT] camera_id={camera_id} event={event_key} "
            f"label={result.label} conf={result.confidence:.2f} "
            f"event_id={event_id} url={public_url}"
        )
    except SnapshotError as e:
        return f"[DETECT_SNAPSHOT_ERR] camera_id={camera_id} event={event_key}: {e}"
    except Exception as e:
        return f"[DETECT_ERR] camera_id={camera_id} event={event_key}: {e!r}"
    finally:
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass


def run_detection_cycle(
    bridge: SupabaseBridge,
    settings: Settings,
    detectors: dict[str, GenericYoloDetector],
) -> None:
    """1분 주기 일반 감지 사이클 — 4종 detector 전부 순회."""
    if not detectors:
        log.debug("DETECTORS_ENABLED 비어있음 — 감지 스킵")
        return

    log.info("=== 일반 감지 사이클 시작 (활성 %s) ===", list(detectors.keys()))
    try:
        all_cams = bridge.fetch_active_cameras()
    except Exception as e:
        log.error("카메라 목록 조회 실패: %s", e)
        return

    cams_by_id = {int(c["camera_id"]): c for c in all_cams}

    for event_key, detector in detectors.items():
        cfg = DETECTOR_CONFIGS.get(event_key)
        if cfg is None:
            log.warning("[%s] DETECTOR_CONFIGS 에 항목 없음, 스킵", event_key)
            continue
        target_cams = [
            cams_by_id[cid] for cid in cfg["camera_ids"] if cid in cams_by_id
        ]
        if not target_cams:
            log.debug(
                "[%s] 매핑 카메라 %s 중 활성된 것 없음", event_key, cfg["camera_ids"]
            )
            continue
        for cam in target_cams:
            msg = _process_detection_for_camera(
                bridge, settings, event_key, detector, cfg, cam
            )
            if msg.startswith(("[no_detect]", "[detect_skip_cooldown]", "[no_alert_yet]")):
                log.debug(msg)
            else:
                log.info(msg)
    log.info("=== 일반 감지 사이클 종료 ===")


# ──────────────────────────────────────────────
# scheduler builder
# ──────────────────────────────────────────────
def build_scheduler(
    settings: Settings,
    bridge: SupabaseBridge,
    detector: Optional[FallDetector] = None,
    detectors: Optional[dict[str, GenericYoloDetector]] = None,
) -> BlockingScheduler:
    """10분 스냅샷 + 1분 쓰러짐 + 1분 일반 감지(4종) 잡을 등록.

    detector / detectors 가 None 이면 해당 잡은 건너뜀 (개발 환경 유연성).
    """
    scheduler = BlockingScheduler(
        executors={"default": APSThreadPoolExecutor(8)},
    )
    scheduler.add_job(
        run_cycle,
        trigger=IntervalTrigger(minutes=settings.snapshot_interval_min),
        args=[bridge, settings],
        id="periodic_snapshot",
        max_instances=1,
        coalesce=True,
    )
    if detector is not None:
        scheduler.add_job(
            run_fall_cycle,
            trigger=IntervalTrigger(minutes=settings.fall_interval_min),
            args=[bridge, settings, detector],
            id="fall_detection",
            max_instances=1,
            coalesce=True,
        )
    if detectors:
        scheduler.add_job(
            run_detection_cycle,
            trigger=IntervalTrigger(minutes=settings.detectors_interval_min),
            args=[bridge, settings, detectors],
            id="general_detection",
            max_instances=1,
            coalesce=True,
        )
    return scheduler
