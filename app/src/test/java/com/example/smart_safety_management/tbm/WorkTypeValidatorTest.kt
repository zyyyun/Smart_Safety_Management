package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkTypeValidatorTest {

    private val templates = listOf(
        TbmTemplateRow(templateId = 1, workType = "forklift", title = "Forklift", isActive = true),
        TbmTemplateRow(templateId = 2, workType = "chemical", title = "Chemical", isActive = true),
        TbmTemplateRow(templateId = 3, workType = "custom_ops", title = "Custom", isActive = true),
        TbmTemplateRow(templateId = 4, workType = "inactive_ops", title = "Inactive", isActive = false),
    )

    @Test
    fun seedTypes_areKnownSeeds() {
        listOf("forklift", "chemical", "hot_work").forEach {
            assertTrue("$it should be a seed type", WorkTypeValidator.isKnownSeed(it))
        }
    }

    @Test
    fun customTemplate_isValidWhenActive() {
        assertTrue(WorkTypeValidator.isValid("custom_ops", templates))
    }

    @Test
    fun inactiveTemplate_isInvalid() {
        assertFalse(WorkTypeValidator.isValid("inactive_ops", templates))
    }

    @Test
    fun unknownTemplate_isInvalid() {
        assertFalse(WorkTypeValidator.isValid("welding", templates))
    }

    @Test
    fun normalize_lowercasesAndTrims() {
        assertEquals("forklift", WorkTypeValidator.normalize("  FORKLIFT  "))
    }

    @Test
    fun displayName_usesTemplateTitle() {
        assertEquals("Custom", WorkTypeValidator.displayName("custom_ops", templates))
    }
}
