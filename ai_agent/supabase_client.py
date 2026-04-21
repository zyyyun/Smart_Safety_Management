"""Supabase REST + Storage 래퍼.

- 카메라 목록 조회 (`live_url_detail` 있는 행만)
- Storage에 JPEG 업로드 → 공개 URL 반환
- system Edge Function 호출 (camera_capture)
"""

from __future__ import annotations

import logging
import time
from pathlib import Path
from typing import Any

import httpx
from supabase import Client, create_client

from config import Settings


log = logging.getLogger(__name__)


class SupabaseBridge:
    def __init__(self, settings: Settings):
        self._settings = settings
        self._client: Client = create_client(
            settings.supabase_url, settings.supabase_service_role_key
        )
        self._http = httpx.Client(timeout=15.0)

    # ────────────────────────────────────────
    # cameras
    # ────────────────────────────────────────
    def fetch_active_cameras(self) -> list[dict[str, Any]]:
        """live_url_detail이 채워진 카메라 목록."""
        result = (
            self._client.table("cameras")
            .select("camera_id, device_name, install_area, live_url_detail")
            .not_.is_("live_url_detail", "null")
            .neq("live_url_detail", "")
            .execute()
        )
        return list(result.data or [])

    # ────────────────────────────────────────
    # storage
    # ────────────────────────────────────────
    def upload_snapshot(self, camera_id: int, local_path: Path) -> tuple[str, str]:
        """스냅샷 파일을 Storage에 업로드. (public_url, object_path) 반환."""
        timestamp_ms = int(time.time() * 1000)
        object_path = f"periodic/{camera_id}/snapshot_{camera_id}_{timestamp_ms}.jpg"

        with local_path.open("rb") as fp:
            self._client.storage.from_(self._settings.captures_bucket).upload(
                path=object_path,
                file=fp,
                file_options={
                    "content-type": "image/jpeg",
                    "x-upsert": "true",
                },
            )

        public_url = self._client.storage.from_(
            self._settings.captures_bucket
        ).get_public_url(object_path)
        # supabase-py는 때때로 trailing "?" 를 붙임 — 간단히 정리.
        public_url = public_url.rstrip("?")
        return public_url, object_path

    # ────────────────────────────────────────
    # system edge function
    # ────────────────────────────────────────
    def register_periodic_capture(
        self, camera_id: int, image_url: str, storage_path: str
    ) -> dict[str, Any]:
        """system Edge Function에 insert + retention 요청."""
        payload = {
            "action": "camera_capture",
            "camera_id": camera_id,
            "image_url": image_url,
            "storage_path": storage_path,
        }
        # x-system-secret : 우리 커스텀 공유 비밀 (Edge Function 검증용)
        # Authorization   : Supabase Gateway가 요구하는 JWT (anon/service_role 무관, 존재 여부만 통과)
        headers = {
            "x-system-secret": self._settings.system_agent_secret,
            "Authorization": f"Bearer {self._settings.supabase_service_role_key}",
            "Content-Type": "application/json",
        }

        resp = self._http.post(
            self._settings.system_endpoint, json=payload, headers=headers
        )
        resp.raise_for_status()
        return resp.json()

    def close(self) -> None:
        self._http.close()
