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
from fusion_configs import FUSION_CONFIGS
from fusion_helpers import compute_fusion_collision, compute_fusion_helmet_missing
from snapshot import SnapshotError, capture
from supabase_client import SupabaseBridge
from yolo_detector import DetectResult, GenericYoloDetector


# ─────────────────────────────────────────────────────────────────────────────
# Phase 8 RTSP-02 시연 evidence — capture 이미지에 bbox + label annotation.
# detection_events.image_url 의 capture 이미지를 detector 가 어떤 좌표를
# 검출했는지 한 눈에 보이도록 그림. 시연 슬라이드 / 운영 dashboard 양쪽에서
# detector 신뢰성을 사용자 가시화. 처리 시간 < 5ms (cv2.rectangle + putText).
#
# 색상 컨벤션 (BGR — OpenCV 기본):
#   fire     → 빨강     (0, 0, 255)
#   helmet   → 노랑     (0, 255, 255)
#   forklift → 주황     (0, 128, 255)
#   person   → 초록     (0, 255, 0)
#   fall     → 자주     (255, 0, 255)
#   FUSION   → 시안     (255, 255, 0)
#   default  → 빨강
_BBOX_COLOR_BGR = {
    "fire": (0, 0, 255),
    "helmet": (0, 255, 255),
    "hardhat_missing": (0, 255, 255),
    "forklift": (0, 128, 255),
    "person": (0, 255, 0),
    "fall": (255, 0, 255),
    "forklift_collision": (255, 255, 0),
}


# ─────────────────────────────────────────────────────────────────────────────
# 2026-05-20 architecture 정정 — RTSP 실 카메라는 모든 detector/fusion/fall 자동 적용.
# detector_configs.py 의 camera_ids 1:1 매핑은 mp4 시연 카메라 (TEST-CAM-01~05)
# 한정. 실 RTSP 카메라는 어떤 상황이 일어날지 사전에 모르므로 5종 detector 모두
# 같은 frame 검토.
# 분기 기준: cameras.live_url_detail 이 'rtsp://' 또는 'rtsps://' 로 시작하면 RTSP.
# ─────────────────────────────────────────────────────────────────────────────
def _is_rtsp_camera(camera: dict[str, Any]) -> bool:
    """cameras row 의 live_url_detail 이 RTSP URL 이면 True."""
    url = (camera.get("live_url_detail") or "").strip()
    return url.lower().startswith(("rtsp://", "rtsps://"))


def _count_rtsp_cameras(cameras: list[dict[str, Any]]) -> int:
    return sum(1 for camera in cameras if _is_rtsp_camera(camera))


def _expand_target_cameras(
    base_camera_ids: list[int],
    all_cams: list[dict[str, Any]],
    cams_by_id: dict[int, dict[str, Any]],
) -> list[dict[str, Any]]:
    """detector_configs 의 1:1 매핑된 카메라 + 모든 RTSP 카메라 (자동 포함, dedup).

    Args:
        base_camera_ids: detector_configs 또는 fusion_configs 의 'camera_ids' (mp4 시연용 매핑).
        all_cams: bridge.fetch_active_cameras() 전체 결과.
        cams_by_id: {camera_id: camera_row} dict.

    Returns:
        실행 대상 camera row 리스트 (set 기반 dedup, 안정적 순서).
    """
    target_ids: set[int] = set()
    # 1. mp4 시연 매핑 (camera_configs 의 camera_ids — 보통 단일 ID)
    for cid in base_camera_ids:
        if cid in cams_by_id:
            target_ids.add(cid)
    # 2. RTSP 카메라 자동 추가 (모든 detector/fusion/fall 5종 적용 — 운영 카메라)
    for cam in all_cams:
        if _is_rtsp_camera(cam):
            target_ids.add(int(cam["camera_id"]))
    # 정렬된 순서 — log 가독성
    return [cams_by_id[cid] for cid in sorted(target_ids) if cid in cams_by_id]


