package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardsListReducerTest {

    private val seedHazards = listOf(
        TbmTemplateHazard(id = "h1", text = "Existing 1"),
        TbmTemplateHazard(id = "h2", text = "Existing 2"),
    )

    @Test
    fun addHazard_emptyList_returnsSingleItemMarkedCustom() {
        val result = HazardsListReducer.addHazard(emptyList(), "New hazard")
        assertEquals(1, result.size)
        assertEquals("h1", result[0].id)
        assertEquals("New hazard", result[0].text)
        assertTrue(result[0].isCustom)
    }

    @Test
    fun addHazard_appendsWithNextNumericId() {
        val result = HazardsListReducer.addHazard(seedHazards, "Third one")
        assertEquals(3, result.size)
        assertEquals("h3", result[2].id)
        assertTrue(result[2].isCustom)
    }

    @Test
    fun addHazard_emptyText_returnsListUnchanged() {
        val result = HazardsListReducer.addHazard(seedHazards, "   ")
        assertEquals(seedHazards, result)
    }

    @Test
    fun addHazardByEntity_dupId_rejected() {
        val entity = TbmTemplateHazard(id = "h1", text = "Dup attempt")
        val result = HazardsListReducer.addHazardByEntity(seedHazards, entity)
        assertEquals(seedHazards, result)  // unchanged
    }

    @Test
    fun editHazardText_byId_marksCustom() {
        val result = HazardsListReducer.editHazardText(seedHazards, "h2", "Edited 2")
        assertEquals(2, result.size)
        assertEquals("Existing 1", result[0].text)
        assertEquals("Edited 2", result[1].text)
        assertTrue(result[1].isCustom)
    }

    @Test
    fun removeHazardById_filtersOnlyMatching() {
        val result = HazardsListReducer.removeHazardById(seedHazards, "h1")
        assertEquals(1, result.size)
        assertEquals("h2", result[0].id)
    }

    @Test
    fun addControl_emptyList_assignsC1() {
        val result = HazardsListReducer.addControl(emptyList(), "Wear PPE", hazardId = "h1", level = "control")
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
        assertEquals("h1", result[0].hazardId)
        assertEquals("control", result[0].level)
        assertTrue(result[0].isCustom)
    }

    @Test
    fun addAction_appendsWithNextId() {
        val seed = listOf(TbmTemplateAction(id = "a1", text = "Check route"))
        val result = HazardsListReducer.addAction(seed, "Confirm spotter")
        assertEquals(2, result.size)
        assertEquals("a2", result[1].id)
        assertTrue(result[1].isCustom)
    }
}
