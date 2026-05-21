package com.example.smart_safety_management.tbm

import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase 9 / 09-03 TBM-02 — TIMESTAMPTZ ISO-8601 OFFSET 형식 검증.
 *
 * Pitfall 8 (timezone): tbm_sessions.expected_end_at 은 TIMESTAMPTZ —
 * Android 측에서 ZonedDateTime.now(Asia/Seoul) 의 ISO_OFFSET_DATE_TIME 형식으로 전송.
 * Naive datetime (offset 누락) 은 거부.
 *
 * - parse("2026-05-18T09:15:00+09:00") → success
 * - parse("2026-05-18T00:15:00Z")       → success (Z = +00:00)
 * - parse("2026-05-18T09:15:00")        → failure (offset 누락)
 * - parse("invalid")                     → failure
 *
 * v1.0: 과거 시각도 parse 성공 (UI 측에서 별도 가드). future-only 제약은 v1.1+.
 */
object ExpectedEndAtValidator {

    fun parse(input: String): Result<ZonedDateTime> = runCatching {
        ZonedDateTime.parse(input, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /**
     * 현재 시각 (KST). UI default = nowKst().plusMinutes(15) — Claude's Discretion
     * (CONTEXT D-07): TBM 표준 15분 권장.
     */
    fun nowKst(): ZonedDateTime = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))

    /**
     * 서버 전송용 ISO_OFFSET_DATE_TIME 포맷 (e.g., "2026-05-18T09:15:00+09:00").
     */
    fun formatForServer(z: ZonedDateTime): String =
        z.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
