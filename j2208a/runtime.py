# -*- coding: utf-8 -*-
"""BLE notify -> j2208a pipeline integration entry point.

Per CONTEXT.md D-04 (인라인 통합) + D-09 (전이 알림) + D-12 (BLE client calls
Edge Function with service_role) + D-14 (cmd=0x28 silent drop).

`scripts/j2208a_sensor_reader.py:_on_notify` calls `process_sample` for every
BLE notification. The runtime owns the in-process state (1-minute buffer,
state machine, derive context, last_raw_ts cold-start grace) and persists to
Supabase via `supabase_writer` helpers. Safety alerts trigger an FCM push by
calling the `notifications` Edge Function with `action='watch-alert'`.

Threading model: `process_sample` is sync (called from bleak's notify callback,
which is sync). `tick()` is also sync (called by the heartbeat asyncio task
via `_heartbeat_loop` in sensor_reader.py). Both share the module-level
`_runtime` instance — safe under asyncio's single-thread cooperative model.

D-14 strict reading (per advisor): cmd == 0x28 (CMD_HRV_BLOOD_PRESSURE) must
NOT update `last_raw_ts` either. Otherwise a healthy 0x28 stream alongside a
dead 0x09 stream would mask COMMS_LOST. The short-circuit lives BEFORE any
`last_raw_ts` assignment.

BLOCKER fix (carried from 04-02 derive.py:136): `last_raw_ts is None` is the
cold-start sentinel — derive_alerts skips COMMS_LOST evaluation in that case.
We initialize `RuntimeState.last_raw_ts = None` and only set it after the
first 0x09 (or other non-0x28) packet has been processed end-to-end.

RT-7 contract: `process_sample` called at realistic BLE notify rates (sub-
second spacing) NEVER triggers COMMS_LOST — only the heartbeat `tick()`,
called every ~10s when no raw arrives, can detect long gaps.
"""

from __future__ import annotations

import os
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable, Optional

from .aggregate import aggregate_minute
from .derive import AlertEvent, DeriveContext, derive_alerts
from .state_machine import StateMachine
from .supabase_writer import (
    CMD_HRV_BLOOD_PRESSURE,
    call_watch_alert_edge_function,
    insert_minute_summary,
    insert_raw_event,
    insert_safety_alert,
    insert_wear_state_event,
    update_safety_alert_resolved,
)
from .validate import Sample, validate_sample


# ──────────────────────────────────────────
# Environment configuration
# ──────────────────────────────────────────
DEVICE_ID = int(os.environ.get("TEST_DEVICE_ID", "1"))
USER_ID = os.environ.get("TEST_USER_ID", "testuser1")


# ──────────────────────────────────────────
# Runtime state (module-level singleton)
# ──────────────────────────────────────────
@dataclass
class RuntimeState:
    """Process-memory state for one BLE session.

    Reset between tests with `runtime._runtime = RuntimeState()` to avoid
    nondeterministic test ordering (DeriveContext.last_severity / state machine
    window leak otherwise).
    """

    sm: StateMachine = field(default_factory=StateMachine)
    derive_ctx: DeriveContext = field(default_factory=DeriveContext)
    prev_validate_sample: Optional[Sample] = None
    current_minute_floor: Optional[int] = None
    minute_buffer: list = field(default_factory=list)
    last_state: str = "TRANSIENT"
    # last_raw_ts == None ≡ cold-start (no raw ever arrived). Per derive.py:136
    # COMMS_LOST is skipped when None — prevents phantom WARNING on first
    # process_sample call.
    last_raw_ts: Optional[float] = None
    # Most recent alert_id per type — used to attach Edge Function payload to
    # the matching DB row when emitting a resolution event.
    last_alert_ids: dict = field(default_factory=lambda: {
        "TACHY": None, "REMOVED": None, "COMMS_LOST": None,
    })


_runtime = RuntimeState()
_client: Any = None


def _get_client() -> Any:
    """Lazy supabase client — only built on first use (production)."""
    global _client
    if _client is None:
        from .supabase_writer import get_client
        _client = get_client()
    return _client


def _to_iso(ts: float) -> str:
    return datetime.fromtimestamp(ts, tz=timezone.utc).isoformat()


