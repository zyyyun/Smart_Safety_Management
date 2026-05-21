# -*- coding: utf-8 -*-
"""S4 Derive — risk classification with transition-only emission.

Per CONTEXT.md D-09 + D-10:
  - TACHY (Karvonen):     HR_60s_median ≥ (220 - AGE) × 0.85, gated on WORN ≥ 60s
  - REMOVED (탈착):        wear_state OFF for ≥ 5 minutes (300s)
  - COMMS_LOST (통신두절): no raw_event for ≥ 120s

Transition-only emission (PROJECT.md key decision, J2208A §6):
  Alerts fire on 정상↔주의↔경보 *transitions* only, never on sustained levels.
  Resolution alerts (경보→정상) carry severity='NORMAL' + is_resolution=True.

Pre-receipt grace (BLOCKER fix): `last_raw_ts is None` ≡ "no raw has ever
arrived yet" (cold-start). COMMS_LOST evaluation is *skipped* in that case —
otherwise every first BLE notify would trigger a phantom WARNING -> FCM push.
After the first raw, last_raw_ts is set and ≥120s gaps fire normally.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional


# Per CONTEXT.md D-10 — testuser1's age. Operator-tunable.
TEST_USER_AGE = 30

WORN_GATE_SEC      = 60.0   # WORN ≥ this many seconds -> TACHY may evaluate (D-09)
OFF_REMOVED_SEC    = 300.0  # OFF ≥ 5 minutes -> 탈착 (D-09)
COMMS_LOST_SEC     = 120.0  # raw absent ≥ 120s -> 통신두절 (D-09)
TACHY_KARVONEN     = 0.85   # HR ≥ (220 - AGE) × 0.85 -> 빈맥 (D-09)


class Severity(str, Enum):
    """Severity levels emitted by derive_alerts.

    DB CHECK constraint accepts CAUTION / WARNING / DANGER (D-01). NORMAL is an
    in-process sentinel for "no alert / resolved" — it is NEVER inserted into
    safety_alerts; the writer uses update_safety_alert_resolved instead.
    """

    NORMAL  = "NORMAL"   # resolved / no-alert sentinel (writer-side only)
    CAUTION = "CAUTION"
    WARNING = "WARNING"
    DANGER  = "DANGER"


ALERT_TYPES = ("TACHY", "REMOVED", "COMMS_LOST")


@dataclass
class AlertEvent:
    """One transition emitted by derive_alerts (caller persists)."""

    alert_type: str          # 'TACHY' | 'REMOVED' | 'COMMS_LOST'
    severity: str            # 'CAUTION' | 'WARNING' | 'DANGER' | 'NORMAL'
    is_resolution: bool      # True iff transition is back to NORMAL
    reason: dict


@dataclass
class DeriveContext:
    """Process-memory transition state (per advisor guidance — no DB queries)."""

    last_severity: dict = field(default_factory=lambda: {
        "TACHY": "NORMAL", "REMOVED": "NORMAL", "COMMS_LOST": "NORMAL",
    })
    worn_since_ts: Optional[float] = None
    off_since_ts: Optional[float] = None


def _karvonen_threshold(age: int) -> float:
    return (220 - age) * TACHY_KARVONEN


def derive_alerts(
    ctx: DeriveContext,
    now_ts: float,
    wear_state: str,                 # output of state_machine.classify_state
    hr_60s_median: Optional[int],
    last_raw_ts: Optional[float],
) -> List[AlertEvent]:
    """Evaluate the 3 risk types and emit transition events only.

    Per CONTEXT.md D-09. The WORN ≥ 60s gate applies *only to TACHY* — REMOVED
    is governed by OFF duration and COMMS_LOST is a BLE-link concern, both
    independent of how long the device has been WORN.
    """
    alerts: List[AlertEvent] = []

    # ---------------- WORN duration tracking ----------------
    if wear_state == "WORN":
        if ctx.worn_since_ts is None:
            ctx.worn_since_ts = now_ts
    else:
        ctx.worn_since_ts = None

    # ---------------- OFF duration tracking ----------------
    if wear_state == "OFF":
        if ctx.off_since_ts is None:
            ctx.off_since_ts = now_ts
    else:
        ctx.off_since_ts = None

    worn_long_enough = (
        ctx.worn_since_ts is not None
        and (now_ts - ctx.worn_since_ts) >= WORN_GATE_SEC
    )

    # ---------------- TACHY (WORN ≥ 60s only) ----------------
    new_tachy_sev = "NORMAL"
    if worn_long_enough and hr_60s_median is not None:
        if hr_60s_median >= _karvonen_threshold(TEST_USER_AGE):
            new_tachy_sev = "WARNING"
    alerts.extend(_emit_transition(
        ctx, "TACHY", new_tachy_sev, now_ts,
        {"hr_60s_median": hr_60s_median,
         "threshold": _karvonen_threshold(TEST_USER_AGE)},
    ))

    # ---------------- REMOVED (OFF ≥ 5 min) ----------------
    new_removed_sev = "NORMAL"
    if ctx.off_since_ts is not None and (now_ts - ctx.off_since_ts) >= OFF_REMOVED_SEC:
        new_removed_sev = "WARNING"
    alerts.extend(_emit_transition(
        ctx, "REMOVED", new_removed_sev, now_ts,
        {"off_duration_sec": (now_ts - ctx.off_since_ts) if ctx.off_since_ts else 0},
    ))

    # ---------------- COMMS_LOST (raw absent ≥ 120s) ----------------
    # last_raw_ts is None ≡ "no raw has ever arrived" (cold-start grace). Skip
    # COMMS_LOST evaluation in that case — emitting a WARNING here would push
    # phantom FCM alerts on every fresh process start.
    new_comms_sev = "NORMAL"
    if last_raw_ts is not None and (now_ts - last_raw_ts) >= COMMS_LOST_SEC:
        new_comms_sev = "WARNING"
    alerts.extend(_emit_transition(
        ctx, "COMMS_LOST", new_comms_sev, now_ts,
        {"last_raw_age_sec": (now_ts - last_raw_ts) if last_raw_ts is not None else None},
    ))

    return alerts


def _emit_transition(
    ctx: DeriveContext,
    alert_type: str,
    new_sev: str,
    now_ts: float,
    reason: dict,
) -> List[AlertEvent]:
    """Emit ONE event iff severity transitioned. Sustained level -> 0 events."""
    prev = ctx.last_severity[alert_type]
    if new_sev == prev:
        return []
    ctx.last_severity[alert_type] = new_sev
    is_resolution = (new_sev == "NORMAL")
    return [AlertEvent(
        alert_type=alert_type,
        severity=new_sev,
        is_resolution=is_resolution,
        reason=reason,
    )]
