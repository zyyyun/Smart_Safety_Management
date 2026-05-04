# -*- coding: utf-8 -*-
"""Wear-state state machine.

Per CONTEXT.md D-05·D-06:
  - 5 states: OFF, WARMUP, TRANSIENT, WORN, ABNORMAL
  - 5-second sliding window majority (≥ 3/5 same -> state)
  - Tie -> TRANSIENT (unstable transition)

Thresholds are Python module constants (D-06). v1.1 may externalize after §8
calibration experiments.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from typing import Deque, Optional, Tuple


# Per CONTEXT.md D-06 — Python constants (v1.1 externalization)
T_OFF  = 33.5     # °C, case-external equilibrium
T_WARM = 35.5     # °C, skin contact
N1_OFF_ENTRY_SEC  = 30.0   # OFF entry: HR=0 + temp ≤ T_OFF for N1 seconds
N2_WORN_ENTRY_SEC = 30.0   # WORN entry: HR>0 stable + temp ≥ T_WARM for N2 seconds
SLIDING_WINDOW_SEC = 5.0   # 5-second sliding window majority

STATES = ("OFF", "WARMUP", "TRANSIENT", "WORN", "ABNORMAL")


def _instantaneous_state(hr: Optional[int], temp: Optional[float]) -> str:
    """Compute the instantaneous wear state for one sample.

    Long-duration entry (N1/N2 seconds) is tracked by the *caller* (the sliding
    window majority handles short-term smoothing; long-duration entry is the
    derive-stage gate, e.g. WORN ≥ 60s for TACHY evaluation).
    """
    if hr is None or temp is None:
        return "TRANSIENT"
    # ABNORMAL: clearly out-of-physiological-range values that survived S2
    # (defense-in-depth — S2 already filters HR>220 / temp>43 as INVALID, but
    # if those leak through we mark wear-state ABNORMAL).
    if hr > 220 or temp > 43.0:
        return "ABNORMAL"
    if hr == 0 and temp <= T_OFF:
        return "OFF"
    if hr > 0 and temp >= T_WARM:
        return "WORN"
    # Otherwise: WARMUP (worn but PPG not locked, or skin warming up)
    return "WARMUP"


@dataclass
class StateMachine:
    """Sliding-window majority classifier."""

    window: Deque[Tuple[float, str]] = field(default_factory=deque)

    def update(self, ts: float, hr: Optional[int], temp: Optional[float]) -> str:
        inst = _instantaneous_state(hr, temp)
        self.window.append((ts, inst))
        cutoff = ts - SLIDING_WINDOW_SEC
        while self.window and self.window[0][0] < cutoff:
            self.window.popleft()
        return self._majority()

    def _majority(self) -> str:
        if not self.window:
            return "TRANSIENT"
        counts: dict = {}
        for _, s in self.window:
            counts[s] = counts.get(s, 0) + 1
        sorted_counts = sorted(counts.items(), key=lambda kv: -kv[1])
        # Tie at the top -> TRANSIENT (unstable)
        if len(sorted_counts) > 1 and sorted_counts[0][1] == sorted_counts[1][1]:
            return "TRANSIENT"
        return sorted_counts[0][0]


def classify_state(samples: list) -> str:
    """Convenience wrapper for tests. samples = [(ts, hr, temp), ...]."""
    sm = StateMachine()
    last = "TRANSIENT"
    for ts, hr, temp in samples:
        last = sm.update(ts, hr, temp)
    return last
