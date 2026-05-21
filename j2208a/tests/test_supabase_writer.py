# -*- coding: utf-8 -*-
"""Tests for j2208a.supabase_writer — D-14 (0x28 silent drop) + lazy import.

All tests use a MagicMock client — no real DB connection, no env vars required.
"""

from unittest.mock import MagicMock

from j2208a import supabase_writer
from j2208a.supabase_writer import (
    CMD_HRV_BLOOD_PRESSURE,
    insert_raw_event,
    update_device_last_comm_at,
)


def _make_mock_client():
    """Build a MagicMock matching the supabase-py fluent chain:
    client.table('x').upsert({...}, ...).execute() -> MagicMock.
    """
    return MagicMock()


def test_insert_raw_event_drops_0x28():
    """D-14 core regression guard: cmd=0x28 (CMD_HRV_BLOOD_PRESSURE) -> SILENT DROP.

    Rationale: the 0x09 stream (CMD_HEALTH_MEASURE) carries the same HR + temp
    plus steps as a superset, so 0x28 is duplicate. Must NOT trigger
    raw_events.upsert chain.
    """
    client = _make_mock_client()
    insert_raw_event(
        client,
        device_id=1,
        ts_iso="2026-05-02T12:00:00.000Z",
        cmd=0x28,
        raw_hex="28 02 57 00 00 00 00 00 6f 01 00 00 00 00 00 6f",
        parsed={"heart_rate": 72, "temperature_c": 36.5},
    )
    assert client.table.call_count == 0, (
        f"D-14 violation: expected 0 calls to client.table() for cmd=0x28, "
        f"got {client.table.call_count}. silent drop must skip the upsert chain."
    )


def test_insert_raw_event_keeps_0x09():
    """D-14 control: cmd=0x09 (canonical superset stream) is normally inserted."""
    client = _make_mock_client()
    insert_raw_event(
        client,
        device_id=1,
        ts_iso="2026-05-02T12:00:00.000Z",
        cmd=0x09,
        raw_hex="09 02 d2 04 00 00 00 00 00 00 00 00 00 00 00 00",
        parsed={"heart_rate": 72, "temperature_c": 36.5, "steps": 1234},
    )
    assert client.table.call_count == 1, (
        f"D-14 keep: expected 1 call to client.table() for cmd=0x09, "
        f"got {client.table.call_count}."
    )
    client.table.assert_called_with("raw_events")
    client.table.return_value.upsert.assert_called_once()


def test_supabase_writer_imports_without_sdk():
    """Lazy-import regression: module must load even when supabase-py is absent
    AND export the 7 functions that 04-03 + Phase 7 BRIDGE-01 import.
    """
    assert hasattr(supabase_writer, "insert_raw_event")
    assert hasattr(supabase_writer, "insert_minute_summary")
    assert hasattr(supabase_writer, "insert_wear_state_event")
    assert hasattr(supabase_writer, "insert_safety_alert")
    assert hasattr(supabase_writer, "update_safety_alert_resolved")
    assert hasattr(supabase_writer, "update_device_last_comm_at")  # Phase 7 BRIDGE-01
    assert hasattr(supabase_writer, "call_watch_alert_edge_function")
    assert CMD_HRV_BLOOD_PRESSURE == 0x28


def test_update_device_last_comm_at_invokes_devices_table_update():
    """Phase 7 BRIDGE-01 CONNECTED status trigger — devices.last_comm_at UPDATE
    SQL contract:
       UPDATE devices SET last_comm_at = $ts_iso WHERE device_id = $device_id
    fluent chain: client.table('devices').update({...}).eq('device_id', N).execute()
    """
    client = _make_mock_client()
    update_device_last_comm_at(client, device_id=1, ts_iso="2026-05-19T04:30:00+00:00")
    client.table.assert_called_with("devices")
    update_call = client.table.return_value.update
    update_call.assert_called_once_with({"last_comm_at": "2026-05-19T04:30:00+00:00"})
    eq_call = update_call.return_value.eq
    eq_call.assert_called_with("device_id", 1)
    eq_call.return_value.execute.assert_called_once()


def test_update_device_last_comm_at_swallows_failure():
    """Best-effort heartbeat — DB 실패가 BLE pipeline 을 절대 차단하지 않음.
    client.table(...).update(...).eq(...).execute() 가 Exception 던져도 함수는
    silent fail (print stderr 만)."""
    client = MagicMock()
    client.table.return_value.update.return_value.eq.return_value.execute.side_effect = (
        RuntimeError("transient PostgREST error")
    )
    # Should NOT raise — function swallows exception
    update_device_last_comm_at(client, device_id=1, ts_iso="2026-05-19T04:30:00+00:00")
