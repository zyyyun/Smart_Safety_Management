"""Phase 2 MODEL-02 — _process_detection_for_camera 의 frames_required buffer 동작 테스트.

D-03 (CONTEXT.md): pytest unit test (mocked detector + bridge + capture).
시퀀스 입력별 알람 발사 횟수 검증. cooldown 상호작용 포함.
"""

from __future__ import annotations

import sys
import time
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

# ai_agent 패키지 import 보장 (cwd 가 프로젝트 루트일 때).
AGENT_DIR = Path(__file__).resolve().parents[1]
if str(AGENT_DIR) not in sys.path:
    sys.path.insert(0, str(AGENT_DIR))

import scheduler  # noqa: E402  (sys.path 조정 후)


# ──────────────────────────────────────────────
# fixtures
# ──────────────────────────────────────────────
@pytest.fixture(autouse=True)
def reset_module_state():
    """매 테스트마다 _detection_buffer / _detection_cooldown 초기화."""
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    yield
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()


@pytest.fixture
def stub_external(monkeypatch, tmp_path):
    """capture / cv2.imread 를 no-op stub 으로 대체.

    capture() 는 tmp_path 에 빈 jpg 를 만들지 않아도 cv2.imread 를 stub 했으므로 OK.
    """
    monkeypatch.setattr(scheduler, "capture", lambda *a, **kw: None)

    fake_img = MagicMock(name="fake_bgr_ndarray")
    monkeypatch.setattr(scheduler.cv2, "imread", lambda _path: fake_img)
    return fake_img


def make_settings(tmp_path):
    return SimpleNamespace(
        snapshot_tmp_dir=tmp_path,
        ffmpeg_bin="ffmpeg",
        detectors_demo_seek_sec=0.0,
        detectors_cooldown_min=10,
    )


def make_bridge():
    """register_ai_event / upload_detection_snapshot mock 로 호출 횟수 카운트."""
    bridge = MagicMock(name="bridge")
    bridge.upload_detection_snapshot.return_value = ("https://example.test/snap.jpg", "obj/path")
    bridge.register_ai_event.return_value = {"event_id": 999}
    return bridge


def make_detector(sequence):
    """sequence 에서 한 번에 하나씩 is_detected 를 꺼내는 detector mock."""
    iterator = iter(sequence)

    def _detect(_img):
        try:
            flag = next(iterator)
        except StopIteration:
            flag = False
        return SimpleNamespace(
            is_detected=flag,
            confidence=0.42 if flag else None,
            bbox=None,
            label="x" if flag else None,
            inference_ms=12.3,
        )

    detector = MagicMock(name="detector")
    detector.detect.side_effect = _detect
    return detector


def fire_cfg():
    return {
        "event_name": "화재",
        "risk_level": "DANGER",
        "frames_required": 5,
        "storage_prefix": "fire",
    }


def helmet_cfg():
    return {
        "event_name": "안전모 미착용",
        "risk_level": "WARNING",
        "frames_required": 3,
        "storage_prefix": "helmet",
    }


def forklift_cfg():
    return {
        "event_name": "지게차 진입",
        "risk_level": "WARNING",
        "frames_required": 1,
        "storage_prefix": "forklift",
    }


CAM = {"camera_id": 1, "live_url_detail": "rtsp://stub"}


def run_n_cycles(n, *, bridge, settings, event_key, detector, cfg):
    results = []
    for _ in range(n):
        msg = scheduler._process_detection_for_camera(
            bridge, settings, event_key, detector, cfg, CAM
        )
        results.append(msg)
    return results


# ──────────────────────────────────────────────
# tests — D-03 5 시퀀스 + 추가 3개
# ──────────────────────────────────────────────
def test_n5_consecutive_true_fires_one_alert(stub_external, tmp_path):
    bridge = make_bridge()
    detector = make_detector([True, True, True, True, True])
    msgs = run_n_cycles(
        5, bridge=bridge, settings=make_settings(tmp_path),
        event_key="fire", detector=detector, cfg=fire_cfg(),
    )
    assert bridge.register_ai_event.call_count == 1
    assert msgs[-1].startswith("[DETECT]"), msgs
    # 첫 4 cycle 은 [no_alert_yet]
    for m in msgs[:4]:
        assert m.startswith("[no_alert_yet]"), m


