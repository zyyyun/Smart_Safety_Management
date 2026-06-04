package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Test

class TbmOpsAggregationTest {
    @Test
    fun aggregatesHazardsControlsAndChecksWithOpsSource() {
        val forklift = TbmTemplateRow(
            templateId = 1,
            workType = "forklift",
            title = "Forklift work",
            hazards = listOf(TbmTemplateHazard("h1", "Collision hazard")),
            controls = listOf(
                TbmTemplateControl(
                    id = "c1",
                    hazardId = "h1",
                    level = "control",
                    text = "Assign signaler",
                ),
            ),
            checks = listOf("Confirm work perimeter"),
        )
        val hot = TbmTemplateRow(
            templateId = 2,
            workType = "hot",
            title = "Hot work",
            hazards = listOf(TbmTemplateHazard("h2", "Fire hazard")),
            controls = listOf(
                TbmTemplateControl(
                    id = "c2",
                    hazardId = "h2",
                    level = "control",
                    text = "Place extinguisher",
                ),
            ),
            checks = listOf("Confirm spark screen"),
        )

        val result = aggregateSelectedOps(listOf(forklift, hot))

        assertEquals(listOf(1, 2), result.templateIds)
        assertEquals(listOf("Forklift work", "Hot work"), result.opsTitles)
        assertEquals(2, result.hazards.size)
        assertEquals(1, result.hazards[0].opsTemplateId)
        assertEquals("Forklift work", result.hazards[0].opsTitle)
        assertEquals(2, result.controls.size)
        assertEquals(2, result.controls[1].opsTemplateId)
        assertEquals("Hot work", result.controls[1].opsTitle)
        assertEquals(2, result.checks.size)
        assertEquals(1, result.checks[0].opsTemplateId)
        assertEquals("Forklift work", result.checks[0].opsTitle)
    }
}