def _annotate_capture_with_bbox(
    image_path: Path,
    bboxes_with_labels: list[tuple[tuple[float, float, float, float], str, float]],
    event_key: str,
) -> bool:
    """Capture 이미지에 bbox + label 을 그려 같은 path 에 덮어쓰기.

    Args:
        image_path: scheduler 가 capture 한 local JPEG (upload 전).
        bboxes_with_labels: [((x1,y1,x2,y2), label, confidence), ...]
        event_key: 색상 매핑 키 (fire/helmet/forklift/person/fall/hardhat_missing/forklift_collision).

    Returns:
        True 면 덮어쓰기 성공, False 면 실패 (실패 시 원본 그대로 upload — 시연
        evidence 만 손해, pipeline 차단 X).
    """
    try:
        img = cv2.imread(str(image_path))
        if img is None:
            return False
        color = _BBOX_COLOR_BGR.get(event_key, (0, 0, 255))
        thickness = max(2, img.shape[1] // 500)  # 1920 width → thickness 3
        font_scale = max(0.6, img.shape[1] / 2400)
        for (x1, y1, x2, y2), label, conf in bboxes_with_labels:
            x1i, y1i, x2i, y2i = int(x1), int(y1), int(x2), int(y2)
            cv2.rectangle(img, (x1i, y1i), (x2i, y2i), color, thickness)
            text = f"{label} {conf:.2f}" if conf is not None else str(label)
            (tw, th), _ = cv2.getTextSize(
                text, cv2.FONT_HERSHEY_SIMPLEX, font_scale, thickness
            )
            # Label 배경 — 검은 직사각형 (가독성)
            label_y = max(y1i - 6, th + 4)
            cv2.rectangle(
                img,
                (x1i, label_y - th - 4),
                (x1i + tw + 4, label_y + 4),
                (0, 0, 0),
                -1,
            )
            cv2.putText(
                img, text, (x1i + 2, label_y),
                cv2.FONT_HERSHEY_SIMPLEX, font_scale, color, thickness,
                lineType=cv2.LINE_AA,
            )
        ok = cv2.imwrite(str(image_path), img)
        return bool(ok)
    except Exception:
        return False


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

# (camera_id, fusion_key) 별 최근 N 사이클 fusion rule 결과 buffer (Phase 3 / FUSION-01·02, D-06).
# - 키: (camera_id, fusion_key) — _detection_buffer 와 동일 tuple 패턴.
# - cooldown 은 _detection_cooldown 공유 — 동일 카메라의 detector/fusion 알람 동등 처리 (D-06).
# - 알람 발사 후 buffer.clear() + cooldown 갱신.
# - 1프로세스 PoC 휘발성 OK — 재시작 시 자동 reset.
_fusion_buffer: dict[tuple[int, str], deque] = {}


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
        # Phase 8 RTSP-03: capture 성공 직후 cameras.last_frame_at = now() 갱신
        # (pg_cron cameras_healthcheck() 의 진실 source — 4 detector 모두 동일 패턴).
        bridge.update_camera_health(camera_id)
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

    log.info(
        "대상 카메라 %d개 처리 시작 (RTSP=%d)",
        len(cameras),
        _count_rtsp_cameras(cameras),
    )
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
        # Phase 8 RTSP-03: capture 성공 직후 헬스체크 source 갱신 (fall detector).
        bridge.update_camera_health(camera_id)
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

    log.info(
        "fall 대상 후보: total=%d rtsp=%d configured=%s",
        len(all_cams),
        _count_rtsp_cameras(all_cams),
        list(enabled_ids),
    )

    # 2026-05-20: fall 도 RTSP 카메라 자동 포함 (architecture 정정 — RTSP 는 5종 모두).
    cams_by_id = {int(c["camera_id"]): c for c in all_cams}
    targets = _expand_target_cameras(list(enabled_ids), all_cams, cams_by_id)
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
        # Phase 8 RTSP-03: capture 성공 직후 헬스체크 source 갱신 (general detector).
        bridge.update_camera_health(camera_id)
        img = cv2.imread(str(tmp_path))
        if img is None:
            return f"[DETECT_ERR] camera_id={camera_id} event={event_key}: cv2.imread failed"

        # fire detector 같은 다중 클래스 모델은 동일 프레임에서 fire+smoke 가
        # 함께 나올 수 있으므로 detect_all() 결과를 우선 사용한다.
        # 테스트 stub 호환을 위해 detect_all 이 없으면 detect() 단건으로 폴백.
        all_results: list[DetectResult]
        detect_all_fn = getattr(detector, "detect_all", None)
        if callable(detect_all_fn):
            raw_results = detect_all_fn(img)
            if isinstance(raw_results, list):
                all_results = [r for r in raw_results if r.is_detected]
                primary_result = (
                    max(
                        all_results,
                        key=lambda r: (float(r.confidence or 0.0), float(r.inference_ms or 0.0)),
                    )
                    if all_results
                    else DetectResult(is_detected=False)
                )
            else:
                primary_result = detector.detect(img)
                all_results = [primary_result] if primary_result.is_detected else []
        else:
            primary_result = detector.detect(img)
            all_results = [primary_result] if primary_result.is_detected else []

        # Phase 2 MODEL-02: buffer push (cooldown skip 이 아닌 모든 detect 결과를 누적).
        # maxlen 은 DETECTOR_CONFIGS 의 max frames_required (현재 fire 5).
        # buffer 미존재 시 lazy 생성. is_detected=False 도 push (연속성 깨뜨리는 신호).
        frames_required = int(cfg.get("frames_required", 1))
        buffer = _detection_buffer.get(cooldown_key)
        if buffer is None:
            buffer = deque(maxlen=max(5, frames_required))
            _detection_buffer[cooldown_key] = buffer
        buffer.append(bool(all_results))

        if not all_results:
            return (
                f"[no_detect] camera_id={camera_id} event={event_key} "
                f"inf={primary_result.inference_ms:.0f}ms"
            )

        # Phase 2 MODEL-02: N 연속 검사 — 최근 N 결과가 모두 True 일 때만 알람 발사.
        # forklift/person 의 N=1 도 동일 코드 경로 (D-04, 분기 없이 즉시 통과).
        recent = list(buffer)[-frames_required:]
        if len(buffer) < frames_required or not all(recent):
            return (
                f"[no_alert_yet] camera_id={camera_id} event={event_key} "
                f"frames={sum(recent)}/{frames_required} "
                f"conf={float(primary_result.confidence or 0.0):.2f}"
            )

        # Phase 8 RTSP-02 시연 evidence — upload 직전에 bbox + label annotate.
        # all_results 의 bbox 들을 모두 그림 (예: fire + smoke 동시 검출).
        # 실패해도 silent (원본 capture 그대로 upload).
        bbox_list = [
            (r.bbox, r.label or event_key, float(r.confidence or 0.0))
            for r in all_results
            if r.bbox is not None
        ]
        if bbox_list:
            _annotate_capture_with_bbox(
                tmp_path,
                bbox_list,
                event_key,
            )

        public_url, _path = bridge.upload_detection_snapshot(
            camera_id, cfg.get("storage_prefix", event_key), tmp_path
        )
        event = bridge.register_ai_event(
            camera_id=camera_id,
            event_name=cfg["event_name"],
            risk_level=cfg["risk_level"],
            accuracy=float(primary_result.confidence or 0.0),
            image_url=public_url,
        )
        # Phase 2 MODEL-02: 알람 발사 후 buffer reset (D-02).
        # 다음 알람은 cooldown 만료 + buffer 재누적 N 연속 둘 다 충족 시에만 발생.
        buffer.clear()
        _detection_cooldown[cooldown_key] = now
        event_id = event.get("event_id")
        labels = sorted({(r.label or event_key) for r in all_results})
        labels_str = ",".join(labels)
        return (
            f"[DETECT] camera_id={camera_id} event={event_key} "
            f"labels={labels_str} conf={float(primary_result.confidence or 0.0):.2f} "
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


def _process_fusion_for_camera(
    bridge: SupabaseBridge,
    settings: Settings,
    fusion_key: str,
    cfg: dict[str, Any],
    camera: dict[str, Any],
    detectors: dict[str, "GenericYoloDetector"],
) -> str:
    """한 카메라에 대한 fusion rule 평가 + _fusion_buffer 누적 + 알람 발사.

    Phase 3 D-05. _process_detection_for_camera 와 동일한 7단계 흐름:
    1. cooldown 검사 → [fusion_skip_cooldown]
    2. snapshot capture + cv2.imread
    3. cfg['detectors_required'] 의 각 detector 에 detect_all(img) 호출
    4. rule 분기 (iou_gt / hardhat_missing) → fusion_result: bool
    5. _fusion_buffer push
    6. N 연속 검사 → [no_fusion_yet]
    7. 알람 발사: upload + register_ai_event + buffer.clear() + cooldown 갱신 → [FUSION]
    """
    camera_id = int(camera["camera_id"])
    rtsp_url = camera["live_url_detail"]

    # 1. cooldown 검사 (_detection_cooldown 공유 — D-06)
    fusion_key_tuple = (camera_id, fusion_key)
    now = time.time()
    last_ts = _detection_cooldown.get(fusion_key_tuple)
    if last_ts and (now - last_ts) < settings.detectors_cooldown_min * 60:
        return f"[fusion_skip_cooldown] camera_id={camera_id} fusion={fusion_key}"

    # 2. snapshot capture + imread (tmp_name 에 fusion_ prefix — collision 방지)
    tmp_name = f"fusion_{fusion_key}_{camera_id}_{int(now * 1000)}.jpg"
    tmp_path = settings.snapshot_tmp_dir / tmp_name
    img = None
    try:
        capture(
            rtsp_url,
            tmp_path,
            ffmpeg_bin=settings.ffmpeg_bin,
            seek_seconds=settings.detectors_demo_seek_sec,
        )
        # Phase 8 RTSP-03: capture 성공 직후 헬스체크 source 갱신 (fusion detector).
        bridge.update_camera_health(camera_id)
        img = cv2.imread(str(tmp_path))
    except Exception as exc:
        return f"[FUSION_ERR] camera_id={camera_id} fusion={fusion_key}: capture failed: {exc}"
    if img is None:
        return f"[FUSION_ERR] camera_id={camera_id} fusion={fusion_key}: cv2.imread failed"

    # 3. detect_all() for each required detector
    frame_width = max(1, img.shape[1])
    det_results: dict[str, list] = {}
    max_conf = 0.0
    for det_key in cfg["detectors_required"]:
        det = detectors.get(det_key)
        if det is None:
            log.warning(
                "[FUSION] detector '%s' not in detectors dict, skipping %s",
                det_key, fusion_key,
            )
            return (
                f"[FUSION_ERR] camera_id={camera_id} fusion={fusion_key}: "
                f"detector {det_key!r} not loaded"
            )
        results = det.detect_all(img)
        det_results[det_key] = results
        for r in results:
            if r.confidence and r.confidence > max_conf:
                max_conf = r.confidence

    # 4. rule 분기 → fusion_result (bool)
    rule = cfg.get("rule", "")
    if rule == "iou_gt":
        forklifts = det_results.get("forklift", [])
        persons   = det_results.get("person", [])
        fusion_result = compute_fusion_collision(forklifts, persons, cfg.get("threshold", 0.3))
    elif rule == "hardhat_missing":
        persons = det_results.get("person", [])
        # Filter to true helmet bboxes only — exclude "head"-labeled bboxes (unhelmeted head)
        # which would otherwise match hardhat_is_on() and suppress the alarm (D-04 checker fix).
        helmets = [
            h for h in det_results.get("helmet", [])
            if h.label and h.label.lower() in ("helmet", "hardhat")
        ]
        fusion_result = compute_fusion_helmet_missing(persons, helmets, frame_width)
        # accuracy = unmatched person conf (D-03 Claude's Discretion)
        if fusion_result and persons:
            max_conf = max((p.confidence or 0.0) for p in persons)
    else:
        return f"[FUSION_ERR] camera_id={camera_id} fusion={fusion_key}: unknown rule={rule!r}"

    # 5. _fusion_buffer push
    frames_required = int(cfg.get("frames_required", 3))
    fbuf = _fusion_buffer.get(fusion_key_tuple)
    if fbuf is None:
        fbuf = deque(maxlen=max(5, frames_required))
        _fusion_buffer[fusion_key_tuple] = fbuf
    fbuf.append(bool(fusion_result))

    if not fusion_result:
        return f"[no_fusion] camera_id={camera_id} fusion={fusion_key}"

    # 6. N 연속 검사
    recent = list(fbuf)[-frames_required:]
    if len(fbuf) < frames_required or not all(recent):
        return (
            f"[no_fusion_yet] camera_id={camera_id} fusion={fusion_key} "
            f"frames={sum(recent)}/{frames_required}"
        )

    # 7. 알람 발사
    # Phase 8 RTSP-02 시연 evidence — fusion bbox 다중 annotate (upload 직전).
    # iou_gt: forklift + person 모두 그림 / hardhat_missing: person 만 그림.
    try:
        bbox_list: list[tuple[tuple[float, float, float, float], str, float]] = []
        for det_key, results in det_results.items():
            for r in results:
                if r.bbox is not None:
                    bbox_list.append(
                        (r.bbox, r.label or det_key, r.confidence or 0.0)
                    )
        if bbox_list:
            _annotate_capture_with_bbox(tmp_path, bbox_list, fusion_key)
    except Exception:
        pass

    try:
        public_url, _ = bridge.upload_detection_snapshot(
            camera_id, cfg.get("storage_prefix", fusion_key), tmp_path
        )
        event = bridge.register_ai_event(
            camera_id=camera_id,
            event_name=cfg["event_name"],
            risk_level=cfg["risk_level"],
            accuracy=float(max_conf),
            image_url=public_url,
        )
        fbuf.clear()
        _detection_cooldown[fusion_key_tuple] = now
        event_id = event.get("event_id")
        return (
            f"[FUSION] camera_id={camera_id} fusion={fusion_key} "
            f"conf={max_conf:.2f} event_id={event_id}"
        )
    except Exception as exc:
        return f"[FUSION_ERR] camera_id={camera_id} fusion={fusion_key}: alarm failed: {exc}"
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
    log.info(
        "detection 대상 후보: total=%d rtsp=%d enabled=%s",
        len(all_cams),
        _count_rtsp_cameras(all_cams),
        list(detectors.keys()),
    )

    for event_key, detector in detectors.items():
        cfg = DETECTOR_CONFIGS.get(event_key)
        if cfg is None:
            log.warning("[%s] DETECTOR_CONFIGS 에 항목 없음, 스킵", event_key)
            continue
        if cfg.get("disabled", False):
            log.debug("[%s] disabled=True, 단독 알람 스킵 (fusion 전용, D-04)", event_key)
            continue
        # 2026-05-20: RTSP 실 카메라는 모든 detector 자동 포함 (architecture 정정).
        target_cams = _expand_target_cameras(cfg["camera_ids"], all_cams, cams_by_id)
        if not target_cams:
            log.debug(
                "[%s] 매핑 카메라 %s + RTSP 자동 포함 모두 없음", event_key, cfg["camera_ids"]
            )
            continue
        for cam in target_cams:
            msg = _process_detection_for_camera(
                bridge, settings, event_key, detector, cfg, cam
            )
            # 2026-05-20: detection diagnosis visibility — keep no_detect / no_alert_yet
            # at INFO so demo console shows whether model sees fire (per-frame) vs.
            # frames_required gate (5-consecutive). Only cooldown stays at debug.
            if msg.startswith("[detect_skip_cooldown]"):
                log.debug(msg)
            else:
                log.info(msg)

    # [Phase 3 addition] fusion loop — after detector loop (D-05, D-12)
    # 2026-05-20: RTSP 실 카메라는 모든 fusion 자동 포함 (architecture 정정).
    # 2026-05-20: fusion 의 detectors_required 가 detectors dict 에 모두 로드돼야만
    # 진행 — fire-only 같은 subset 모드에서 fusion path 가 per-camera WARNING 노이즈
    # 만 만들고 어차피 [FUSION_ERR] 로 즉시 fail 했던 것 정리.
    for fusion_key, fcfg in FUSION_CONFIGS.items():
        required = fcfg.get("detectors_required", [])
        missing = [d for d in required if d not in detectors]
        if missing:
            log.debug(
                "[FUSION] %s skipped — required detectors %s not enabled (current: %s)",
                fusion_key, missing, list(detectors.keys()),
            )
            continue
        f_target_cams = _expand_target_cameras(fcfg["camera_ids"], all_cams, cams_by_id)
        if not f_target_cams:
            log.debug(
                "[FUSION] %s: 매핑 카메라 %s + RTSP 자동 포함 모두 없음",
                fusion_key, fcfg["camera_ids"],
            )
            continue
        for cam in f_target_cams:
            fmsg = _process_fusion_for_camera(
                bridge, settings, fusion_key, fcfg, cam, detectors
            )
            if fmsg.startswith(("[no_fusion]", "[fusion_skip_cooldown]", "[no_fusion_yet]")):
                log.debug(fmsg)
            else:
                log.info(fmsg)
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
