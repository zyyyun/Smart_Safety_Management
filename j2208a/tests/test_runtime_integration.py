# -*- coding: utf-8 -*-
"""Integration tests for j2208a.runtime — mocked supabase client + alert_caller.

Per CONTEXT.md D-04·D-09·D-12·D-14 + 04-03 plan tasks 2 (RT-1..RT-7).

Tests run without supabase-py installed: the writer module performs lazy
import (`create_client = None` if absent), and these tests never call
`get_client()` — every test passes its own MagicMock client + alert_caller.

CRITICAL: every test resets `runtime._runtime` first. Without this, state
leaks across tests (DeriveContext.last_severity, StateMachine.window) cause
nondeterministic ordering failures.
"""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from j2208a import runtime
from j2208a.derive import TEST_USER_AGE


@pytest.fixture(autouse=True)
def _reset_runtime():
    """Fresh RuntimeState before each test — see file docstring."""
    runtime._runtime = runtime.RuntimeState()
    yield
    runtime._runtime = runtime.RuntimeState()


def _karvonen() -> int:
    return int((220 - TEST_USER_AGE) * 0.85)  # 161 for age 30


def _table_calls(client: MagicMock) -> list[str]:
    """Return the ordered list of table names accessed via client.table(name)."""
    return [c.args[0] for c in client.table.call_args_list]


# ──────────────────────────────────────────
# RT-1: every non-0x28 sample triggers raw_events INSERT
# ──────────────────────────────────────────
def test_RT1_raw_event_insert_on_every_sample():
    client = MagicMock()
    alert_caller = MagicMock(return_value=True)
    parsed = {
        "cmd": "0x09", "heart_rate": 70, "temperature_c": 36.5, "steps": 1234,
    }
    runtime.process_sample(
        parsed, "09 02 ...", 0x09,
        now_ts=1_700_000_000.0,
        client=client, alert_caller=alert_caller,
    )
    assert "raw_events" in _table_calls(client), (
        f"raw_events table not touched. Calls: {_table_calls(client)}"
    )


# ──────────────────────────────────────────
# RT-2: minute boundary triggers minute_summary INSERT
# ──────────────────────────────────────────
def test_RT2_minute_boundary_triggers_minute_summary_insert():
    client = MagicMock()
    alert_caller = MagicMock(return_value=True)
    # 2 samples in same minute (epoch 1700000000 = ...:20:00 UTC, second 0+10+20)
    runtime.process_sample(
        {"heart_rate": 70, "temperature_c": 36.5, "steps": 100}, "raw1", 0x09,
        now_ts=1_700_000_010.0, client=client, alert_caller=alert_caller,
    )
    runtime.process_sample(
        {"heart_rate": 71, "temperature_c": 36.6, "steps": 105}, "raw2", 0x09,
        now_ts=1_700_000_020.0, client=client, alert_caller=alert_caller,
    )
    # Cross into next minute
    runtime.process_sample(
        {"heart_rate": 72, "temperature_c": 36.7, "steps": 110}, "raw3", 0x09,
        now_ts=1_700_000_061.0, client=client, alert_caller=alert_caller,
    )
    assert "minute_summary" in _table_calls(client), (
        f"minute_summary not flushed at boundary. Calls: {_table_calls(client)}"
    )


# ──────────────────────────────────────────
# RT-3: WORN ≥ 60s + HR ≥ Karvonen → TACHY WARNING via alert_caller
# ──────────────────────────────────────────
def test_RT3_tachy_warning_when_worn_and_hr_high():
    client = MagicMock()
    # Make insert_safety_alert return a fake alert_id
    client.table.return_value.insert.return_value.execute.return_value.data = [
        {"alert_id": 42}
    ]
    alert_caller = MagicMock(return_value=True)
    high_hr = _karvonen() + 5  # over threshold

    # Drive enough WORN samples spanning > 60s so derive's WORN gate opens.
    # State machine sliding window = 5s, so dense sampling builds majority.
    # Sample every 1s for 65s.
    for i in range(70):
        ts = 1_700_000_000.0 + i
        runtime.process_sample(
            {"heart_rate": high_hr, "temperature_c": 36.5, "steps": 100 + i},
            f"raw{i}", 0x09, now_ts=ts,
            client=client, alert_caller=alert_caller,
        )

    # Find any alert_caller call with alert_type='TACHY' and severity='WARNING'
    tachy_warning_calls = [
        c for c in alert_caller.call_args_list
        if len(c.args) >= 3 and c.args[1] == "TACHY" and c.args[2] == "WARNING"
    ]
    assert tachy_warning_calls, (
        f"TACHY WARNING never emitted. alert_caller calls: "
        f"{alert_caller.call_args_list}"
    )


