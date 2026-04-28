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
    def _upload_to_captures(
        self, object_path: str, local_path: Path
    ) -> tuple[str, str]:
        """공통 업로드 헬퍼 — camera-captures 버킷에 JPEG upsert 후 (url, path) 반환."""
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
        public_url = public_url.rstrip("?")
        return public_url, object_path

    def upload_snapshot(self, camera_id: int, local_path: Path) -> tuple[str, str]:
        """10분 주기 PERIODIC 스냅샷 업로드 — periodic/ 하위."""
        timestamp_ms = int(time.time() * 1000)
        object_path = f"periodic/{camera_id}/snapshot_{camera_id}_{timestamp_ms}.jpg"
        return self._upload_to_captures(object_path, local_path)

    def upload_detection_snapshot(
        self, camera_id: int, event_key: str, local_path: Path
    ) -> tuple[str, str]:
        """AI 감지 시점 스냅샷 업로드 — detection/{camera_id}/{event_key}_*.jpg.

        event_key 는 detector 식별자 (예: 'fall', 'fire', 'helmet', 'forklift', 'person').
        PERIODIC 스냅샷과 prefix 가 분리되어 retention/필터링이 용이.
        """
        timestamp_ms = int(time.time() * 1000)
        object_path = (
            f"detection/{camera_id}/{event_key}_{camera_id}_{timestamp_ms}.jpg"
        )
        return self._upload_to_captures(object_path, local_path)

    def upload_fall_snapshot(
        self, camera_id: int, local_path: Path
    ) -> tuple[str, str]:
        """[Deprecated] 쓰러짐 전용 — upload_detection_snapshot('fall', ...) 위임."""
        return self.upload_detection_snapshot(camera_id, "fall", local_path)

    # ────────────────────────────────────────
    # system edge function
    # ────────────────────────────────────────
    def _system_headers(self) -> dict[str, str]:
        # x-system-secret : 우리 커스텀 공유 비밀 (Edge Function 검증용)
        # Authorization   : Supabase Gateway가 요구하는 JWT (anon/service_role 무관, 존재 여부만 통과)
        return {
            "x-system-secret": self._settings.system_agent_secret,
            "Authorization": f"Bearer {self._settings.supabase_service_role_key}",
            "Content-Type": "application/json",
        }

    def register_periodic_capture(
        self, camera_id: int, image_url: str, storage_path: str
    ) -> dict[str, Any]:
        """system Edge Function에 camera_captures(PERIODIC) insert + retention 요청."""
        payload = {
            "action": "camera_capture",
            "camera_id": camera_id,
            "image_url": image_url,
            "storage_path": storage_path,
        }
        resp = self._http.post(
            self._settings.system_endpoint,
            json=payload,
            headers=self._system_headers(),
        )
        resp.raise_for_status()
        return resp.json()

    def register_ai_event(
        self,
        *,
        camera_id: int,
        event_name: str,
        risk_level: str,
        accuracy: float,
        image_url: str | None = None,
    ) -> dict[str, Any]:
        """쓰러짐/화재 등 AI 감지 이벤트를 detection_events 로 등록.

        서버 내부에서 camera_captures(event_type=event_name) + detection_events
        + notifications 까지 한번에 생성.
        """
        payload = {
            "action": "create_ai_event",
            "camera_id": camera_id,
            "event_name": event_name,
            "risk_level": risk_level,
            "accuracy": accuracy,
        }
        if image_url:
            payload["image_url"] = image_url

        resp = self._http.post(
            self._settings.system_endpoint,
            json=payload,
            headers=self._system_headers(),
        )
        resp.raise_for_status()
        return resp.json()

    def close(self) -> None:
        self._http.close()
