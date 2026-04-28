"""LP-2 확장용 4종 레퍼런스 영상 일괄 업로드 + cameras 갱신.

레거시 폴더 (D:\\2025_산업안전\\산업안전\\모델 7종\\) 의 데모 자산을
Supabase Storage `reference-videos/{event_key}/` 아래에 업로드하고
cameras.live_url_detail 을 갱신해 ai_agent 가 바로 RTSP 자리에 mp4 URL 을 넣어
프레임 추출할 수 있게 한다.

소스 종류별 처리:
  - mp4         : 그대로 업로드
  - gif         : ffmpeg 로 mp4 변환 (yuv420p, faststart)
  - image_loop  : 단일 이미지를 N초 loop mp4 로 변환

ffmpeg 가 PATH 에 있어야 함 (기존 ai_agent/snapshot.py 와 동일 가정).

사용:
    python ai_agent/scripts/upload_reference_videos.py
    python ai_agent/scripts/upload_reference_videos.py --only fire,helmet
    python ai_agent/scripts/upload_reference_videos.py --skip-update  # 업로드만, cameras 갱신 X
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path

from dotenv import load_dotenv
from supabase import Client, create_client


BUCKET = "reference-videos"
LEGACY_BASE = Path(r"D:\2025_산업안전\산업안전\모델 7종")


# (event_key, camera_id, kind, source path, [optional duration])
SOURCES: dict[str, dict] = {
    "fire": {
        "camera_id": 1,
        "kind": "mp4",
        "src": LEGACY_BASE / "화재 탐지" / "input.mp4",
    },
    "helmet": {
        "camera_id": 5,
        "kind": "image_loop",
        # 첫 번째 jpg 자동 탐색
        "src_dir": LEGACY_BASE / "안전모 탐지" / "hard_hat_images",
        "duration": 60,
    },
    "forklift": {
        "camera_id": 4,
        "kind": "gif",
        "src": LEGACY_BASE / "지게차 탐지" / "test_forklift.gif",
    },
    "person": {
        # 1.5GB 원본 → 60초 클립 + crf 28 압축 (≈10-30MB).
        "camera_id": 3,
        "kind": "mp4_clip",
        "src": LEGACY_BASE / "사람 탐지" / "input_video.mp4",
        "duration": 60,
    },
}


def find_first_image(folder: Path) -> Path | None:
    if not folder.exists():
        return None
    for ext in (".jpg", ".jpeg", ".png"):
        files = sorted(folder.glob(f"*{ext}"))
        if files:
            return files[0]
    return None


def run_ffmpeg(cmd: list[str]) -> None:
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(
            f"ffmpeg failed (exit {proc.returncode}):\n{proc.stderr[-2000:]}"
        )


def convert_gif_to_mp4(gif_path: Path, out_path: Path) -> None:
    run_ffmpeg([
        "ffmpeg", "-y", "-i", str(gif_path),
        "-movflags", "faststart",
        "-pix_fmt", "yuv420p",
        "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",  # 짝수 해상도 보정
        str(out_path),
    ])


def convert_image_loop_to_mp4(img_path: Path, duration: int, out_path: Path) -> None:
    run_ffmpeg([
        "ffmpeg", "-y", "-loop", "1", "-t", str(duration), "-i", str(img_path),
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-tune", "stillimage",
        "-r", "10",  # 10fps for static image
        "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
        str(out_path),
    ])


def convert_mp4_clip(src: Path, duration: int, out_path: Path) -> None:
    """원본 mp4 의 처음 duration 초만 잘라 압축 (50MB 미만 목표)."""
    run_ffmpeg([
        "ffmpeg", "-y", "-ss", "0", "-t", str(duration), "-i", str(src),
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "28",
        "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
        "-an",  # 오디오 제거 (검출에 불필요)
        "-movflags", "faststart",
        str(out_path),
    ])


def ensure_bucket(client: Client) -> None:
    try:
        client.storage.create_bucket(
            BUCKET,
            options={
                "public": True,
                "file_size_limit": 52428800,  # 50MB
                "allowed_mime_types": [
                    "video/mp4",
                    "video/webm",
                    "video/x-matroska",
                    "video/quicktime",
                ],
            },
        )
        print(f"[bucket] created: {BUCKET}")
    except Exception as e:
        msg = str(e).lower()
        if "exists" in msg or "already" in msg:
            return
        print(f"[bucket] create skipped: {e}")


def upload_video(client: Client, local: Path, remote: str) -> str:
    with local.open("rb") as fp:
        client.storage.from_(BUCKET).upload(
            path=remote,
            file=fp,
            file_options={"content-type": "video/mp4", "x-upsert": "true"},
        )
    url = client.storage.from_(BUCKET).get_public_url(remote).rstrip("?")
    return url


def update_camera_url(client: Client, camera_id: int, url: str) -> int:
    resp = (
        client.table("cameras")
        .update({"live_url": url, "live_url_detail": url})
        .eq("camera_id", camera_id)
        .execute()
    )
    return len(resp.data or [])


def process_one(
    event_key: str, cfg: dict, client: Client, skip_update: bool, work_dir: Path
) -> tuple[bool, str]:
    """단일 detector 의 영상 변환·업로드·DB 갱신. (성공여부, 메시지) 반환."""
    kind = cfg["kind"]
    camera_id = cfg["camera_id"]

    # 1) source 결정 + 변환
    if kind == "mp4":
        src = Path(cfg["src"])
        if not src.exists():
            return False, f"source mp4 부재: {src}"
        local_mp4 = src
    elif kind == "gif":
        src = Path(cfg["src"])
        if not src.exists():
            return False, f"source gif 부재: {src}"
        local_mp4 = work_dir / f"{event_key}_from_gif.mp4"
        try:
            convert_gif_to_mp4(src, local_mp4)
        except Exception as e:
            return False, f"gif→mp4 변환 실패: {e}"
    elif kind == "image_loop":
        src_dir = Path(cfg["src_dir"])
        img = find_first_image(src_dir)
        if img is None:
            return False, f"source 이미지 폴더 비어있음: {src_dir}"
        local_mp4 = work_dir / f"{event_key}_from_image.mp4"
        try:
            convert_image_loop_to_mp4(img, cfg.get("duration", 60), local_mp4)
        except Exception as e:
            return False, f"image→mp4 변환 실패: {e}"
    elif kind == "mp4_clip":
        src = Path(cfg["src"])
        if not src.exists():
            return False, f"source mp4 부재: {src}"
        local_mp4 = work_dir / f"{event_key}_clip.mp4"
        try:
            convert_mp4_clip(src, cfg.get("duration", 60), local_mp4)
        except Exception as e:
            return False, f"mp4 클립 변환 실패: {e}"
    else:
        return False, f"unknown kind: {kind}"

    # 2) Storage 업로드
    remote_path = f"{event_key}/source.mp4"
    try:
        url = upload_video(client, local_mp4, remote_path)
    except Exception as e:
        return False, f"upload 실패: {e}"

    # 3) cameras 갱신
    if skip_update:
        return True, f"uploaded {url} (cameras 갱신 생략)"
    try:
        n = update_camera_url(client, camera_id, url)
    except Exception as e:
        return False, f"upload OK ({url}) 이지만 cameras 갱신 실패: {e}"
    if n == 0:
        return False, f"camera_id={camera_id} 행 없음. URL={url}"
    return True, f"camera_id={camera_id} ← {url}"


def main() -> int:
    load_dotenv()
    url_env = os.getenv("SUPABASE_URL")
    key_env = os.getenv("SUPABASE_SERVICE_ROLE_KEY")
    if not url_env or not key_env:
        print("[error] SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY 가 .env 에 없습니다.", file=sys.stderr)
        return 2

    p = argparse.ArgumentParser(description=__doc__ or "")
    p.add_argument("--only", default=None, help="처리할 detector 쉼표 구분 (예: fire,helmet)")
    p.add_argument("--skip-update", action="store_true", help="cameras 갱신 생략")
    args = p.parse_args()

    targets = list(SOURCES.keys())
    if args.only:
        only = {t.strip() for t in args.only.split(",") if t.strip()}
        targets = [k for k in targets if k in only]
        if not targets:
            print("[error] --only 매칭되는 detector 없음", file=sys.stderr)
            return 1

    client = create_client(url_env, key_env)
    ensure_bucket(client)

    results = {}
    with tempfile.TemporaryDirectory(prefix="lp2_videos_") as tmp:
        work_dir = Path(tmp)
        for key in targets:
            cfg = SOURCES[key]
            print(f"\n=== {key} (camera_id={cfg['camera_id']}) ===")
            ok, msg = process_one(key, cfg, client, args.skip_update, work_dir)
            results[key] = (ok, msg)
            print(f"  -> {'OK' if ok else 'FAIL'} {msg}")

    print("\n=== 요약 ===")
    success = sum(1 for ok, _ in results.values() if ok)
    fail = len(results) - success
    print(f"  성공 {success} / 실패 {fail}")
    for key, (ok, msg) in results.items():
        print(f"  [{'OK' if ok else 'ERR'}] {key}: {msg}")

    return 0 if fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
