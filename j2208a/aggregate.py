# -*- coding: utf-8 -*-
"""S3 Aggregate — 5s / 30s / 1-minute windows.

Per CONTEXT.md D-08 + D-17 (50% -> 30% threshold correction):
  - HR: 5s median per chunk -> 1-min median over 12 chunks (here computed
    directly over all GOOD HR samples in the minute — equivalent for median).
  - temp: 30s median + IQR per chunk -> 1-min median+IQR over 2 chunks.
  - steps: 1-minute delta (last - first).
  - good_ratio < MIN_GOOD_RATIO (0.30) -> aggregates set to None (NULL).
"""

from __future__ import annotations

import statistics
from dataclasses import dataclass
from typing import List, Optional


# Per CONTEXT.md D-17 — empirical correction (50% -> 30%).
# 20s-cycle `cmd_health_measure(2, True)` restart drops 1-2 samples per restart
# into HR=0 (PPG re-lock WARMUP) -> ≈ 3-5% per minute. 50% threshold was too
# strict — would mark normal operation as missing. Lowered to 30%.
MIN_GOOD_RATIO = 0.30  # < this ratio -> NULL aggregates (D-17)


@dataclass
class MinuteResult:
    hr_median: Optional[int]
    temp_median: Optional[float]
    temp_iqr: Optional[float]
    steps_delta: Optional[int]
    good_ratio: float
    dominant_state: Optional[str]


def _iqr(values: List[float]) -> float:
    """Inter-quartile range (Q3 - Q1) using sorted-slice positions.

    Avoids numpy dependency. For small samples (n<2) returns 0.0.
    """
    if len(values) < 2:
        return 0.0
    s = sorted(values)
    n = len(s)
    q1 = s[n // 4]
    q3 = s[(3 * n) // 4]
    return q3 - q1


def aggregate_minute(samples: list, dominant_state: Optional[str] = None) -> MinuteResult:
    """Aggregate one minute's samples.

    samples = [{'ts': float, 'hr': int|None, 'temp': float|None,
                'steps': int|None, 'quality_hr': str, 'quality_temp': str}, ...]

    Per CONTEXT.md D-08 (windows) + D-17 (threshold 0.30).
    """
    if not samples:
        return MinuteResult(None, None, None, None, 0.0, dominant_state)

    n = len(samples)
    good_hr: List[int] = [s["hr"] for s in samples if s.get("quality_hr") == "GOOD" and s.get("hr") is not None]
    good_tmp: List[float] = [s["temp"] for s in samples if s.get("quality_temp") == "GOOD" and s.get("temp") is not None]

    good_ratio_hr = len(good_hr) / n if n else 0.0
    good_ratio_tmp = len(good_tmp) / n if n else 0.0
    # Conservative — lower of the two ratios drives missing-flag
    good_ratio = min(good_ratio_hr, good_ratio_tmp)

    # D-17: GOOD ratio ≥ MIN_GOOD_RATIO (0.30) -> aggregate; else NULL.
    hr_median = int(statistics.median(good_hr)) if good_ratio_hr >= MIN_GOOD_RATIO and good_hr else None
    temp_median = round(statistics.median(good_tmp), 1) if good_ratio_tmp >= MIN_GOOD_RATIO and good_tmp else None
    temp_iqr = round(_iqr(good_tmp), 1) if good_ratio_tmp >= MIN_GOOD_RATIO and good_tmp else None

    # dominant_state — per D-17, missing-flag also nulls dominant_state so the
    # row is still recorded but flagged as missing aggregates.
    if good_ratio < MIN_GOOD_RATIO:
        dominant_state = None

    # steps delta — first vs last non-None steps in the minute.
    steps_first = next((s["steps"] for s in samples if s.get("steps") is not None), None)
    steps_last = next((s["steps"] for s in reversed(samples) if s.get("steps") is not None), None)
    steps_delta = (steps_last - steps_first) if (steps_first is not None and steps_last is not None) else None

    return MinuteResult(hr_median, temp_median, temp_iqr, steps_delta, good_ratio, dominant_state)
