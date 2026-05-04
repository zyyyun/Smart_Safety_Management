# -*- coding: utf-8 -*-
"""Tests for j2208a.supabase_writer — D-14 (0x28 silent drop) + lazy import.

All tests use a MagicMock client — no real DB connection, no env vars required.
"""

from unittest.mock import MagicMock

from j2208a import supabase_writer
from j2208a.supabase_writer import (
    CMD_HRV_BLOOD_PRESSURE,
    insert_raw_event,
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
    AND export the 6 functions that 04-03 will import.
    """
    assert hasattr(supabase_writer, "insert_raw_event")
    assert hasattr(supabase_writer, "insert_minute_summary")
    assert hasattr(supabase_writer, "insert_wear_state_event")
    assert hasattr(supabase_writer, "insert_safety_alert")
    assert hasattr(supabase_writer, "update_safety_alert_resolved")
    assert hasattr(supabase_writer, "call_watch_alert_edge_function")
    assert CMD_HRV_BLOOD_PRESSURE == 0x28