def _alert_message(
    alert_type: str,
    severity: str,
    reason: dict,
    is_resolution: bool,
) -> tuple[str, str]:
    """Korean-language title/body for FCM push (matches Android channel
    `fcm_default_channel` in MyFirebaseMessagingService.kt:48)."""
    if is_resolution:
        return (
            f"[정상 복귀] {alert_type}",
            f"{alert_type} 해소됨",
        )
    if alert_type == "TACHY":
        hr = reason.get("hr_60s_median")
        thr = reason.get("threshold")
        return (
            "[주의] 빈맥 의심",
            f"심박 60s median={hr} (threshold={thr})",
        )
    if alert_type == "REMOVED":
        off = reason.get("off_duration_sec", 0) or 0
        return (
            "[주의] 시계 탈착 감지",
            f"OFF {off:.0f}s 지속",
        )
    if alert_type == "COMMS_LOST":
        age = reason.get("last_raw_age_sec") or 0
        return (
            "[주의] 통신 두절",
            f"마지막 raw 수신 {age:.0f}s 전",
        )
    return (f"[{severity}] {alert_type}", str(reason))


# ──────────────────────────────────────────
# Public entry points
# ──────────────────────────────────────────
def process_sample(
    parsed: dict,
    raw_hex: str,
    cmd: int,
    now_ts: Optional[float] = None,
    client: Any = None,
    alert_caller: Optional[Callable] = None,
) -> None:
    """Process one BLE notify packet end-to-end.

    Order matters (per advisor + BLOCKER fix in derive.py:136):
      1. cmd == 0x28 → silent drop (D-14). Do NOT update last_raw_ts; do NOT
         enter S2/S3. Otherwise a healthy 0x28 stream would mask COMMS_LOST
         when 0x09 dies.
      2. insert_raw_event for non-0x28.
      3. If parsed has neither HR nor temp (e.g. 0x13 battery response, 0x22
         MAC response) → still update last_raw_ts (link is alive) and return.
      4. S2 validate → state machine update → wear_state_events on transition.
      5. Append to 1-min buffer; flush previous minute if boundary crossed.
      6. derive_alerts BEFORE updating last_raw_ts (otherwise gap is always 0).
      7. last_raw_ts = now_ts (for the NEXT call's gap measurement and for
         tick()'s COMMS_LOST evaluation).
    """
    if now_ts is None:
        now_ts = time.time()
    client = client or _get_client()
    alert_caller = alert_caller or call_watch_alert_edge_function
    rt = _runtime

    ts_iso = _to_iso(now_ts)

    # D-14 strict (per advisor): cmd == 0x28 is dual-stream duplicate of 0x09.
    # supabase_writer.insert_raw_event silently drops it for raw_events, but we
    # ALSO must not feed it to S2/S3 (D-14: "S2/S3 입력에도 사용하지 않음")
    # AND must not update last_raw_ts (otherwise 0x28-alive masks 0x09-dead).
    if cmd == CMD_HRV_BLOOD_PRESSURE:
        # Defensive call to insert_raw_event — writer silently drops 0x28; this
        # keeps a single canonical drop site if D-14 ever changes.
        try:
            insert_raw_event(client, DEVICE_ID, ts_iso, cmd, raw_hex, parsed)
        except Exception as e:
            print(f"[runtime] insert_raw_event(0x28) failed: {e}")
        return

    # Persist raw (non-0x28). Failures are logged but do not abort the
    # pipeline — BLE link liveness is the higher-value signal.
    try:
        insert_raw_event(client, DEVICE_ID, ts_iso, cmd, raw_hex, parsed)
    except Exception as e:
        print(f"[runtime] insert_raw_event failed: {e}")

    hr = parsed.get("heart_rate")
    temp = parsed.get("temperature_c")
    if hr is None and temp is None:
        # Auxiliary packet (battery / MAC / version / time response).
        # Link is alive — update last_raw_ts so COMMS_LOST is not falsely
        # tripped during a sequence of these. No S2/S3/derive needed.
        rt.last_raw_ts = now_ts
        return

    # ── S2 Validate ──
    sample = Sample(ts=now_ts, heart_rate=hr, temperature_c=temp)
    quality = validate_sample(sample, rt.prev_validate_sample)
    rt.prev_validate_sample = sample

    # ── State Machine ──
    new_state = rt.sm.update(
        now_ts,
        hr if hr is not None else 0,
        temp if temp is not None else 0.0,
    )
    if new_state != rt.last_state:
        try:
            insert_wear_state_event(
                client, DEVICE_ID, ts_iso,
                rt.last_state, new_state,
                {"hr": hr, "temp": temp},
            )
        except Exception as e:
            print(f"[runtime] insert_wear_state_event failed: {e}")
        rt.last_state = new_state

    # ── Minute boundary detection / flush ──
    minute_floor = int(now_ts // 60)
    if rt.current_minute_floor is None:
        rt.current_minute_floor = minute_floor
    if minute_floor != rt.current_minute_floor:
        _flush_minute(rt, client)
        rt.current_minute_floor = minute_floor
        rt.minute_buffer = []

    rt.minute_buffer.append({
        "ts": now_ts,
        "hr": hr,
        "temp": temp,
        "steps": parsed.get("steps"),
        "quality_hr": quality.get("heart_rate"),
        "quality_temp": quality.get("temperature_c"),
    })

    # ── HR_60s_median for TACHY (Karvonen) ──
    good_hr = [
        s["hr"] for s in rt.minute_buffer
        if s.get("quality_hr") == "GOOD" and s.get("hr") is not None
    ]
    hr_60s_median = int(sum(good_hr) / len(good_hr)) if good_hr else None

    # ── S4 Derive (BEFORE updating last_raw_ts so the previous gap is seen) ──
    events = derive_alerts(
        rt.derive_ctx, now_ts, new_state, hr_60s_median, rt.last_raw_ts,
    )
    for ev in events:
        _emit_alert(client, alert_caller, ev, ts_iso, rt)

    # ── Now update last_raw_ts for the NEXT call's gap measurement ──
    rt.last_raw_ts = now_ts


def tick(
    now_ts: Optional[float] = None,
    client: Any = None,
    alert_caller: Optional[Callable] = None,
) -> None:
    """Heartbeat — periodic call (~10s) to detect time-based transitions even
    when no raw arrives.

    Required for:
      - COMMS_LOST: raw absent ≥ 120s (process_sample alone never sees this
        since each call is bounded by sub-second BLE notify spacing).
      - REMOVED: OFF state ≥ 5 min — also requires time progression with no
        new state-changing samples.

    The heartbeat reuses the existing minute buffer's GOOD HR median so TACHY
    evaluation (which is gated on WORN ≥ 60s) doesn't need a fresh sample to
    re-evaluate.
    """
    if now_ts is None:
        now_ts = time.time()
    client = client or _get_client()
    alert_caller = alert_caller or call_watch_alert_edge_function
    rt = _runtime

    good_hr = [
        s["hr"] for s in rt.minute_buffer
        if s.get("quality_hr") == "GOOD" and s.get("hr") is not None
    ]
    hr_60s_median = int(sum(good_hr) / len(good_hr)) if good_hr else None

    events = derive_alerts(
        rt.derive_ctx, now_ts, rt.last_state, hr_60s_median, rt.last_raw_ts,
    )
    if not events:
        return
    ts_iso = _to_iso(now_ts)
    for ev in events:
        _emit_alert(client, alert_caller, ev, ts_iso, rt)


# ──────────────────────────────────────────
# Internal helpers
# ──────────────────────────────────────────
def _flush_minute(rt: RuntimeState, client: Any) -> None:
    """Aggregate the current minute's buffer and persist (D-08 + D-17)."""
    if not rt.minute_buffer:
        return
    result = aggregate_minute(rt.minute_buffer, rt.last_state)
    minute_ts = datetime.fromtimestamp(
        rt.current_minute_floor * 60, tz=timezone.utc,
    ).isoformat()
    try:
        insert_minute_summary(
            client, DEVICE_ID, minute_ts,
            result.hr_median, result.temp_median, result.temp_iqr,
            result.steps_delta, result.dominant_state, result.good_ratio,
        )
    except Exception as e:
        print(f"[runtime] insert_minute_summary failed: {e}")


def _emit_alert(
    client: Any,
    alert_caller: Callable,
    ev: AlertEvent,
    ts_iso: str,
    rt: RuntimeState,
) -> None:
    """Persist one alert transition + push FCM via Edge Function (D-11·D-12)."""
    if ev.is_resolution:
        # 경보→정상 transition: update existing row's resolved_at, do NOT
        # insert a new safety_alerts row (DB CHECK rejects severity='NORMAL').
        try:
            update_safety_alert_resolved(
                client, DEVICE_ID, ev.alert_type, ts_iso,
            )
        except Exception as e:
            print(f"[runtime] update_safety_alert_resolved failed: {e}")
        title, body = _alert_message(
            ev.alert_type, ev.severity, ev.reason, True,
        )
        alert_id = rt.last_alert_ids.get(ev.alert_type) or 0
    else:
        try:
            alert_id = insert_safety_alert(
                client, DEVICE_ID, ev.alert_type,
                ev.severity, ev.reason, ts_iso,
            )
            rt.last_alert_ids[ev.alert_type] = alert_id
        except Exception as e:
            print(f"[runtime] insert_safety_alert failed: {e}")
            alert_id = -1
        title, body = _alert_message(
            ev.alert_type, ev.severity, ev.reason, False,
        )

    # Edge Function severity ∈ {CAUTION, WARNING, DANGER, NORMAL} —
    # whitelist enforced at notifications/index.ts watch-alert action.
    sev_for_edge = "NORMAL" if ev.is_resolution else ev.severity
    try:
        alert_caller(USER_ID, ev.alert_type, sev_for_edge, alert_id, title, body)
    except Exception as e:
        print(f"[runtime] call_watch_alert_edge_function failed: {e}")
