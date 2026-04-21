"""RTSP 스트림에서 단일 프레임을 JPEG로 추출.

기존 레거시(server/cron_scheduler.js)의 FFmpeg 옵션을 그대로 이식.
- analyzeduration / probesize 축소로 연결 지연 감소
- -frames:v 1  : 1프레임만 추출
- -q:v 2       : JPEG 품질 상위
- -update 1    : 실시간 갱신 모드
"""

from __future__ import annotations

import logging
import subprocess
from pathlib import Path


log = logging.getLogger(__name__)


class SnapshotError(RuntimeError):
    """FFmpeg 실행/반환 오류."""


def capture(
    rtsp_url: str,
    output_path: Path | str,
    *,
    ffmpeg_bin: str = "ffmpeg",
    timeout_sec: int = 30,
) -> Path:
    """RTSP 스트림에서 프레임 하나를 캡처해 output_path에 저장.

    Raises:
        SnapshotError: ffmpeg 실행 실패 또는 출력 파일이 생성되지 않은 경우.
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    is_rtsp = rtsp_url.lower().startswith(("rtsp://", "rtsps://"))

    cmd: list[str] = [ffmpeg_bin, "-y"]
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
