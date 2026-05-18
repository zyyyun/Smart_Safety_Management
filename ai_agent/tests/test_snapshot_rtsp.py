"""Phase 8 RTSP-01·03 — snapshot.capture_rtsp + URL scheme 분기 wrapper 단위 검증.

D-01·02 (08-CONTEXT.md): drift_test.py 검증 패턴 (cv2.VideoCapture(CAP_FFMPEG) +
2초 sleep + cap.isOpened()+frame is not None 가드 + 3회 retry + 시도 사이 2초 wait).

6 cases:
1. test_rtsp_success_first_attempt — 첫 시도 성공, sleep 1회 (handshake), read 1회
2. test_rtsp_retry_then_success — [fail, fail, success], sleep 5회 (handshake×3 + retry×2)
3. test_rtsp_three_failures_raises — 3회 모두 fail → SnapshotError raise
4. test_rtsp_isOpened_false_treated_as_fail — isOpened=False → SnapshotError (drift_test 가드)
5. test_capture_dispatch_rtsp — capture("rtsp://...") → capture_rtsp 호출, _capture_ffmpeg 미호출
6. test_capture_dispatch_mp4 — capture("https://.../mp4") → _capture_ffmpeg 호출, capture_rtsp 미호출
"""

from __future__ import annotations

import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

# ai_agent 패키지 import 보장 (test_scheduler_buffer.py 패턴 미러).
AGENT_DIR = Path(__file__).resolve().parents[1]
if str(AGENT_DIR) not in sys.path:
    sys.path.insert(0, str(AGENT_DIR))

import snapshot  # noqa: E402  (sys.path 조정 후)


# ──────────────────────────────────────────────
# fixtures / factories
# ──────────────────────────────────────────────
@pytest.fixture
def fake_frame():
    """numpy ndarray 흉내 — cv2.imwrite 가 받기만 하면 OK."""
    arr = MagicMock(name="bgr_ndarray")
    arr.shape = (720, 1280, 3)
    return arr


def _make_cap(read_outcomes: list, is_opened: bool = True):
    """cv2.VideoCapture mock factory.

    read_outcomes: list of (ret, frame) tuples — 호출 순서대로 반환.
    """
    cap = MagicMock(name="VideoCapture")
    cap.isOpened.return_value = is_opened
    cap.read.side_effect = read_outcomes
    return cap


# ──────────────────────────────────────────────
# capture_rtsp() — drift_test.py 검증 패턴
# ──────────────────────────────────────────────
def test_rtsp_success_first_attempt(monkeypatch, fake_frame, tmp_path):
    """첫 시도 ret=True + frame OK + isOpened=True → return 즉시.

    sleep × 1회 (handshake). retry sleep 없음. cap.read 1회.
    """
    cap = _make_cap([(True, fake_frame)])
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: cap)
    monkeypatch.setattr(snapshot.cv2, "imwrite", lambda p, f: True)
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    out = snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=3)

    assert out == tmp_path / "out.jpg"
    assert snapshot.time.sleep.call_count == 1  # handshake only
    assert cap.read.call_count == 1
    assert cap.release.called


def test_rtsp_retry_then_success(monkeypatch, fake_frame, tmp_path):
    """[fail, fail, success] → 3번째 시도에서 return.

    handshake×3 + retry×2 = sleep 5회.
    """
    caps = [
        _make_cap([(False, None)]),
        _make_cap([(False, None)]),
        _make_cap([(True, fake_frame)]),
    ]
    cap_iter = iter(caps)
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: next(cap_iter))
    monkeypatch.setattr(snapshot.cv2, "imwrite", lambda p, f: True)
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    out = snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=3)

    assert out == tmp_path / "out.jpg"
    assert snapshot.time.sleep.call_count == 5  # 3 handshake + 2 retry-wait
    # 각 cap 모두 release 호출 (try/finally 가드)
    for cap in caps:
        assert cap.release.called


def test_rtsp_three_failures_raises(monkeypatch, tmp_path):
    """3회 모두 ret=False → SnapshotError raise (message 에 'failed after 3 attempts')."""
    caps = [_make_cap([(False, None)]) for _ in range(3)]
    cap_iter = iter(caps)
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: next(cap_iter))
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    with pytest.raises(snapshot.SnapshotError, match="failed after 3 attempts"):
        snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=3)


def test_rtsp_isOpened_false_treated_as_fail(monkeypatch, fake_frame, tmp_path):
    """ret=True + frame OK 지만 cap.isOpened()=False → 실패 취급 (drift_test.py 가드).

    max_attempts=1 로 한 번에 SnapshotError raise 검증.
    """
    cap = _make_cap([(True, fake_frame)], is_opened=False)
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: cap)
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    with pytest.raises(snapshot.SnapshotError):
        snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=1)
    assert cap.release.called


# ──────────────────────────────────────────────
# capture() wrapper — URL scheme 분기 (D-01)
# ──────────────────────────────────────────────
def test_capture_dispatch_rtsp(monkeypatch, tmp_path):
    """capture() wrapper 가 rtsp:// → capture_rtsp() 호출, _capture_ffmpeg 미호출."""
    rtsp_called = MagicMock(return_value=tmp_path / "out.jpg")
    ffmpeg_called = MagicMock()
    monkeypatch.setattr(snapshot, "capture_rtsp", rtsp_called)
    monkeypatch.setattr(snapshot, "_capture_ffmpeg", ffmpeg_called)

    snapshot.capture("rtsp://cam/live", tmp_path / "out.jpg")

    assert rtsp_called.called
    assert not ffmpeg_called.called


def test_capture_dispatch_mp4(monkeypatch, tmp_path):
    """capture() wrapper 가 file/mp4 → 기존 _capture_ffmpeg() 호출, capture_rtsp 미호출."""
    rtsp_called = MagicMock()
    ffmpeg_called = MagicMock(return_value=tmp_path / "out.jpg")
    monkeypatch.setattr(snapshot, "capture_rtsp", rtsp_called)
    monkeypatch.setattr(snapshot, "_capture_ffmpeg", ffmpeg_called)

    snapshot.capture("https://storage.example/fire/source_v2.mp4", tmp_path / "out.jpg")

    assert ffmpeg_called.called
    assert not rtsp_called.called