# ──────────────────────────────────────────
# RT-4: sustained TACHY → alert_caller fires once (transition only)
# ──────────────────────────────────────────
def test_RT4_sustained_tachy_emits_only_once():
    client = MagicMock()
    client.table.return_value.insert.return_value.execute.return_value.data = [
        {"alert_id": 7}
    ]
    alert_caller = MagicMock(return_value=True)
    high_hr = _karvonen() + 5

    # 90s of sustained high HR → only ONE TACHY transition expected
    for i in range(90):
        ts = 1_700_000_000.0 + i
        runtime.process_sample(
            {"heart_rate": high_hr, "temperature_c": 36.5, "steps": 100 + i},
            f"raw{i}", 0x09, now_ts=ts,
            client=client, alert_caller=alert_caller,
        )

    tachy_warning_calls = [
        c for c in alert_caller.call_args_list
        if len(c.args) >= 3 and c.args[1] == "TACHY" and c.args[2] == "WARNING"
    ]
    assert len(tachy_warning_calls) == 1, (
        f"Expected exactly 1 TACHY transition, got {len(tachy_warning_calls)}: "
        f"{tachy_warning_calls}"
    )


# ──────────────────────────────────────────
# RT-5: state transition → wear_state_events INSERT
# ──────────────────────────────────────────
def test_RT5_state_transition_inserts_wear_state_event():
    client = MagicMock()
    alert_caller = MagicMock(return_value=True)

    # Start with OFF condition (HR=0, low temp) — state machine majority will
    # settle on OFF after several samples.
    for i in range(8):
        ts = 1_700_000_000.0 + i
        runtime.process_sample(
            {"heart_rate": 0, "temperature_c": 31.0}, f"off{i}", 0x09,
            now_ts=ts, client=client, alert_caller=alert_caller,
        )

    # Now switch to clearly-WORN values (HR>0, temp ≥ T_WARM=35.5).
    # The 5s sliding window must accumulate enough WORN samples.
    for i in range(10):
        ts = 1_700_000_010.0 + i
        runtime.process_sample(
            {"heart_rate": 75, "temperature_c": 36.5}, f"on{i}", 0x09,
            now_ts=ts, client=client, alert_caller=alert_caller,
        )

    assert "wear_state_events" in _table_calls(client), (
        f"wear_state_events never inserted despite OFF→WORN transition. "
        f"Calls: {_table_calls(client)}"
    )


# ──────────────────────────────────────────
# RT-6: tick() detects COMMS_LOST after 120s+ silence
# ──────────────────────────────────────────
def test_RT6_tick_detects_comms_lost():
    client = MagicMock()
    client.table.return_value.insert.return_value.execute.return_value.data = [
        {"alert_id": 99}
    ]
    alert_caller = MagicMock(return_value=True)

    # First receipt — sets last_raw_ts to t=0 epoch baseline
    runtime.process_sample(
        {"heart_rate": 70, "temperature_c": 36.5}, "raw0", 0x09,
        now_ts=1_700_000_000.0,
        client=client, alert_caller=alert_caller,
    )

    # tick at +130s (≥ 120s gap) → COMMS_LOST WARNING
    runtime.tick(
        now_ts=1_700_000_130.0,
        client=client, alert_caller=alert_caller,
    )
    comms_calls = [
        c for c in alert_caller.call_args_list
        if len(c.args) >= 3 and c.args[1] == "COMMS_LOST" and c.args[2] == "WARNING"
    ]
    assert len(comms_calls) == 1, (
        f"Expected 1 COMMS_LOST WARNING from tick(), got {len(comms_calls)}: "
        f"{comms_calls}"
    )

    # Second tick at +140s — already in WARNING, no additional emission
    runtime.tick(
        now_ts=1_700_000_140.0,
        client=client, alert_caller=alert_caller,
    )
    comms_calls_after = [
        c for c in alert_caller.call_args_list
        if len(c.args) >= 3 and c.args[1] == "COMMS_LOST" and c.args[2] == "WARNING"
    ]
    assert len(comms_calls_after) == 1, (
        f"COMMS_LOST emitted again on sustained WARNING (transition rule "
        f"violated). Got {len(comms_calls_after)} total calls."
    )


# ──────────────────────────────────────────
# RT-7: process_sample alone (realistic BLE rate) NEVER emits COMMS_LOST
# ──────────────────────────────────────────
def test_RT7_process_sample_alone_never_emits_comms_lost():
    """Regression guard for the BLOCKER fix in derive.py:136 + the runtime's
    last_raw_ts-update-after-derive ordering.

    Per advisor: contract = "process_sample at realistic BLE notify rates
    (sub-second spacing) never triggers COMMS_LOST". Test reflects that — sub-
    second spacing for ~3 minutes. Long-gap behavior is tested by RT-6 via
    tick(), not here.
    """
    client = MagicMock()
    alert_caller = MagicMock(return_value=True)

    # 5-6 Hz realistic notify spacing — 0.2s apart. Run for ~150s of wall time.
    base = 1_700_000_000.0
    for i in range(750):  # 750 * 0.2s = 150s
        ts = base + (i * 0.2)
        runtime.process_sample(
            {"heart_rate": 70, "temperature_c": 36.5, "steps": 100 + i},
            f"raw{i}", 0x09, now_ts=ts,
            client=client, alert_caller=alert_caller,
        )

    comms_calls = [
        c for c in alert_caller.call_args_list
        if len(c.args) >= 3 and c.args[1] == "COMMS_LOST"
    ]
    assert not comms_calls, (
        f"BLOCKER REGRESSION: process_sample emitted COMMS_LOST despite "
        f"sub-second BLE notify spacing. tick() must be the only path for "
        f"comms-lost detection. Calls: {comms_calls}"
    )


