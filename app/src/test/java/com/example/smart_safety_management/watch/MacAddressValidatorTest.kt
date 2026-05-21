package com.example.smart_safety_management.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 / 07-03 BRIDGE-03a — MAC 정규식 단위 테스트.
 * 정규식 ^([0-9A-F]{2}:){5}[0-9A-F]{2}$ 의 경계 케이스.
 */
class MacAddressValidatorTest {

    @Test
    fun test_validMacUppercase_passes() {
        assertTrue(MacAddressValidator.isValid("21:02:02:06:01:69"))
    }

    @Test
    fun test_validMacLowercase_normalizesToValid() {
        // 정규화 후 검사 — lowercase 도 정상으로 인정
        assertTrue(MacAddressValidator.isValid("ab:cd:ef:12:34:56"))
    }

    @Test
    fun test_validMacWithLeadingTrailingSpaces_normalizes() {
        assertTrue(MacAddressValidator.isValid("  21:02:02:06:01:69  "))
    }

    @Test
    fun test_invalidLength_fails() {
        // octet 5개만 (마지막 :69 누락)
        assertFalse(MacAddressValidator.isValid("21:02:02:06:01"))
    }

    @Test
    fun test_invalidSeparator_fails() {
        // 콜론 대신 하이픈
        assertFalse(MacAddressValidator.isValid("21-02-02-06-01-69"))
    }

    @Test
    fun test_invalidHexChar_fails() {
        // G 는 유효 hex 가 아님
        assertFalse(MacAddressValidator.isValid("ZZ:02:02:06:01:69"))
    }

    @Test
    fun test_emptyString_fails() {
        assertFalse(MacAddressValidator.isValid(""))
    }

    @Test
    fun test_normalizeReturnsUppercase() {
        assertEquals("AB:CD:EF:12:34:56", MacAddressValidator.normalize("ab:cd:ef:12:34:56"))
    }

    @Test
    fun test_normalizeTrimsWhitespace() {
        assertEquals("21:02:02:06:01:69", MacAddressValidator.normalize("  21:02:02:06:01:69  "))
    }
}
