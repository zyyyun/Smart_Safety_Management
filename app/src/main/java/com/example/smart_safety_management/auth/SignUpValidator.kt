package com.example.smart_safety_management.auth

import java.util.regex.Pattern

/**
 * Phase 11 / 11-02 Sub-task 2.1 (UX-01) — 입구 흐름 공통 폼 validator.
 *
 * SignUpValidator.validate(field, value) 가 4 field (EMAIL/PHONE/PASSWORD/NAME) 에 대해
 * pure 결정적으로 SignUpFieldError? 반환. null 이면 OK, non-null 이면 에러 코드.
 * UI 는 errorBannerMessage(field, error) 로 변환 후 ErrorBanner Composable 표시.
 *
 * Pure JVM 호환: java.util.regex.Pattern 사용 (android.util.Patterns 는 unit test 환경에서
 * NoClassDefFoundError 가능 — Robolectric 미사용).
 */

enum class SignUpField { EMAIL, PHONE, PASSWORD, NAME }

enum class SignUpFieldError {
    EMPTY,           // 빈 입력
    INVALID_FORMAT,  // 형식 오류 (email 정규식 / phone 010-XXXX-XXXX)
    TOO_SHORT,       // 최소 길이 미달 (password >= 8)
}

object SignUpValidator {

    private val EMAIL_REGEX: Pattern = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    // 010-XXXX-XXXX, 하이픈은 optional
    private val PHONE_REGEX: Pattern = Pattern.compile(
        "^010-?\\d{4}-?\\d{4}$"
    )

    private const val PASSWORD_MIN_LENGTH = 8

    fun validate(field: SignUpField, value: String): SignUpFieldError? {
        return when (field) {
            SignUpField.EMAIL -> validateEmail(value)
            SignUpField.PHONE -> validatePhone(value)
            SignUpField.PASSWORD -> validatePassword(value)
            SignUpField.NAME -> validateName(value)
        }
    }

    private fun validateEmail(value: String): SignUpFieldError? {
        if (value.trim().isEmpty()) return SignUpFieldError.EMPTY
        if (!EMAIL_REGEX.matcher(value.trim()).matches()) return SignUpFieldError.INVALID_FORMAT
        return null
    }

    private fun validatePhone(value: String): SignUpFieldError? {
        if (value.trim().isEmpty()) return SignUpFieldError.EMPTY
        if (!PHONE_REGEX.matcher(value.trim()).matches()) return SignUpFieldError.INVALID_FORMAT
        return null
    }

    private fun validatePassword(value: String): SignUpFieldError? {
        if (value.isEmpty()) return SignUpFieldError.EMPTY
        if (value.length < PASSWORD_MIN_LENGTH) return SignUpFieldError.TOO_SHORT
        return null
    }

    private fun validateName(value: String): SignUpFieldError? {
        if (value.trim().isEmpty()) return SignUpFieldError.EMPTY
        return null
    }
}
