# -*- coding: utf-8 -*-
"""S2 Validate — per-field quality classifier.

Per CONTEXT.md D-07:
  - HR=0       -> WARMUP   (PPG lock-on pending; NOT a missing value)
  - HR<30/>220 -> INVALID
  - temp<25/>43 -> INVALID
  - |Δtemp|/Δt > 1.5°C/sec -> NOISY
  - else       -> GOOD

`HR is None` and `temp is None` are treated as INVALID — the upstream packet did
not carry that field. Trend-based NOISY is intentionally NOT added (D-16: the
wear-state state machine catches cumulative drops).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional


HR_INVALID_MIN = 30
HR_INVALID_MAX = 220
TEMP_INVALID_MIN = 25.0
TEMP_INVALID_MAX = 43.0
DTEMP_NOISY_PER_SEC = 1.5  # |Δtemp| > this rate (°C/sec) -> NOISY


@dataclass
class Sample:
    """One parsed sample suitable for S2 Validate.

    ts: epoch seconds (float). heart_rate / temperature_c may be None when the
    packet did not include the field (e.g. short 0x09 response).
    """

    ts: float
    heart_rate: Optional[int]
    temperature_c: Optional[float]


def validate_sample(sample: Sample, prev: Optional[Sample]) -> dict:
    """Return per-field quality dict.

    Keys: 'heart_rate', 'temperature_c'.
    Values ∈ {'GOOD', 'WARMUP', 'NOISY', 'INVALID'} per CONTEXT.md D-07.
    """
    q: dict = {}

    # ---------------- HR ----------------
    if sample.heart_rate is None:
        q["heart_rate"] = "INVALID"
    elif sample.heart_rate == 0:
        q["heart_rate"] = "WARMUP"
    elif sample.heart_rate < HR_INVALID_MIN or sample.heart_rate > HR_INVALID_MAX:
        q["heart_rate"] = "INVALID"
    else:
        q["heart_rate"] = "GOOD"

    # ---------------- temperature ----------------
    if sample.temperature_c is None:
        q["temperature_c"] = "INVALID"
    elif sample.temperature_c < TEMP_INVALID_MIN or sample.temperature_c > TEMP_INVALID_MAX:
        q["temperature_c"] = "INVALID"
    elif prev is not None and prev.temperature_c is not None:
        dt = sample.ts - prev.ts
        if dt > 0:
            rate = abs(sample.temperature_c - prev.temperature_c) / dt
            if rate > DTEMP_NOISY_PER_SEC:
                q["temperature_c"] = "NOISY"
            else:
                q["temperature_c"] = "GOOD"
        else:
            q["temperature_c"] = "GOOD"
    else:
        q["temperature_c"] = "GOOD"

    return q
