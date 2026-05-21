"""RTSP PoC: Supabase Storage 청크 다운로드 → YOLO 추론 → detection_events INSERT.

design doc : .planning/explorations/2026-05-21_rtsp_mobile_relay_architecture.md (Approach 5)
plan       : ~/.claude/plans/feature-rtps-test-shimmering-fiddle.md (v3.1)

흐름:
    1. supabase.storage.from_('rtsp-poc').list('cam{id}/') 객체 list
    2. .rtsp_poc_state.json 의 last_processed_key (folder 별) 이후 객체만 iterate
    3. JPEG download → cv2.imdecode → GenericYoloDetector.detect()
    4. is_detected + conf ≥ threshold 면:
         a. 표준 camera-captures bucket 으로 upload_detection_snapshot (image_url 받음)
         b. register_ai_event() → detection_events + camera_captures + notifications + FCM
    5. (--delete-after, 기본 ON) 처리된 rtsp-poc 객체 storage 에서 delete
    6. state.json 의 last_processed_key 업데이트 (atomic write)

실행 (ai_agent 가 flat namespace 라 cd 후 직접 실행):
    cd ai_agent
    python rtsp_poc_pull.py --camera 1                     # 1회 처리
    python rtsp_poc_pull.py --camera 1 --watch             # 5초 cycle 무한 반복
    python rtsp_poc_pull.py --camera 1 --no-delete-after   # storage 보존 (디버깅)
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import tempfile
import time
from pathlib import Path
from typing import Any

import cv2
import numpy as np

from config import load_settings
from detector_configs import DETECTOR_CONFIGS
from supabase_client import SupabaseBridge
from yolo_detector import GenericYoloDetector


log = logging.getLogger("rtsp_poc_pull")


BUCKET_DEFAULT = "rtsp-poc"
STATE_FILE_DEFAULT = ".rtsp_poc_state.json"
WATCH_INTERVAL_SEC = 5.0


# ────────────────────────────────────────
# state file (last processed object key per camera folder)
# ────────────────────────────────────────
def _load_state(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        log.warning("state file %s 손상, 빈 state 로 시작: %s", path, e)
        return {}


def _save_state(path: Path, state: dict[str, str]) -> None:
    """Atomic write — .tmp 에 쓰고 rename. 동시 실행 시 마지막 writer 가 이김."""
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8")
    tmp.replace(path)


# ────────────────────────────────────────
# detector (PoC 는 person 만)
# ────────────────────────────────────────
def _build_person_detector() -> GenericYoloDetector:
    """DETECTOR_CONFIGS['person'] 의 설정 그대로 person detector 생성."""
    cfg = DETECTOR_CONFIGS["person"]
    return GenericYoloDetector(
        name="person",
        weights_path=cfg["weights"],
        conf_thres=cfg["conf_thres"],
        iou_thres=cfg["iou_thres"],
        img_size=cfg["img_size"],
        target_classes=cfg.get("target_classes"),
        framework=cfg.get("framework", "yolov8"),
    )


# ────────────────────────────────────────
# core pull loop
# ────────────────────────────────────────
def _pull_one_camera(
    bridge: SupabaseBridge,
    bucket: str,
    camera_id: int,
    state: dict[str, str],
    detector: GenericYoloDetector,
    detector_cfg: dict[str, Any],
    delete_after: bool,
) -> dict[str, int]:
    """단일 카메라 폴더의 신규 JPEG 들을 처리.

    state[folder] 에 마지막 처리 object name 을 in-place 갱신 (caller 가 _save_state).
    """
    folder = f"cam{camera_id}"
    last_key = state.get(folder, "")

    # bridge 가 storage public method 를 노출 안 하니 _client 직접 사용.
    # plan v3.1 의 "ai_agent/supabase_client.py 변경 없음" 항목 보존.
    client = bridge._client  # type: ignore[attr-defined]

    try:
        objects = client.storage.from_(bucket).list(
            folder,
            {"limit": 100, "sortBy": {"column": "name", "order": "asc"}},
        )
    except Exception as e:
        log.error("storage list 실패 bucket=%s folder=%s: %s", bucket, folder, e)
        return {"processed": 0, "events": 0}

    if not objects:
        log.info("camera_id=%s: 객체 0개", camera_id)
        return {"processed": 0, "events": 0}

    # object dict 양식: {'name': '1716280123456.jpg', 'id': '...', 'created_at': '...', ...}
    new_objects = [o for o in objects if o.get("name", "") > last_key]
    log.info(
        "camera_id=%s: list %d, 신규 %d (last=%s)",
        camera_id,
        len(objects),
        len(new_objects),
        last_key or "<none>",
    )

    processed = 0
    events = 0
    for obj in new_objects:
        name = obj.get("name", "")
        if not name or not name.endswith(".jpg"):
            continue
        path_in_bucket = f"{folder}/{name}"

        try:
            jpeg_bytes = client.storage.from_(bucket).download(path_in_bucket)
        except Exception as e:
            log.warning("download 실패 %s: %s", path_in_bucket, e)
            continue
        if not jpeg_bytes:
            log.warning("download 빈 응답 %s", path_in_bucket)
            continue

        # JPEG bytes → BGR np.ndarray (snapshot.py 와 같은 입력 양식)
        arr = np.frombuffer(jpeg_bytes, dtype=np.uint8)
        image_bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if image_bgr is None or image_bgr.size == 0:
            log.warning("cv2.imdecode 실패 %s", path_in_bucket)
            state[folder] = name  # 손상 객체는 건너뛰고 다음 cycle 에 다시 안 보게
            processed += 1
            if delete_after:
                _try_delete(client, bucket, path_in_bucket)
            continue

        # YOLO 추론
        result = detector.detect(image_bgr)
        is_hit = (
            result.is_detected
            and result.confidence is not None
            and result.confidence >= detector_cfg["conf_thres"]
        )
        log.info(
            "camera_id=%s name=%s detected=%s conf=%.3f label=%s hit=%s (%.1fms)",
            camera_id,
            name,
            result.is_detected,
            result.confidence or 0.0,
            result.label or "-",
            is_hit,
            result.inference_ms,
        )

        if is_hit:
            # detection 시 표준 흐름 — 같은 JPEG bytes 를 camera-captures bucket
            # 으로 upload_detection_snapshot (기존 snapshot.py / scheduler.py 흐름)
            # 후 register_ai_event 가 detection_events + camera_captures +
            # notifications + FCM 까지 처리.
            try:
                with tempfile.NamedTemporaryFile(
                    suffix=".jpg", delete=False, prefix=f"rtsp_poc_{camera_id}_"
                ) as tmp:
                    tmp.write(jpeg_bytes)
                    tmp_path = Path(tmp.name)
                try:
                    image_url, _storage_path = bridge.upload_detection_snapshot(
                        camera_id=camera_id,
                        event_key=detector_cfg["storage_prefix"],
                        local_path=tmp_path,
                    )
                    bridge.register_ai_event(
                        camera_id=camera_id,
                        event_name=detector_cfg["event_name"],
                        risk_level=detector_cfg["risk_level"],
                        accuracy=float(result.confidence or 0.0),
                        image_url=image_url,
                    )
                    events += 1
                    log.info(
                        "AI event 등록: camera_id=%s conf=%.3f event=%s url=%s",
                        camera_id,
                        result.confidence,
                        detector_cfg["event_name"],
                        image_url,
                    )
                finally:
                    tmp_path.unlink(missing_ok=True)
            except Exception as e:
                log.error("AI event 등록 실패 camera_id=%s: %s", camera_id, e)
                # event 등록 실패해도 state 는 진행 (재처리 안 함). 사용자가 logcat 보고 결정.

        processed += 1
        state[folder] = name  # 최신 처리 key 갱신 (성공/실패 무관, 진행)

        if delete_after:
            _try_delete(client, bucket, path_in_bucket)

    return {"processed": processed, "events": events}


def _try_delete(client: Any, bucket: str, path_in_bucket: str) -> None:
    try:
        client.storage.from_(bucket).remove([path_in_bucket])
    except Exception as e:
        log.warning("storage delete 실패 %s: %s", path_in_bucket, e)


# ────────────────────────────────────────
# CLI
# ────────────────────────────────────────
def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "RTSP PoC: rtsp-poc bucket 의 청크를 다운로드해 YOLO 추론 후 "
            "detection_events 로 등록."
        )
    )
    parser.add_argument(
        "--camera",
        type=int,
        action="append",
        required=True,
        help="처리할 camera_id (반복 가능 — 예: --camera 1 --camera 2)",
    )
    parser.add_argument(
        "--detectors",
        default="person",
        help="쉼표 구분 detector list (현재 person 만 지원)",
    )
    parser.add_argument("--bucket", default=BUCKET_DEFAULT)
    parser.add_argument("--state-file", default=STATE_FILE_DEFAULT)
    parser.add_argument(
        "--delete-after",
        action="store_true",
        default=True,
        help="처리된 rtsp-poc 객체 즉시 delete (PoC 기본 ON)",
    )
    parser.add_argument(
        "--no-delete-after",
        dest="delete_after",
        action="store_false",
        help="storage 보존 (디버깅 용)",
    )
    parser.add_argument(
        "--watch",
        action="store_true",
        help=f"무한 반복 ({WATCH_INTERVAL_SEC:g}초 cycle). Ctrl+C 로 종료.",
    )
    parser.add_argument("--log-level", default="INFO")
    args = parser.parse_args()

    logging.basicConfig(
        level=getattr(logging, args.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    detectors = [d.strip() for d in args.detectors.split(",") if d.strip()]
    if detectors != ["person"]:
        log.warning(
            "현재 PoC 는 person detector 만 지원. 요청: %s → person 으로 폴백.",
            detectors,
        )

    settings = load_settings()
    bridge = SupabaseBridge(settings)

    detector = _build_person_detector()
    detector_cfg = DETECTOR_CONFIGS["person"]

    state_path = Path(args.state_file)
    if not state_path.is_absolute():
        # `cd ai_agent && python rtsp_poc_pull.py` 실행 가정 — cwd 기준.
        state_path = Path.cwd() / args.state_file

    log.info(
        "start: cameras=%s bucket=%s state=%s delete_after=%s watch=%s",
        args.camera,
        args.bucket,
        state_path,
        args.delete_after,
        args.watch,
    )

    def _one_pass() -> dict[str, int]:
        state = _load_state(state_path)
        total = {"processed": 0, "events": 0}
        for cam_id in args.camera:
            result = _pull_one_camera(
                bridge=bridge,
                bucket=args.bucket,
                camera_id=cam_id,
                state=state,
                detector=detector,
                detector_cfg=detector_cfg,
                delete_after=args.delete_after,
            )
            total["processed"] += result["processed"]
            total["events"] += result["events"]
        _save_state(state_path, state)
        log.info(
            "pass 완료: processed=%s events=%s state=%s",
            total["processed"],
            total["events"],
            state_path,
        )
        return total

    try:
        if args.watch:
            log.info("watch mode — %.1fs cycle 무한 반복 (Ctrl+C 종료)", WATCH_INTERVAL_SEC)
            while True:
                _one_pass()
                time.sleep(WATCH_INTERVAL_SEC)
        else:
            _one_pass()
        return 0
    except KeyboardInterrupt:
        log.info("종료 (KeyboardInterrupt)")
        return 0
    finally:
        bridge.close()


if __name__ == "__main__":
    sys.exit(main())
