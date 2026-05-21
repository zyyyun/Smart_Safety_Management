# -*- coding: utf-8 -*-
"""Unit tests for j2208a.derive — D-09·D-10 risk classification.

D-1..D-7 cover the 3 risk types + WORN gate + transition-only emission +
resolution alerts. D-5b is the cold-start pre-receipt grace regression guard
(BLOCKER fix: COMMS_LOST must NOT fire when last_raw_ts is None).
"""

from j2208a.derive import (
    DeriveContext,
    TEST_USER_AGE,
    derive_alerts,
    _karvonen_threshold,
)


# ---------------- TACHY ----------------

def test_d1_tachy_fires_when_hr_above_karvonen():
    """D-1: TEST_USER_AGE=30 -> threshold = (220-30)*0.85 = 161.5.
    HR_60s_median=170 > 161.5 -> TACHY/WARNING fires.
    """
    assert TEST_USER_AGE == 30
    assert _karvonen_threshold(30) == 161.5
    ctx = DeriveContext()
    now_ts = 1_700_000_000.0
    # Pre-set worn_since_ts so WORN gate is satisfied (200s of WORN).
    ctx.worn_since_ts = now_ts - 200.0
    events = derive_alerts(ctx, now_ts, "WORN", hr_60s_median=170,
                           last_raw_ts=now_ts - 1.0)
    tachy = [e for e in events if e.alert_type == "TACHY"]
    assert len(tachy) == 1
    assert tachy[0].severity == "WARNING"
    assert tachy[0].is_resolution is False


def test_d2_tachy_does_not_fire_below_threshold():
    """D-2: HR_60s_median=160 < 161.5 -> no TACHY."""
    ctx = DeriveContext()
    now_ts = 1_700_000_000.0
    ctx.worn_since_ts = now_ts - 200.0
    events = derive_alerts(ctx, now_ts, "WORN", hr_60s_median=160,
                           last_raw_ts=now_ts - 1.0)
    tachy = [e for e in events if e.alert_type == "TACHY"]
    assert len(tachy) == 0


# ---------------- REMOVED ----------------

def test_d3_removed_fires_after_off_300s():
    """D-3: wear_state=OFF for 5+ minutes (300s) -> REMOVED/WARNING fires."""
    ctx = DeriveContext()
    now_ts = 1_700_000_000.0
    # Pre-set off_since_ts to simulate 300+ s of OFF
    ctx.off_since_ts = now_ts - 350.0
    events = derive_alerts(ctx, now_ts, "OFF", hr_60s_median=None,
                           last_raw_ts=now_ts - 1.0)
    removed = [e for e in events if e.alert_type == "REMOVED"]
    assert len(removed) == 1
    assert removed[0].severity == "WARNING"


def test_d4_removed_does_not_re_fire_during_sustained_off():
    """D-4: same OFF sustained -> only 1 transition emission, NOT 2."""
    ctx = DeriveContext()
    now_ts = 1_700_000_000.0
    ctx.off_since_ts = now_ts - 350.0
    # First call: should fire
    events1 = derive_alerts(ctx, now_ts, "OFF", hr_60s_median=None,
                            last_raw_ts=now_ts - 1.0)
    removed1 = [e for e in events1 if e.alert_type == "REMOVED"]
    assert len(removed1) == 1
    # Second call (still OFF, still > 300s) -> should NOT re-fire
    events2 = derive_alerts(ctx, now_ts + 5.0, "OFF", hr_60s_median=None,
                            last_raw_ts=now_ts - 1.0)
    removed2 = [e for e in events2 if e.alert_type == "REMOVED"]
    assert len(removed2) == 0, "REMOVED must not re-fire during sustained OFF"


# ---------------- COMMS_LOST ----------------

def test_d5_comms_lost_with_explicit_stale_last_raw_ts():
    """D-5: stale last_raw_ts (130s ago) -> COMMS_LOST/WARNING fires once.

    Key: last_raw_ts is NOT None — it's an old value. Pre-receipt grace is
    only triggered by None.
    """
    ctx = DeriveContext()
    now_ts = 1_700_000_000.0
    ctx.worn_since_ts = now_ts - 200.0  # WORN 200s — TACHY gate satisfied
    events = derive_alerts(ctx, now_ts, "WORN", hr_60s_median=70,
                           last_raw_ts=now_ts - 130.0)
    comms = [e for e in events if e.alert_type == "COMMS_LOST"]
    assert len(comms) == 1
    assert comms[0].severity == "WARNING"