# ──────────────────────────────────────────
# Bonus: D-14 silent drop — cmd=0x28 short-circuits
# ──────────────────────────────────────────
def test_D14_cmd_0x28_does_not_update_last_raw_ts():
    """Per advisor strict reading of D-14: 0x28 must not mask 0x09 death.

    If 0x28 updated last_raw_ts, a healthy 0x28 stream alongside a dead 0x09
    stream would prevent COMMS_LOST. Verify 0x28 leaves last_raw_ts untouched.
    """
    client = MagicMock()
    alert_caller = MagicMock(return_value=True)
    runtime._runtime.last_raw_ts = None  # cold-start

    # Send a 0x28 packet (carries HR per CMD_HRV_BLOOD_PRESSURE measure_type=2)
    runtime.process_sample(
        {"cmd": "0x28", "measure_type": "HeartRate", "heart_rate": 78,
         "temperature_c": 36.5},
        "28 02 4e ...", 0x28,
        now_ts=1_700_000_000.0,
        client=client, alert_caller=alert_caller,
    )
    assert runtime._runtime.last_raw_ts is None, (
        f"0x28 incorrectly updated last_raw_ts to "
        f"{runtime._runtime.last_raw_ts} — would mask COMMS_LOST when 0x09 dies"
    )

    # Now send 0x09 — should update last_raw_ts
    runtime.process_sample(
        {"cmd": "0x09", "heart_rate": 70, "temperature_c": 36.5},
        "09 02 ...", 0x09,
        now_ts=1_700_000_005.0,
        client=client, alert_caller=alert_caller,
    )
    assert runtime._runtime.last_raw_ts == 1_700_000_005.0, (
        f"0x09 failed to update last_raw_ts (got "
        f"{runtime._runtime.last_raw_ts})"
    )


# ──────────────────────────────────────────
# RT-9·10: Phase 7 BRIDGE-01 — devices.last_comm_at heartbeat (throttled)
# ──────────────────────────────────────────
def test_RT9_heartbeat_fires_on_cold_start():
    """Phase 7 BRIDGE-01 — 첫 packet 도착 시 즉시 1회 devices UPDATE 발사
    (last_heartbeat_ts == None → 즉시 발사 + last_heartbeat_ts 갱신)."""
    client = MagicMock()
    alert_caller = MagicMock(return_value=True)
    runtime.process_sample(
        {"heart_rate": 70, "temperature_c": 36.5}, "raw", 0x09,
        now_ts=1_700_000_000.0,
        client=client, alert_caller=alert_caller,
    )
    # devices table must be touched (heartbeat path)
    assert "devices" in _table_calls(client), (
        f"devices.last_comm_at UPDATE not fired on cold-start. "
        f"Calls: {_table_calls(client)}"
    )
    # last_heartbeat_ts updated
    assert runtime._runtime.last_heartbeat_ts == 1_700_000_000.0, (
        f"last_heartbeat_ts not updated. "
        f"Got {runtime._runtime.last_heartbeat_ts}"
    )


def test_RT10_heartbeat_throttled_within_interval():
    """Phase 7 BRIDGE-01 — HEARTBEAT_INTERVAL_SEC=60 이내 재발사 차단.
    첫 0x09 패킷 → devices UPDATE 1회. 30s 후 0x09 → 추가 UPDATE 0회.
    61s 후 0x09 → 추가 UPDATE 1회 (throttle 만료)."""
    client = MagicMock()
    alert_caller = MagicMock(return_value=True)
    # cold-start fire
    runtime.process_sample(
        {"heart_rate": 70, "temperature_c": 36.5}, "raw1", 0x09,
        now_ts=1_700_000_000.0,
        client=client, alert_caller=alert_caller,
    )
    devices_count_1 = sum(1 for c in _table_calls(client) if c == "devices")

    # +30s within throttle window — no additional UPDATE
    runtime.process_sample(
        {"heart_rate": 71, "temperature_c": 36.6}, "raw2", 0x09,
        now_ts=1_700_000_030.0,
        client=client, alert_caller=alert_caller,
    )
    devices_count_2 = sum(1 for c in _table_calls(client) if c == "devices")
    assert devices_count_2 == devices_count_1, (
        f"Throttle violated — devices UPDATE fired within 60s interval. "
        f"count_at_0s={devices_count_1}, count_at_30s={devices_count_2}"
    )

    # +61s after first — throttle window expired, fire again
    runtime.process_sample(
        {"heart_rate": 72, "temperature_c": 36.7}, "raw3", 0x09,
        now_ts=1_700_000_061.0,
        client=client, alert_caller=alert_caller,
    )
    devices_count_3 = sum(1 for c in _table_calls(client) if c == "devices")
    assert devices_count_3 == devices_count_1 + 1, (
        f"devices UPDATE not re-fired after throttle expiry. "
        f"count_at_0s={devices_count_1}, count_at_61s={devices_count_3} "
        f"(expected {devices_count_1 + 1})"
    )
