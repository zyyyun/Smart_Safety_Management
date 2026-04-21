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
    # Supabase / Storage
    supabase_url: str
    supabase_service_role_key: str
    system_agent_secret: str
    captures_bucket: str

    # Snapshot (Next-4)
    snapshot_interval_min: int
    snapshot_tmp_dir: Path
    ffmpeg_bin: str
    log_level: str

    # Fall detection (Next-3 / LP-2)
    fall_model_weights: str
    fall_interval_min: int
    fall_cooldown_min: int
    fall_device: str  # 'auto' | 'cpu' | 'cuda' | 'cuda:N'
    fall_conf_thres: float
    fall_iou_thres: float
    fall_img_size: int
    fall_enabled_camera_ids: tuple[int, ...]
    fall_demo_seek_sec: float | None  # 레퍼런스 MP4 데모 용도 (RTSP 에서는 무시)

    @property
    def system_endpoint(self) -> str:
        return f"{self.supabase_url.rstrip('/')}/functions/v1/system"


def _parse_camera_ids(raw: str) -> tuple[int, ...]:
    if not raw:
        return ()
    ids: list[int] = []
    for token in raw.replace(";", ",").split(","):
        token = token.strip()
        if not token:
            continue
        try:
            ids.append(int(token))
        except ValueError:
            continue
    return tuple(ids)


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
        fall_model_weights=_required("FALL_MODEL_WEIGHTS"),
        fall_interval_min=int(os.getenv("FALL_INTERVAL_MIN", "1")),
        fall_cooldown_min=int(os.getenv("FALL_COOLDOWN_MIN", "10")),
        fall_device=os.getenv("FALL_DEVICE", "auto"),
        fall_conf_thres=float(os.getenv("FALL_CONF_THRES", "0.25")),
        fall_iou_thres=float(os.getenv("FALL_IOU_THRES", "0.65")),
        fall_img_size=int(os.getenv("FALL_IMG_SIZE", "960")),
        fall_enabled_camera_ids=_parse_camera_ids(os.getenv("FALL_ENABLED_CAMERA_IDS", "")),
        fall_demo_seek_sec=_parse_optional_float(os.getenv("FALL_DEMO_SEEK_SEC")),
    )


def _parse_optional_float(raw: str | None) -> float | None:
    if raw is None or not raw.strip():
        return None
    try:
        return float(raw)
    except ValueError:
        return None
