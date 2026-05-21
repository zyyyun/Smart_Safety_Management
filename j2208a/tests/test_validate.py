# -*- coding: utf-8 -*-
"""Unit tests for j2208a.validate — D-07 quality classification.

6 boundary cases + 1 normal-GOOD case = 7 tests (WATCH-02).
"""

from j2208a.validate import Sample, validate_sample


def test_hr_zero_is_warmup():
    """HR=0 means PPG lock-on pending — WARMUP, NOT a missing value."""
    q = validate_sample(Sample(ts=0.0, heart_rate=0, temperature_c=32.0), prev=None)
    assert q["heart_rate"] == "WARMUP"


def test_hr_below_30_is_invalid():
    """HR < 30 -> INVALID (out of physiological range)."""
    q = validate_sample(Sample(ts=0.0, heart_rate=25, temperature_c=36.5), prev=None)
    assert q["heart_rate"] == "INVALID"


def test_hr_above_220_is_invalid():
    """HR > 220 -> INVALID (Karvonen ceiling)."""
    q = validate_sample(Sample(ts=0.0, heart_rate=250, temperature_c=36.5), prev=None)
    assert q["heart_rate"] == "INVALID"


def test_temp_below_25_is_invalid():
    """temp < 25°C -> INVALID."""
    q = validate_sample(Sample(ts=0.0, heart_rate=70, temperature_c=22.0), prev=None)
    assert q["temperature_c"] == "INVALID"


def test_temp_above_43_is_invalid():
    """temp > 43°C -> INVALID."""
    q = validate_sample(Sample(ts=0.0, heart_rate=70, temperature_c=45.0), prev=None)
    assert q["temperature_c"] == "INVALID"


def test_dtemp_above_1_5_per_sec_is_noisy():
    """|Δtemp|/Δt > 1.5°C/sec -> NOISY.

    prev temp=36.0 @ t=0; current temp=37.6 @ t=0.5 -> rate = 3.2°C/sec.
    """
    prev = Sample(ts=0.0, heart_rate=70, temperature_c=36.0)
    cur = Sample(ts=0.5, heart_rate=70, temperature_c=37.6)
    q = validate_sample(cur, prev)
    assert q["temperature_c"] == "NOISY"


def test_normal_values_are_good():
    """HR=70 + temp=36.5 (with stable prev) -> both GOOD."""
    prev = Sample(ts=0.0, heart_rate=70, temperature_c=36.5)
    cur = Sample(ts=1.0, heart_rate=70, temperature_c=36.5)
    q = validate_sample(cur, prev)
    assert q["heart_rate"] == "GOOD"
    assert q["temperature_c"] == "GOOD"
