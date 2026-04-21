"""환경변수 로드 및 설정 객체."""

from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()


def _required(key: str) -> str:
    value = os.getenv(key)
    if not value:
        raise RuntimeError(
            f"환경변수 {key}가 설정되지 않았습니다. ai_agent/.env를 확인하세요."
        )
    return value


@dataclass(frozen=True)
class Settings:
    supabase_url: str
    supabase_service_role_key: str
    system_agent_secret: str
    captures_bucket: str
    snapshot_interval_min: int
    snapshot_tmp_dir: Path
    ffmpeg_bin: str
    log_level: str

    @property
    def system_endpoint(self) -> str:
        return f"{self.supabase_url.rstrip('/')}/functions/v1/system"


def load_settings() -> Settings:
    tmp_dir_raw = os.getenv("SNAPSHOT_TMP_DIR", "").strip()
    tmp_dir = Path(tmp_dir_raw) if tmp_dir_raw else Path(tempfile.gettempdir()) / "pass_snapshots"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    return Settings(
        supabase_url=_required("SUPABASE_URL"),
        supabase_service_role_key=_required("SUPABASE_SERVICE_ROLE_KEY"),
        system_agent_secret=_required("SYSTEM_AGENT_SECRET"),
        captures_bucket=os.getenv("CAMERA_CAPTURES_BUCKET", "camera-captures"),
        snapshot_interval_min=int(os.getenv("SNAPSHOT_INTERVAL_MIN", "10")),
        snapshot_tmp_dir=tmp_dir,
        ffmpeg_bin=os.getenv("FFMPEG_BIN", "ffmpeg"),
        log_level=os.getenv("LOG_LEVEL", "INFO").upper(),
    )
