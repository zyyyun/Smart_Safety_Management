"""acceptance gate — E02_001.mp4 에서 쓰러짐이 최소 1건 감지되는지 확인.

용도
----
FallDetector 포팅 이후, 실제 쓰러짐 레퍼런스 영상에서 판정이 동작하는지 검증.
스케줄러 배포 전 이 스크립트가 반드시 성공해야 함.

사용
----
    python scripts/verify_fall_on_video.py
    python scripts/verify_fall_on_video.py --video D:/.../fall/E02_001.mp4 --fps 2

동작
----
1. ffmpeg 로 대상 영상에서 N fps 로 프레임을 임시 폴더에 추출
2. 각 프레임에 FallDetector.detect_fall 호출
3. 첫 is_fall=True 발견 시 시각 + confidence 출력 후 exit 0
4. 끝까지 없으면 exit 1 (acceptance gate FAIL)
"""

from __future__ import annotations

import argparse
import logging
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import cv2

# ai_agent 디렉토리를 sys.path 에 추가 (scripts/ 에서 상위 모듈 import)
_AI_AGENT_DIR = Path(__file__).resolve().parent.parent
if str(_AI_AGENT_DIR) not in sys.path:
    sys.path.insert(0, str(_AI_AGENT_DIR))

from config import load_settings  # noqa: E402
from fall_detector import FallDetector  # noqa: E402


log = logging.getLogger("verify_fall")


def extract_frames(video: Path, out_dir: Path, fps: int, ffmpeg_bin: str) -> list[Path]:
    """ffmpeg 로 N fps 추출. 반환: 프레임 파일 경로 리스트 (시간순)."""
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        ffmpeg_bin,
        "-y",
        "-i", str(video),
        "-vf", f"fps={fps}",
        "-q:v", "2",
        str(out_dir / "frame_%05d.jpg"),
    ]
    log.debug("extract cmd: %s", " ".join(cmd))
    res = subprocess.run(cmd, capture_output=True, timeout=120, check=False)
    if res.returncode != 0:
        stderr = res.stderr.decode("utf-8", errors="replace").splitlines()[-10:]
        raise RuntimeError(
            f"ffmpeg 추출 실패 (rc={res.returncode}):\n" + "\n".join(stderr)
        )
    frames = sorted(out_dir.glob("frame_*.jpg"))
    return frames


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    p = argparse.ArgumentParser(description=__doc__ or "")
    p.add_argument(
        "--video",
        default="reference_media/fall/E02_001.mp4",
        help="대상 영상 파일 (기본: reference_media/fall/E02_001.mp4)",
    )
    p.add_argument("--fps", type=int, default=3, help="프레임 추출 fps (기본 3)")
    p.add_argument(
        "--max-frames",
        type=int,
        default=500,
        help="최대 검사 프레임 수 (기본 500 = 약 2분 40초 @ 3fps, 짧은 쓰러짐 순간 포함)",
    )
    args = p.parse_args()

    settings = load_settings()

    # Smart_Safety_Management/ 프로젝트 루트 기준으로 영상 경로 해석
    video_path = Path(args.video)
    if not video_path.is_absolute():
        video_path = (_AI_AGENT_DIR.parent / video_path).resolve()
    if not video_path.exists():
        print(f"❌ 영상 파일 없음: {video_path}", file=sys.stderr)
        return 2

    log.info("video: %s", video_path)
    log.info("FallDetector 로드 중 (device=%s)", settings.fall_device)
    detector = FallDetector(
        weights_path=settings.fall_model_weights,
        device=settings.fall_device,
        conf_thres=settings.fall_conf_thres,
        iou_thres=settings.fall_iou_thres,
        img_size=settings.fall_img_size,
    )

    tmp_dir = Path(tempfile.mkdtemp(prefix="fall_verify_"))
    try:
        frames = extract_frames(
            video_path, tmp_dir, fps=args.fps, ffmpeg_bin=settings.ffmpeg_bin
        )
        log.info("추출된 프레임: %d 개", len(frames))
        if not frames:
            print("❌ 프레임이 0개", file=sys.stderr)
            return 3

        for i, frame_path in enumerate(frames[: args.max_frames]):
            img = cv2.imread(str(frame_path))
            if img is None:
                log.warning("이미지 로드 실패: %s", frame_path)
                continue
            result = detector.detect_fall(img)
            ts = i / args.fps
            status = "FALL" if result.is_fall else "    "
            log.info(
                "[%s] t=%5.1fs poses=%d inference=%.0fms%s",
                status,
                ts,
                result.pose_count,
                result.inference_ms,
                f" conf={result.confidence:.2f}" if result.is_fall else "",
            )
            if result.is_fall:
                print(
                    f"\n✅ 쓰러짐 감지 확인: {video_path.name} "
                    f"t={ts:.1f}s conf={result.confidence:.2f}"
                )
                return 0

        print(
            f"\n❌ {len(frames)} 프레임 중 쓰러짐 감지 0건. "
            "threshold / img_size / 영상 해상도 점검 필요.",
            file=sys.stderr,
        )
        return 1
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
