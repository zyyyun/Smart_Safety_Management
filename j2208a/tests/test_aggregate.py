# -*- coding: utf-8 -*-
"""Unit tests for j2208a.aggregate — D-08 windows + D-17 (30%) threshold.

AG-1..AG-4 cover basic aggregation; AG-5..AG-8 are the D-17 regression guards.
"""

from j2208a.aggregate import MIN_GOOD_RATIO, _iqr, aggregate_minute


def _make_samples(specs):
    """specs = [(ts, hr, temp, steps, q_hr, q_temp), ...]."""
    return [
        {"ts": ts, "hr": hr, "temp": temp, "steps": steps,
         "quality_hr": q_hr, "quality_temp": q_temp}
        for ts, hr, temp, steps, q_hr, q_temp in specs
    ]


def test_ag1_hr_5s_median():
    """AG-1: 5-sec window with 5 GOOD HR samples [60,65,70,75,80] -> median=70."""
    specs = [
        (0.0, 60, 36.0, 100, "GOOD", "GOOD"),
        (1.0, 65, 36.0, 100, "GOOD", "GOOD"),
        (2.0, 70, 36.0, 100, "GOOD", "GOOD"),
        (3.0, 75, 36.0, 100, "GOOD", "GOOD"),
        (4.0, 80, 36.0, 100, "GOOD", "GOOD"),
    ]
    result = aggregate_minute(_make_samples(specs))
    assert result.hr_median == 70


def test_ag2_good_ratio_below_30pct_returns_null_aggregates():
    """AG-2 (D-17): GOOD 3/12 = 0.25 < MIN_GOOD_RATIO (0.30) -> hr_median=None."""
    specs = []
    # 3 GOOD HR samples
    for t in range(3):
        specs.append((float(t), 70, 36.0, 100, "GOOD", "GOOD"))
    # 9 INVALID HR samples
    for t in range(3, 12):
        specs.append((float(t), 250, 36.0, 100, "INVALID", "GOOD"))
    result = aggregate_minute(_make_samples(specs))
    # HR good_ratio = 3/12 = 0.25 < 0.30 -> hr_median = None
    assert result.hr_median is None
    assert result.dominant_state is None  # also nulled per D-17


def test_ag3_steps_delta():
    """AG-3: first steps=1000, last steps=1080 -> steps_delta=80."""
    specs = [
        (0.0, 70, 36.0, 1000, "GOOD", "GOOD"),
        (30.0, 72, 36.0, 1040, "GOOD", "GOOD"),
        (59.0, 70, 36.0, 1080, "GOOD", "GOOD"),
    ]
    result = aggregate_minute(_make_samples(specs))
    assert result.steps_delta == 80


def test_ag4_temp_30s_median_and_iqr():
    """AG-4: 5 temp samples [36.0,36.2,36.5,36.8,37.0] -> median=36.5, IQR= s[3]-s[1] = 36.8-36.2 = 0.6."""
    specs = [
        (0.0, 70, 36.0, 100, "GOOD", "GOOD"),
        (10.0, 70, 36.2, 100, "GOOD", "GOOD"),
        (20.0, 70, 36.5, 100, "GOOD", "GOOD"),
        (30.0, 70, 36.8, 100, "GOOD", "GOOD"),
        (40.0, 70, 37.0, 100, "GOOD", "GOOD"),
    ]
    result = aggregate_minute(_make_samples(specs))
    assert result.temp_median == 36.5
    # _iqr uses s[n//4] and s[(3n)//4]; for n=5 -> s[1]=36.2, s[3]=36.8 -> 0.6
    # Use round() to absorb 36.8 - 36.2 = 0.5999... float-binary noise.
    assert round(_iqr([36.0, 36.2, 36.5, 36.8, 37.0]), 1) == 0.6
    # aggregate_minute already rounds temp_iqr to 1 decimal place — exact compare ok.
    assert result.temp_iqr == 0.6


def test_ag5_d17_ratio_above_threshold_passes():
    """AG-5 (D-17 regression guard): GOOD 5/12 ≈ 0.417 passes new 0.30 threshold.

    Under the OLD 0.50 threshold this would have NULL'd a healthy minute.
    """
    specs = []
    for t in range(5):
        specs.append((float(t), 70, 36.0, 100, "GOOD", "GOOD"))
    for t in range(5, 12):
        specs.append((float(t), 0, 36.0, 100, "WARMUP", "GOOD"))
    result = aggregate_minute(_make_samples(specs))
    assert result.hr_median == 70  # NOT None — passes new 30% threshold


def test_ag6_d17_good_ratio_0_29_returns_null():
    """AG-6 (D-17 boundary): GOOD 3/12 = 0.25 < 0.30 -> NULL."""
    specs = []
    for t in range(3):
        specs.append((float(t), 70, 36.0, 100, "GOOD", "GOOD"))
    for t in range(3, 12):
        specs.append((float(t), 0, 36.0, 100, "WARMUP", "GOOD"))
    result = aggregate_minute(_make_samples(specs))
    assert result.hr_median is None  # below 0.30 threshold


def test_ag7_d17_good_ratio_0_31_aggregates():
    """AG-7 (D-17 boundary): GOOD 4/12 ≈ 0.333 > 0.30 -> aggregated."""
    specs = []
    for t in range(4):
        specs.append((float(t), 70, 36.0, 100, "GOOD", "GOOD"))
    for t in range(4, 12):
        specs.append((float(t), 0, 36.0, 100, "WARMUP", "GOOD"))
    result = aggregate_minute(_make_samples(specs))
    assert result.hr_median == 70  # passes 0.30 threshold


def test_ag8_d17_restart_pause_scenario():
    """AG-8 (D-17 realistic scenario): 1 minute = 12 samples; 4 are HR=0 (PPG re-lock
    after `cmd_health_measure(2,True)` 20s restart). good_ratio = 8/12 ≈ 0.67 ->
    passes new 0.30 threshold AND would have passed old 0.50 too. The regression
    guard is for the *worst-case* worker where 6 out of 12 land in WARMUP — under
    old 0.50 this is borderline, under new 0.30 it's clearly safe.
    """
    # 8 GOOD samples + 4 WARMUP (restart pause) = realistic
    specs = []
    for t in range(8):
        specs.append((float(t * 5), 70, 36.0, 100, "GOOD", "GOOD"))
    for t in range(8, 12):
        specs.append((float(t * 5), 0, 36.0, 100, "WARMUP", "GOOD"))
    result = aggregate_minute(_make_samples(specs))
    assert result.hr_median == 70
    assert result.good_ratio >= MIN_GOOD_RATIO
