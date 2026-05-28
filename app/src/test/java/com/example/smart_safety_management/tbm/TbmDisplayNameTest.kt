package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Test

class TbmDisplayNameTest {
    @Test
    fun selectedOpsSessionTitleJoinsTemplateTitles() {
        val templates = listOf(
            TbmTemplateRow(templateId = 1, workType = "forklift", title = "지게차"),
            TbmTemplateRow(templateId = 2, workType = "height", title = "고소작업"),
        )

        assertEquals("지게차 + 고소작업", selectedOpsSessionTitle(templates))
    }

    @Test
    fun selectedOpsSessionTitleFallsBackToDefaultWhenEmpty() {
        assertEquals("TBM 세션", selectedOpsSessionTitle(emptyList()))
    }

    @Test
    fun tbmSessionDisplayTitlePrefersOpsSnapshotTitles() {
        val session = TbmSessionRow(
            sessionId = 1,
            groupId = 1,
            sessionDate = "2026-05-28",
            workScope = "forklift",
            startedAt = "2026-05-28T01:00:00Z",
            expectedEndAt = "2026-05-28T02:00:00Z",
            leaderUserId = "leader",
            workType = "forklift",
            hazardsSnapshot = listOf(
                TbmTemplateHazard("h1", "충돌", opsTitle = "지게차"),
                TbmTemplateHazard("h2", "추락", opsTitle = "고소작업"),
            ),
        )

        assertEquals("지게차 + 고소작업", tbmSessionDisplayTitle(session))
    }
}
