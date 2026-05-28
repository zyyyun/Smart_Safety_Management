package com.example.smart_safety_management

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyChecklistDateTest {
    @Test
    fun usesExplicitChecklistDateBeforeCreatedAt() {
        assertEquals(
            LocalDate.parse("2026-05-20"),
            dailyChecklistDisplayDate(
                checkDate = "2026-05-20",
                createdAt = "2026-05-28T01:23:45Z",
            ),
        )
    }

    @Test
    fun usesDatePortionOfExplicitChecklistTimestampBeforeCreatedAt() {
        assertEquals(
            LocalDate.parse("2026-05-20"),
            dailyChecklistDisplayDate(
                checkDate = "2026-05-20T09:30:00Z",
                createdAt = "2026-05-28T01:23:45Z",
            ),
        )
    }

    @Test
    fun fallsBackToCreatedAtDateWhenChecklistDateMissing() {
        assertEquals(
            LocalDate.parse("2026-05-28"),
            dailyChecklistDisplayDate(
                checkDate = null,
                createdAt = "2026-05-28T01:23:45Z",
            ),
        )
    }
}
