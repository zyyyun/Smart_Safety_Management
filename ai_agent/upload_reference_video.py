"""reference_media/ 의 영상 파일을 Supabase Storage (reference-videos 버킷) 에
업로드하고 cameras 테이블의 live_url / live_url_detail 를 공개 URL로 갱신한다.

목적
----
Next-4 단계에서 agent는 로컬 파일 경로로 FFmpeg 스냅샷을 찍지만, Android 앱은
그 경로를 직접 재생할 수 없다. Supabase Storage 에 영상을 올려두고 공개 URL로
cameras 행을 갱신하면 "현장"/"전경" 영역에서 실제 영상이 재생된다.

사용
----
    python upload_reference_video.py --device TEST-CAM-02 --local reference_media/fall/E02_001.mp4
    python upload_reference_video.py --device TEST-CAM-02 --local <path> --no-update  # 업로드만

버킷이 없으면 자동 생성. 공개 URL(HTTPS)로 덮어쓴다.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from dotenv import load_dotenv
from supabase import Client, create_client

BUCKET = "reference-videos"
ALLOWED_SUFFIX = {".mp4", ".webm", ".mkv", ".mov"}


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
        if "exists" in str(e).lower() or "already" in str(e).lower():
            return
        # 이미 있는 경우 외의 에러는 무시하지 않고 알림
        print(f"[bucket] create skipped: {e}")


def upload(client: Client, local: Path, remote: str) -> str:
    with local.open("rb") as fp:
        client.storage.from_(BUCKET).upload(
            path=remote,
            file=fp,
            file_options={"content-type": "video/mp4", "x-upsert": "true"},
        )
    url = client.storage.from_(BUCKET).get_public_url(remote).rstrip("?")
    print(f"[upload] {local} -> {url}")
    return url


def update_camera(client: Client, device_code: str, url: str) -> int:
    resp = (
        client.table("cameras")
        .update({"live_url": url, "live_url_detail": url})
        .eq("device_code", device_code)
        .execute()
    )
    return len(resp.data or [])


def main() -> int:
    load_dotenv()
    url_env = os.getenv("SUPABASE_URL")
    key_env = os.getenv("SUPABASE_SERVICE_ROLE_KEY")
    if not url_env or not key_env:
        print("[config] SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY 가 .env에 없습니다.", file=sys.stderr)
        return 2

    p = argparse.ArgumentParser(description=__doc__ or "")
    p.add_argument("--device", required=True, help="cameras.device_code (예: TEST-CAM-02)")
    p.add_argument("--local", required=True, help="업로드할 로컬 영상 경로")
    p.add_argument(
        "--remote",
        default=None,
        help="버킷 내부 경로 (기본: <category>/<filename>)",
    )
    p.add_argument(
        "--no-update",
        action="store_true",
        help="cameras 테이블 live_url 갱신을 생략 (업로드만)",
    )
    args = p.parse_args()

    local = Path(args.local).expanduser().resolve()
    if not local.exists():
        print(f"[error] 파일 없음: {local}", file=sys.stderr)
        return 1
    if local.suffix.lower() not in ALLOWED_SUFFIX:
        print(
            f"[error] 지원 확장자 아님 (.mp4/.webm/.mkv/.mov): {local.suffix}",
            file=sys.stderr,
        )
        return 1

    # 기본 remote 경로: reference_media 하위 구조 유지
    if args.remote:
        remote = args.remote
    else:
        # reference_media/fall/E02_001.mp4 -> fall/E02_001.mp4
        parts = list(local.parts)
        if "reference_media" in parts:
            idx = parts.index("reference_media")
            remote = "/".join(parts[idx + 1 :])
        else:
            remote = f"misc/{local.name}"

    client = create_client(url_env, key_env)
    ensure_bucket(client)
    url = upload(client, local, remote)

    if args.no_update:
        print("[update] --no-update 지정, cameras 갱신 생략")
        return 0

    n = update_camera(client, args.device, url)
    if n == 0:
        print(f"[update] 경고 : device_code={args.device} 해당 행이 없어 아무것도 갱신 안 됨")
        return 3
    print(f"[update] cameras({args.device}).live_url / live_url_detail 갱신 완료")
    return 0


if __name__ == "__main__":
    sys.exit(main())
