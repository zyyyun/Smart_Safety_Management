"""RTSP 스트림 또는 영상 파일에서 단일 프레임을 JPEG로 추출.

Phase 8 (D-01·02): URL scheme 으로 자동 분기.
- ``rtsp://`` / ``rtsps://`` → :func:`capture_rtsp` (cv2.VideoCapture + CAP_FFMPEG,
  drift_test.py 검증 패턴)
- 그 외 (mp4·http·file·local path) → :func:`_capture_ffmpeg` (기존 ffmpeg subprocess)

기존 ``capture()`` 시그니처/동작은 보존 — scheduler.py 의 4 detector 진입점 무변경.
SC #4 (mp4 demo + RTSP 운영 동일 detector 코드) 충족.

기존 레거시(server/cron_scheduler.js)의 FFmpeg 옵션을 그대로 이식.
- analyzeduration / probesize 축소로 연결 지연 감소
- -frames:v 1  : 1프레임만 추출
- -q:v 2       : JPEG 품질 상위
- -update 1    : 실시간 갱신 모드
"""

from __future__ import annotations

import logging
import os
import subprocess
import time
from pathlib import Path

import cv2

# Pitfall 6 (08-RESEARCH.md): OpenCV FFMPEG backend 의 rtsp_transport 기본값이
# 환경/버전에 따라 UDP 일 수 있음 → 방화벽/패킷 손실 시 끊김 잦음. TCP 강제.
# setdefault 라 외부 환경변수가 있으면 그쪽 우선.
os.environ.setdefault("OPENCV_FFMPEG_CAPTURE_OPTIONS", "rtsp_transport;tcp")


log = logging.getLogger(__name__)

# drift_test.py try_connect 패턴: 시도 사이 2초 wait + handshake 직후 2초 wait.
# D-02 (08-CONTEXT.md, amended 2026-05-15): exponential 대신 단순 고정 sleep 채택.
BACKOFF_SEC = 2


class SnapshotError(RuntimeError):
    """FFmpeg/cv2 실행 오류 또는 출력 파일 생성 실패."""