def test_n5_breaks_no_alert(stub_external, tmp_path):
    bridge = make_bridge()
    detector = make_detector([True, True, True, True, False])
    msgs = run_n_cycles(
        5, bridge=bridge, settings=make_settings(tmp_path),
        event_key="fire", detector=detector, cfg=fire_cfg(),
    )
    assert bridge.register_ai_event.call_count == 0
    assert msgs[-1].startswith("[no_detect]"), msgs


def test_n5_late_consecutive_fires_alert(stub_external, tmp_path):
    """[F,T,T,T,T,T] — last 5 모두 True 면 6번째 cycle 에 알람 발사."""
    bridge = make_bridge()
    detector = make_detector([False, True, True, True, True, True])
    msgs = run_n_cycles(
        6, bridge=bridge, settings=make_settings(tmp_path),
        event_key="fire", detector=detector, cfg=fire_cfg(),
    )
    assert bridge.register_ai_event.call_count == 1
    assert msgs[-1].startswith("[DETECT]"), msgs


def test_n5_post_alert_clear_then_re_accumulate(stub_external, tmp_path, monkeypatch):
    """알람 발사 후 buffer.clear() — 직후 [T] 단일 cycle 은 알람 0회.

    cooldown 도 갱신되므로 cooldown 우회 위해 detectors_cooldown_min=0 설정.
    """
    bridge = make_bridge()
    detector = make_detector([True] * 6)
    settings = SimpleNamespace(
        snapshot_tmp_dir=tmp_path,
        ffmpeg_bin="ffmpeg",
        detectors_demo_seek_sec=0.0,
        detectors_cooldown_min=0,  # cooldown 우회
    )
    msgs = run_n_cycles(
        6, bridge=bridge, settings=settings,
        event_key="fire", detector=detector, cfg=fire_cfg(),
    )
    # 5번째 cycle 에 1회 알람 + clear → 6번째 cycle 은 buffer=[True], len<5 → no_alert_yet
    assert bridge.register_ai_event.call_count == 1
    assert msgs[4].startswith("[DETECT]"), msgs
    assert msgs[5].startswith("[no_alert_yet]"), msgs[5]


def test_n1_forklift_fires_immediately(stub_external, tmp_path):
    bridge = make_bridge()
    detector = make_detector([True])
    msgs = run_n_cycles(
        1, bridge=bridge, settings=make_settings(tmp_path),
        event_key="forklift", detector=detector, cfg=forklift_cfg(),
    )
    assert bridge.register_ai_event.call_count == 1
    assert msgs[0].startswith("[DETECT]"), msgs


def test_n1_no_detect_no_alert(stub_external, tmp_path):
    bridge = make_bridge()
    detector = make_detector([False])
    msgs = run_n_cycles(
        1, bridge=bridge, settings=make_settings(tmp_path),
        event_key="forklift", detector=detector, cfg=forklift_cfg(),
    )
    assert bridge.register_ai_event.call_count == 0
    assert msgs[0].startswith("[no_detect]"), msgs


def test_cooldown_active_skips_buffer_push(stub_external, tmp_path):
    """cooldown 활성 cycle 에서는 detect 실행조차 안 함 → buffer push 없음 (D-07)."""
    bridge = make_bridge()
    detector = make_detector([True, True])  # 두 번째 호출은 일어나지 않아야 함
    settings = make_settings(tmp_path)

    # 사전 cooldown 설정 — 방금 알람 발사한 것처럼 위장
    scheduler._detection_cooldown[(1, "fire")] = time.time()

    msg = scheduler._process_detection_for_camera(
        bridge, settings, "fire", detector, fire_cfg(), CAM,
    )
    assert msg.startswith("[detect_skip_cooldown]"), msg
    # detector.detect 는 호출되지 않아야 함
    assert detector.detect.call_count == 0
    # buffer 도 비어 있어야 함
    assert (1, "fire") not in scheduler._detection_buffer or len(scheduler._detection_buffer[(1, "fire")]) == 0
    # bridge 호출 없음
    assert bridge.register_ai_event.call_count == 0


def test_n3_helmet_consecutive(stub_external, tmp_path):
    bridge = make_bridge()
    detector = make_detector([True, True, True])
    msgs = run_n_cycles(
        3, bridge=bridge, settings=make_settings(tmp_path),
        event_key="helmet", detector=detector, cfg=helmet_cfg(),
    )
    assert bridge.register_ai_event.call_count == 1
    assert msgs[-1].startswith("[DETECT]"), msgs
    # 첫 2 cycle 은 [no_alert_yet]
    assert msgs[0].startswith("[no_alert_yet]"), msgs[0]
    assert msgs[1].startswith("[no_alert_yet]"), msgs[1]
