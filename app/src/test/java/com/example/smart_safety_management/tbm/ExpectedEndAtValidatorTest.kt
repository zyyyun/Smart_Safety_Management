package com.example.smart_safety_management.tbm

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9 / 09-03 TBM-02 — ExpectedEndAtValidator 단위 테스트 (TDD).
 *
 * 검증 대상: ZonedDateTime ISO-8601 OFFSET 형식 (Pitfall 8 timezone 강제).
 * - ISO_OFFSET_DATE_TIME: 2026-05-18T09:15:00+09:00 / 2026-05-18T00:15:00Z
 * - 누락된 offset: 2026-05-18T09:15:00 → 거부
 * - 잘못된 문자열: "invalid" → 거부
 *
 * Phase 7 MacAddressValidatorTest 패턴 1:1 미러. JUnit4.
 */
class ExpectedEndAtValidatorTest {

    @Test
    fun test_validISOOffset_KSTPlus0900_passes() {
        assertTrue(ExpectedEndAtValidator.parse("2026-05-18T09:15:00+09:00").isSuccess)
    }

    @Test
    fun test_rejectNoOffset_fails() {
        // Pitfall 8 — offset 없는 naive datetime 거부
        assertTrue(ExpectedEndAtValidator.parse("2026-05-18T09:15:00").isFailure)
    }

    @Test
    fun test_rejectInvalidString_fails() {
        assertTrue(ExpectedEndAtValidator.parse("not a timestamp").isFailure)
    }

    @Test
    fun test_acceptZUtcMarker_passes() {
        // ISO_OFFSET_DATE_TIME 은 Z 도 valid offset 으로 받음
        assertTrue(ExpectedEndAtValidator.parse("2026-05-18T00:15:00Z").isSuccess)
    }

    @Test
    fun test_acceptPastTimestamp_passes() {
        // v1.0 한정 — 과거 시각도 parse 자체는 성공 (UI 단에서 별도 가드).
        // ExpectedEndAtValidator.parse 는 format-only 검증.
        assertTrue(ExpectedEndAtValidator.parse("1999-01-01T00:00:00+09:00").isSuccess)
    }
}
