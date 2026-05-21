# -*- coding: utf-8 -*-
"""Supabase writer — service_role client + watch-alert Edge Function helper.

Per CONTEXT.md D-11·D-12·D-14. This module is the contract that 04-03 will
import; signatures are stable.

D-14 (Dual stream): cmd=0x28 (CMD_HRV_BLOOD_PRESSURE) responses carry the same
HR + temperature_c that arrive on cmd=0x09 (CMD_HEALTH_MEASURE). 0x09 is the
superset (steps + HR + temp) so 0x28 is duplicate. raw_events insertion path
performs a SILENT DROP for cmd=0x28 — by design (NOT data loss). 2026-05-02
log analysis confirmed this dual-stream behavior; the 5-6Hz spec was actually
arriving as 10-12Hz because of the duplicate. The 20s `cmd_health_measure(2,
True)` restart command is kept (it keeps 0x09 alive) but the 0x28 by-product
is dropped at the writer.

Lazy import of supabase-py: tests run without the SDK installed; the SDK is
only required at runtime when get_client() is called (production).
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any, Optional


# Lazy import — module must load even when supabase-py is not installed
# (unit tests use a MagicMock client; CI need not pip install supabase).
try:
    from supabase import create_client, Client  # type: ignore
except ImportError:  # pragma: no cover - exercised only when SDK absent
    create_client = None  # type: ignore
    Client = None  # type: ignore


SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
NOTIFICATIONS_FUNCTION_PATH = "/functions/v1/notifications"  # 04-03 adds 'watch-alert' action


# Per CONTEXT.md D-14 — silent-drop sentinel.
CMD_HRV_BLOOD_PRESSURE = 0x28  # dual-stream duplicate; 0x09 is superset


_client_cache: Optional[Any] = None


def get_client() -> Any:
    """Return a memoized service_role Supabase client. Raises if env not set."""
    global _client_cache
    if _client_cache is not None:
        return _client_cache
    if create_client is None:
        raise RuntimeError("supabase-py not installed. pip install supabase")
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        raise RuntimeError("SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY env vars required")
    _client_cache = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
    return _client_cache


# ===================================================================
# Inserts (called by BLE client — 04-03)
# ===================================================================
def insert_raw_event(
    client: Any,
    device_id: int,
    ts_iso: str,
    cmd: int,
    raw_hex: str,
    parsed: dict,
) -> None:
    """Insert into raw_events with 1-second dedup (UNIQUE handled by 04-01).

    D-14: cmd == 0x28 (CMD_HRV_BLOOD_PRESSURE) -> SILENT DROP. The 0x09 stream
    carries the same HR + temperature plus steps, so 0x28 is duplicate. No
    debug log emitted (CONTEXT.md "Claude's Discretion": default off).
    """
    if cmd == CMD_HRV_BLOOD_PRESSURE:
        return  # D-14: dual stream — 0x09 carries same HR/temp + steps; 0x28 silent drop
    client.table("raw_events").upsert(
        {
            "device_id": device_id,
            "ts": ts_iso,
            "cmd": cmd,
            "raw_hex": raw_hex,
            "parsed": parsed,
        },
        on_conflict="device_id,ts_truncated_to_second,raw_hash",
        ignore_duplicates=True,
    ).execute()


def insert_minute_summary(
    client: Any,
    device_id: int,
    minute_ts_iso: str,
    hr_median: Optional[int],
    temp_median: Optional[float],
    temp_iqr: Optional[float],
    steps_delta: Optional[int],
    dominant_state: Optional[str],
    good_ratio: float,
) -> None:
    client.table("minute_summary").upsert({
        "device_id": device_id,
        "minute_ts": minute_ts_iso,
        "hr_median": hr_median,
        "temp_median": temp_median,
        "temp_iqr": temp_iqr,
        "steps_delta": steps_delta,
        "dominant_state": dominant_state,
        "good_ratio": good_ratio,
    }).execute()


def insert_wear_state_event(
    client: Any,
    device_id: int,
    ts_iso: str,
    from_state: str,
    to_state: str,
    reason: dict,
) -> None:
    client.table("wear_state_events").insert({
        "device_id": device_id,
        "ts": ts_iso,
        "from_state": from_state,
        "to_state": to_state,
        "reason": reason,
    }).execute()


def insert_safety_alert(
    client: Any,
    device_id: int,
    alert_type: str,
    severity: str,
    reason: dict,
    raised_at_iso: str,
) -> int:
    """Insert a safety_alerts row. Returns alert_id (or -1 on no-rows).

    severity 'NORMAL' (resolution) MUST NOT be inserted here — use
    update_safety_alert_resolved instead (DB CHECK constraint accepts only
    CAUTION / WARNING / DANGER per D-01).
    """
    result = client.table("safety_alerts").insert({
        "device_id": device_id,
        "alert_type": alert_type,
        "severity": severity,
        "raised_at": raised_at_iso,
        "reason": reason,
    }).execute()
    return result.data[0]["alert_id"] if getattr(result, "data", None) else -1


def update_safety_alert_resolved(
    client: Any,
    device_id: int,
    alert_type: str,
    resolved_at_iso: str,
) -> None:
    """Set resolved_at on the most recent unresolved alert (경보→정상)."""
    client.table("safety_alerts").update(
        {"resolved_at": resolved_at_iso}
    ).eq("device_id", device_id).eq("alert_type", alert_type).is_(
        "resolved_at", "null"
    ).execute()


def upsert_device(
    client: Any,
    user_id: str,
    mac_address: str,
    firmware_version: Optional[str] = None,
    battery_level: Optional[int] = None,
) -> Optional[int]:
    """2026-05-21 — BLE 스크립트가 워치 connect 직후 호출하는 devices 자동 등록.

    카메라의 update_camera_health 패턴 미러 (스크립트 → supabase 자동 등록 →
    앱이 별도 PairWatchSection 없이 자동 인식).

    Schema (010_watch_pipeline.sql:147 seed 와 정합):
        serial_number = 'J2208A-{MAC.upper()}'
        device_type   = 'WATCH'
        mac_address   = MAC.upper()
        user_id       = 호출자 (default 'testuser1')

    Race-safe user_id 보존 정책:
        1. 기존 row + user_id 가 다른 사용자 → user_id 보존 (pair 침범 금지).
           메타데이터 (firmware_version, last_comm_at) 만 update.
        2. 기존 row + user_id NULL or 같음 → user_id set + 메타데이터 update.
        3. 부재 → 신규 INSERT.

    Args:
        client: service_role supabase Client (RLS 우회).
        user_id: pair 대상 user. 환경변수 WATCH_OWNER_USER_ID 또는 default.
        mac_address: BLE MAC (예: "21:02:02:06:01:69"). 대소문자 무관.
        firmware_version: BLE notify packet 에서 추출 가능 시. None 이면 기존 유지.
        battery_level: 초기 배터리 (옵션).

    Returns:
        device_id (int). 호출 실패 시 None — silent fail (BLE 루프 차단 안 함).
    """
    mac_upper = mac_address.upper()
    serial = f"J2208A-{mac_upper}"
    try:
        existing = client.table("devices").select(
            "device_id, user_id, firmware_version"
        ).eq("serial_number", serial).limit(1).execute()
        rows = getattr(existing, "data", None) or []

        if rows:
            row = rows[0]
            device_id = int(row["device_id"])
            existing_user_id = row.get("user_id")
            update_payload: dict[str, Any] = {}
            if firmware_version:
                update_payload["firmware_version"] = firmware_version
            if battery_level is not None:
                update_payload["battery_level"] = battery_level
            # Pair 침범 금지 — 다른 user 에게 이미 pair 됐으면 user_id 보존.
            if existing_user_id is None or existing_user_id == user_id:
                update_payload["user_id"] = user_id
                print(
                    f"[+] upsert_device: existing device_id={device_id} "
                    f"serial={serial} user_id={user_id} (set/refresh)"
                )
            else:
                print(
                    f"[+] upsert_device: existing device_id={device_id} "
                    f"serial={serial} user_id={existing_user_id} preserved "
                    f"(caller={user_id} skipped to protect pair)"
                )
            if update_payload:
                client.table("devices").update(update_payload).eq(
                    "device_id", device_id
                ).execute()
            return device_id

        # 신규 INSERT
        insert_payload: dict[str, Any] = {
            "device_type": "WATCH",
            "serial_number": serial,
            "mac_address": mac_upper,
            "user_id": user_id,
        }
        if firmware_version:
            insert_payload["firmware_version"] = firmware_version
        if battery_level is not None:
            insert_payload["battery_level"] = battery_level
        resp = client.table("devices").insert(insert_payload).execute()
        new_rows = getattr(resp, "data", None) or []
        if new_rows:
            device_id = int(new_rows[0]["device_id"])
            print(
                f"[+] upsert_device: INSERTED device_id={device_id} "
                f"serial={serial} user_id={user_id}"
            )
            return device_id
        return None
    except Exception as e:  # pragma: no cover - 네트워크/스키마 transient
        # 의도된 silent fail — BLE 루프 절대 차단 X
        print(f"[!] upsert_device failed (mac={mac_upper}, user={user_id}): {e}")
        return None


def update_device_last_comm_at(
    client: Any,
    device_id: int,
    ts_iso: str,
) -> None:
    """Update devices.last_comm_at — Phase 7 BRIDGE-01 CONNECTED status trigger.

    Called by runtime.process_sample on every accepted (non-0x28) packet.
    runtime layer throttles the call to once per 60s per device to avoid
    DB hammering (Phase 8 D-09 pattern: rate-limited heartbeat). Phase 7
    PairWatchSection.computeStatus() interprets:
       last_comm_at <  now() - 5min  → DISCONNECTED  ("끊김" 노랑)
       last_comm_at >= now() - 5min  → CONNECTED     ("연결됨" 초록)

    Failure is swallowed (Log.warning equivalent) — heartbeat is best-effort,
    must NEVER break the BLE pipeline. Phase 8 D-09 cameras.last_frame_at
    pattern 1:1 mirror.
    """
    try:
        client.table("devices").update(
            {"last_comm_at": ts_iso}
        ).eq("device_id", device_id).execute()
    except Exception as e:  # pragma: no cover - 네트워크 transient
        # 의도된 silent fail — BLE 루프 절대 차단 X
        print(f"[!] update_device_last_comm_at failed (device_id={device_id}): {e}")


# ===================================================================
# Edge Function call — FCM push (D-12)
# ===================================================================
def call_watch_alert_edge_function(
    user_id: str,
    alert_type: str,
    severity: str,
    alert_id: int,
    title: str,
    body: str,
) -> bool:
    """POST to notifications Edge Function with action='watch-alert' (D-12).

    04-03 will add the matching `case 'watch-alert'` branch to
    supabase/functions/notifications/index.ts. Returns True on 2xx response.
    """
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        return False
    url = SUPABASE_URL.rstrip("/") + NOTIFICATIONS_FUNCTION_PATH
    payload = json.dumps({
        "action": "watch-alert",
        "user_id": user_id,
        "alert_type": alert_type,
        "severity": severity,
        "alert_id": alert_id,
        "title": title,
        "body": body,
    }).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {SUPABASE_SERVICE_ROLE_KEY}",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return 200 <= resp.status < 300
    except urllib.error.URLError:
        return False
