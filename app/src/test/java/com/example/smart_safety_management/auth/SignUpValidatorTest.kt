package com.example.smart_safety_management.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignUpValidatorTest {
    // EMAIL
    @Test fun email_empty_returnsEmpty() {
        assertEquals(SignUpFieldError.EMPTY, SignUpValidator.validate(SignUpField.EMAIL, ""))
    }
    @Test fun email_whitespaceOnly_returnsEmpty() {
        assertEquals(SignUpFieldError.EMPTY, SignUpValidator.validate(SignUpField.EMAIL, "   "))
    }
    @Test fun email_noAt_returnsInvalid() {
        assertEquals(SignUpFieldError.INVALID_FORMAT, SignUpValidator.validate(SignUpField.EMAIL, "foo.bar"))
    }
    @Test fun email_valid_returnsNull() {
        assertNull(SignUpValidator.validate(SignUpField.EMAIL, "user@example.com"))
    }

    // PHONE — 010-XXXX-XXXX
    @Test fun phone_empty_returnsEmpty() {
        assertEquals(SignUpFieldError.EMPTY, SignUpValidator.validate(SignUpField.PHONE, ""))
    }
    @Test fun phone_tooShort_returnsInvalid() {
        assertEquals(SignUpFieldError.INVALID_FORMAT, SignUpValidator.validate(SignUpField.PHONE, "010-1234"))
    }
    @Test fun phone_wrongPrefix_returnsInvalid() {
        assertEquals(SignUpFieldError.INVALID_FORMAT, SignUpValidator.validate(SignUpField.PHONE, "011-1234-5678"))
    }
    @Test fun phone_valid_returnsNull() {
        assertNull(SignUpValidator.validate(SignUpField.PHONE, "010-1234-5678"))
    }
    @Test fun phone_validNoHyphen_returnsNull() {
        // 사용자 편의: 하이픈 없어도 OK
        assertNull(SignUpValidator.validate(SignUpField.PHONE, "01012345678"))
    }

    // PASSWORD
    @Test fun password_empty_returnsEmpty() {
        assertEquals(SignUpFieldError.EMPTY, SignUpValidator.validate(SignUpField.PASSWORD, ""))
    }
    @Test fun password_short_returnsTooShort() {
        assertEquals(SignUpFieldError.TOO_SHORT, SignUpValidator.validate(SignUpField.PASSWORD, "abc123"))
    }
    @Test fun password_exactlyEight_returnsNull() {
        assertNull(SignUpValidator.validate(SignUpField.PASSWORD, "abcd1234"))
    }
    @Test fun password_long_returnsNull() {
        assertNull(SignUpValidator.validate(SignUpField.PASSWORD, "SecurePass123!"))
    }

    // NAME
    @Test fun name_empty_returnsEmpty() {
        assertEquals(SignUpFieldError.EMPTY, SignUpValidator.validate(SignUpField.NAME, ""))
    }
    @Test fun name_korean_returnsNull() {
        assertNull(SignUpValidator.validate(SignUpField.NAME, "홍길동"))
    }
    @Test fun name_english_returnsNull() {
        assertNull(SignUpValidator.validate(SignUpField.NAME, "Hong"))
    }
}
