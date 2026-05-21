package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9 / 09-03 TBM-02 — WorkTypeValidator 단위 테스트 (TDD).
 *
 * 검증 대상: tbm_templates.work_type 5종 (fire/electric/height/heavy/general) 의 enum
 * 정합성 + normalize (lowercase + trim).
 *
 * Phase 7 MacAddressValidatorTest 패턴 1:1 미러. JUnit4 (org.junit.Test) 사용.
 */
class WorkTypeValidatorTest {

    @Test
    fun test_validFire_passes() {
        assertTrue(WorkTypeValidator.isValid("fire"))
    }

    @Test
    fun test_allFiveTypes_pass() {
        listOf("fire", "electric", "height", "heavy", "general").forEach {
            assertTrue("$it should be valid", WorkTypeValidator.isValid(it))
        }
    }

    @Test
    fun test_invalidEmpty_fails() {
        assertFalse(WorkTypeValidator.isValid(""))
    }

    @Test
    fun test_invalidUnknown_fails() {
        assertFalse(WorkTypeValidator.isValid("welding"))
    }

    @Test
    fun test_caseSensitiveBeforeNormalize_uppercaseFails() {
        // isValid 자체는 case-sensitive — normalize 호출 의무는 caller 측.
        assertFalse(WorkTypeValidator.isValid("FIRE"))
    }

    @Test
    fun test_normalize_uppercaseToLower() {
        assertEquals("fire", WorkTypeValidator.normalize("FIRE "))
    }

    @Test
    fun test_normalize_trimsWhitespace() {
        assertEquals("electric", WorkTypeValidator.normalize("  Electric  "))
    }

    @Test
    fun test_normalize_then_isValid_combined() {
        assertTrue(WorkTypeValidator.isValid(WorkTypeValidator.normalize("  HEAVY ")))
    }
}