def test_d5b_pre_receipt_grace_no_comms_lost():
    """D-5b (BLOCKER #1 regression guard): cold start with last_raw_ts=None
    must NOT emit COMMS_LOST. Otherwise every fresh process start triggers a
    phantom WARNING -> FCM push storm.
    """
    ctx = DeriveContext()
    now_ts = 1_700_000_000.0
    events = derive_alerts(ctx, now_ts, "TRANSIENT",
                           hr_60s_median=None, last_raw_ts=None)
    comms = [e for e in events if e.alert_type == "COMMS_LOST"]
    assert len(comms) == 0, "pre-receipt grace: COMMS_LOST must NOT fire"
    assert ctx.last_severity["COMMS_LOST"] == "NORMAL", \
        "last_severity must remain NORMAL — no phantom transition"


# ---------------- WORN gate scope ----------------

def test_d6_worn_gate_only_blocks_tachy_not_other_alerts():
    """D-6 (WORN 60s gate scope): WORN 30s (< 60s) silences TACHY, but REMOVED
    and COMMS_LOST are independent of the WORN gate. The plan worded D-6 as
    'TACHY-only gate' — verify by constructing a state where:
      - WORN gate would silence TACHY (worn_since_ts is short)
      - OFF tracking has OFF >= 5min so REMOVED could fire (state change to OFF)
      - last_raw_ts is 130s old so COMMS_LOST can fire

    Since wear_state can't be both WORN and OFF in the same call, exercise the
    gate scope by:
      a) call with WORN + short worn_since + high HR -> no TACHY (gate works)
      b) call with OFF + long off_since + stale raw -> REMOVED + COMMS_LOST fire
    """
    # Part (a): WORN-but-short -> TACHY gated even with high HR.
    ctx_a = DeriveContext()
    now_ts_a = 1_700_000_000.0
    ctx_a.worn_since_ts = now_ts_a - 30.0  # only 30s of WORN -> < 60s gate
    events_a = derive_alerts(ctx_a, now_ts_a, "WORN", hr_60s_median=170,
                             last_raw_ts=now_ts_a - 1.0)
    tachy_a = [e for e in events_a if e.alert_type == "TACHY"]
    assert len(tachy_a) == 0, "TACHY gated by WORN < 60s"

    # Part (b): OFF + long off_since + stale raw -> REMOVED + COMMS_LOST both
    # fire independently of the WORN gate.
    ctx_b = DeriveContext()
    now_ts_b = 1_700_000_000.0
    ctx_b.off_since_ts = now_ts_b - 350.0
    events_b = derive_alerts(ctx_b, now_ts_b, "OFF", hr_60s_median=None,
                             last_raw_ts=now_ts_b - 130.0)
    removed_b = [e for e in events_b if e.alert_type == "REMOVED"]
    comms_b = [e for e in events_b if e.alert_type == "COMMS_LOST"]
    assert len(removed_b) == 1, "REMOVED is NOT gated by WORN duration"
    assert len(comms_b) == 1, "COMMS_LOST is NOT gated by WORN duration"


# ---------------- Resolution ----------------

def test_d7_resolution_alert_on_recovery():
    """D-7: TACHY fires, then HR returns below threshold -> resolution alert
    (severity='NORMAL', is_resolution=True) emitted once.
    """
    ctx = DeriveContext()
    now_ts = 1_700_000_000.0
    ctx.worn_since_ts = now_ts - 200.0
    # Step 1: TACHY fires
    events1 = derive_alerts(ctx, now_ts, "WORN", hr_60s_median=170,
                            last_raw_ts=now_ts - 1.0)
    assert any(e.alert_type == "TACHY" and e.severity == "WARNING" for e in events1)
    assert ctx.last_severity["TACHY"] == "WARNING"
    # Step 2: HR drops back to safe -> resolution
    events2 = derive_alerts(ctx, now_ts + 60.0, "WORN", hr_60s_median=120,
                            last_raw_ts=now_ts + 59.0)
    res = [e for e in events2 if e.alert_type == "TACHY"]
    assert len(res) == 1
    assert res[0].severity == "NORMAL"
    assert res[0].is_resolution is True
