# -*- coding: utf-8 -*-
"""Unit tests for j2208a.state_machine — D-05·D-06 wear-state classifier.

5 wear states + 5-second sliding-window majority + tie -> TRANSIENT.
"""

from j2208a.state_machine import (
    StateMachine,
    classify_state,
    T_OFF,
    T_WARM,
)


def test_sm1_off_after_30s_of_hr0_and_low_temp():
    """SM-1 (OFF entry): HR=0 + temp ≤ T_OFF for 30+ seconds -> OFF."""
    # 31 one-second samples spanning t=0..30 — ample for 5s sliding window.
    samples = [(float(t), 0, 32.0) for t in range(31)]
    assert classify_state(samples) == "OFF"


def test_sm2_worn_after_30s_of_hr_pos_and_warm():
    """SM-2 (WORN entry): HR>0 + temp ≥ T_WARM for 30+ seconds -> WORN."""
    samples = [(float(t), 70, 36.0) for t in range(31)]
    assert classify_state(samples) == "WORN"


def test_sm3_warmup_when_temp_between_off_and_warm():
    """SM-3 (WARMUP): T_OFF < temp < T_WARM -> WARMUP (worn but warming)."""
    # temp=34.0 is between T_OFF=33.5 and T_WARM=35.5
    samples = [(float(t), 0, 34.0) for t in range(31)]
    assert classify_state(samples) == "WARMUP"


def test_sm4_majority_window_resolves_to_worn():
    """SM-4 (sliding window majority): [WORN, WORN, TRANSIENT, WORN, WORN] -> WORN."""
    sm = StateMachine()
    # Manually inject 5 instantaneous classifications across a 5s window.
    # Simulate by feeding samples that produce the desired instantaneous states.
    sm.window.clear()
    sm.window.append((0.0, "WORN"))
    sm.window.append((1.0, "WORN"))
    sm.window.append((2.0, "TRANSIENT"))
    sm.window.append((3.0, "WORN"))
    sm.window.append((4.0, "WORN"))
    assert sm._majority() == "WORN"


def test_sm5_tie_resolves_to_transient():
    """SM-5 (tie -> TRANSIENT): [WORN, WARMUP, TRANSIENT, OFF, WORN] = 2 WORN, 1 each
    of others — top is WORN-only with count 2 (no tie).
    Use a true tie: [WORN, WARMUP, WORN, WARMUP, TRANSIENT] -> WORN/WARMUP both 2.
    """
    sm = StateMachine()
    sm.window.clear()
    sm.window.append((0.0, "WORN"))
    sm.window.append((1.0, "WARMUP"))
    sm.window.append((2.0, "WORN"))
    sm.window.append((3.0, "WARMUP"))
    sm.window.append((4.0, "TRANSIENT"))
    # WORN=2, WARMUP=2, TRANSIENT=1 -> tie at top -> TRANSIENT
    assert sm._majority() == "TRANSIENT"