# ──────────────────────────────────────────────────────────────────────────────
# RTSP — cv2.VideoCapture + CAP_FFMPEG (D-01, drift_test.py 검증 패턴)
# ──────────────────────────────────────────────────────────────────────────────
def capture_rtsp(
    url: str,
    output_path: Path | str,
    *,
    max_attempts: int = 3,
) -> Path:
    """RTSP 스트림에서 프레임 한 장을 캡처해 ``output_path`` 에 저장.

    drift_test.py (사용자 환경 검증) 의 try_connect 패턴 채택:

    1. ``cv2.VideoCapture(url, cv2.CAP_FFMPEG)`` 로 핸들 오픈
    2. ``cap.set(CAP_PROP_BUFFERSIZE, 1)`` — stale frame 방지 (latest 만)
    3. ``time.sleep(BACKOFF_SEC)`` — RTSP handshake 완료 대기
    4. ``cap.read()`` 호출 후 **3-가드** 검사: ``ret`` ∧ ``cap.isOpened()`` ∧
       ``frame is not None``
    5. 실패 시 release + ``time.sleep(BACKOFF_SEC)`` retry (총 max_attempts 회)
    6. 3회 모두 실패하면 :class:`SnapshotError` raise

    Parameters
    ----------
    url :
        ``rtsp://...`` 또는 ``rtsps://...``
    output_path :
        프레임을 저장할 파일 경로 (디렉터리 자동 생성)
    max_attempts :
        재시도 횟수 — default 3 (D-02)

    Raises
    ------
    SnapshotError
        max_attempts 회 모두 핸드셰이크 실패 또는 cv2.imwrite 실패.
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    last_err: str | None = None

    for attempt in range(max_attempts):
        cap = cv2.VideoCapture(url, cv2.CAP_FFMPEG)
        try:
            # Latest frame only — long-lived 가 아닌 1-shot capture 이므로 stale 회피.
            # (Pitfall 6 의 backend 별 무시 가능성은 RESEARCH §Pitfall 6 참조 — TCP 강제로 보완)
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            time.sleep(BACKOFF_SEC)  # RTSP handshake 대기 (drift_test.py 검증)

            ret, frame = cap.read()
            if ret and cap.isOpened() and frame is not None:
                ok = cv2.imwrite(str(output_path), frame)
                if ok:
                    log.debug(
                        "capture_rtsp OK attempt=%d url=%s out=%s",
                        attempt + 1, url, output_path,
                    )
                    return output_path
                last_err = "cv2.imwrite returned False"
            else:
                last_err = (
                    f"cap.read ret={ret} isOpened={cap.isOpened()} "
                    f"frame_is_None={frame is None}"
                )
        finally:
            cap.release()

        # 마지막 시도 후엔 wait 하지 않음 (즉시 raise).
        if attempt < max_attempts - 1:
            log.debug(
                "capture_rtsp retry attempt=%d/%d url=%s err=%s",
                attempt + 1, max_attempts, url, last_err,
            )
            time.sleep(BACKOFF_SEC)  # 시도 사이 wait (D-02)

    raise SnapshotError(
        f"RTSP capture failed after {max_attempts} attempts "
        f"(url={url}): {last_err}"
    )


# ──────────────────────────────────────────────────────────────────────────────
# 파일/HTTP/mp4 — 기존 ffmpeg subprocess (Phase 1·2·3 호환, 본문 무변경)
# ──────────────────────────────────────────────────────────────────────────────
def _capture_ffmpeg(
    rtsp_url: str,
    output_path: Path | str,
    *,
    ffmpeg_bin: str = "ffmpeg",
    timeout_sec: int = 30,
    seek_seconds: float | None = None,
) -> Path:
    """ffmpeg subprocess 로 파일/HTTP/mp4 또는 RTSP 에서 프레임 한 장을 캡처.

    Phase 1·2·3 에서 사용하던 capture() 본문을 그대로 옮긴 internal helper.
    SC #4 충족 위해 mp4 fallback 호환 유지.

    Parameters
    ----------
    seek_seconds :
        입력이 파일(로컬 또는 HTTP 영상)일 때, 이 초 지점으로 input seek 후 1프레임 추출.
        레퍼런스 MP4 데모 환경에서 특정 시점(쓰러짐 순간 등)을 잡기 위한 플래그.
        RTSP 라이브 스트림에서는 의미 없으므로 무시해도 무방하지만, -ss 자체는
        허용되므로 넣어도 에러 없음.

    Raises
    ------
    SnapshotError
        ffmpeg 실행 실패 또는 출력 파일이 생성되지 않은 경우.
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    is_rtsp = rtsp_url.lower().startswith(("rtsp://", "rtsps://"))

    cmd: list[str] = [ffmpeg_bin, "-y"]
    # Input seek — -i 앞에 둬야 빠른 seek (key frame 기준)
    if seek_seconds is not None and seek_seconds > 0:
        cmd.extend(["-ss", f"{seek_seconds}"])
    # RTSP 전용 옵션 (로컬 파일/HTTP에는 지원되지 않음)
    if is_rtsp:
        cmd.extend(["-rtsp_transport", "tcp"])
    cmd.extend([
        "-analyzeduration", "1000000",
        "-probesize", "1000000",
        "-i", rtsp_url,
        "-frames:v", "1",
        "-q:v", "2",
        "-update", "1",
        str(output_path),
    ])

    log.debug("FFmpeg exec: %s", " ".join(cmd))

    try:
        # bytes 모드로 돌려 Windows cp949 등 locale 인코딩 이슈 회피.
        result = subprocess.run(
            cmd,
            capture_output=True,
            timeout=timeout_sec,
            check=False,
        )
    except subprocess.TimeoutExpired as e:
        raise SnapshotError(f"FFmpeg timeout after {timeout_sec}s: {rtsp_url}") from e
    except FileNotFoundError as e:
        raise SnapshotError(
            f"FFmpeg 실행 파일을 찾을 수 없습니다 (bin={ffmpeg_bin}). PATH 또는 FFMPEG_BIN 설정 확인."
        ) from e

    if result.returncode != 0:
        stderr_text = result.stderr.decode("utf-8", errors="replace") if result.stderr else ""
        stderr_tail = "\n".join(stderr_text.splitlines()[-20:])
        raise SnapshotError(
            f"FFmpeg 종료 코드 {result.returncode}, url={rtsp_url}\n{stderr_tail}"
        )

    if not output_path.exists() or output_path.stat().st_size == 0:
        raise SnapshotError(f"FFmpeg 성공했지만 출력 파일 없음/빈 파일: {output_path}")

    return output_path


# ──────────────────────────────────────────────────────────────────────────────
# 외부 진입점 — URL scheme 분기 wrapper (D-01)
# ──────────────────────────────────────────────────────────────────────────────
def capture(
    url: str,
    output_path: Path | str,
    *,
    ffmpeg_bin: str = "ffmpeg",
    timeout_sec: int = 30,
    seek_seconds: float | None = None,
) -> Path:
    """URL scheme 으로 RTSP / mp4 fallback 자동 분기 (Phase 8 D-01).

    - ``rtsp://...`` / ``rtsps://...`` → :func:`capture_rtsp` (cv2.VideoCapture)
      ``seek_seconds`` / ``timeout_sec`` / ``ffmpeg_bin`` 은 RTSP 라이브에 의미 없으므로
      무시 (시그니처 호환 위해 받기만 함).
    - 그 외 → :func:`_capture_ffmpeg` (기존 ffmpeg subprocess, mp4 데모 호환)

    scheduler.py 의 4 detector 진입점 (line 78/139/235/335) 의 ``capture(rtsp_url,
    tmp_path, ffmpeg_bin=settings.ffmpeg_bin[, seek_seconds=...])`` 호출 무변경.
    """
    if url.lower().startswith(("rtsp://", "rtsps://")):
        return capture_rtsp(url, output_path)
    return _capture_ffmpeg(
        url,
        output_path,
        ffmpeg_bin=ffmpeg_bin,
        timeout_sec=timeout_sec,
        seek_seconds=seek_seconds,
    )
