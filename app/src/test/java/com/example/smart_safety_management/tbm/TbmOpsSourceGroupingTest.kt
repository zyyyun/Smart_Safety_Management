package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Test

class TbmOpsSourceGroupingTest {
    @Test
    fun groupsItemsByOpsTitleWhenMetadataExists() {
        val grouped = groupByOpsTitle(
            listOf(
                TbmTemplateHazard("h1", "Collision", opsTitle = "Forklift"),
                TbmTemplateHazard("h2", "Fire", opsTitle = "Hot work"),
                TbmTemplateHazard("h3", "Fall", opsTitle = "Forklift"),
            ),
            labelOf = { it.opsTitle },
        )

        assertEquals(listOf("Forklift", "Hot work"), grouped.map { it.opsTitle })
        assertEquals(listOf("Collision", "Fall"), grouped[0].items.map { it.text })
        assertEquals(listOf("Fire"), grouped[1].items.map { it.text })
    }

    @Test
    fun usesFallbackGroupWhenMetadataIsMissing() {
        val grouped = groupByOpsTitle(
            listOf(
                TbmTemplateControl("c1", text = "Use spotter"),
                TbmTemplateControl("c2", text = "Set barrier", opsTitle = ""),
            ),
            labelOf = { it.opsTitle },
        )

        assertEquals(listOf(null), grouped.map { it.opsTitle })
        assertEquals(2, grouped.single().items.size)
    }

    @Test
    fun parsesChecklistOpsTitlePrefix() {
        val item = checklistDisplayItem(
            TbmChecklistRow(
                checklistId = 1,
                sessionId = 10,
                itemIdx = 0,
                itemText = "[Forklift] Check blind spots",
            ),
        )

        assertEquals("Forklift", item.opsTitle)
        assertEquals("Check blind spots", item.displayText)
    }

    @Test
    fun leavesChecklistWithoutPrefixInFallbackGroup() {
        val item = checklistDisplayItem(
            TbmChecklistRow(
                checklistId = 1,
                sessionId = 10,
                itemIdx = 0,
                itemText = "Check PPE",
            ),
        )

        assertEquals(null, item.opsTitle)
        assertEquals("Check PPE", item.displayText)
    }
}
