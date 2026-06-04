package com.example.smart_safety_management.ui.components

import com.example.smart_safety_management.auth.SignUpField
import com.example.smart_safety_management.auth.SignUpFieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorBannerMessageTest {
    @Test fun emailEmpty_koreanMessage() {
        assertEquals("이메일을 입력해 주세요", errorBannerMessage(SignUpField.EMAIL, SignUpFieldError.EMPTY))
    }
    @Test fun emailInvalid_koreanMessage() {
        assertEquals("올바른 이메일 형식이 아닙니다", errorBannerMessage(SignUpField.EMAIL, SignUpFieldError.INVALID_FORMAT))
    }
    @Test fun phoneEmpty_koreanMessage() {
        assertEquals("휴대폰 번호를 입력해 주세요", errorBannerMessage(SignUpField.PHONE, SignUpFieldError.EMPTY))
    }
    @Test fun phoneInvalid_koreanMessageContainsFormatHint() {
        val msg = errorBannerMessage(SignUpField.PHONE, SignUpFieldError.INVALID_FORMAT)
        assertTrue("phone INVALID_FORMAT 메시지에 '010' 형식 안내 누락", msg.contains("010"))
    }
    @Test fun passwordEmpty_koreanMessage() {
        assertEquals("비밀번호를 입력해 주세요", errorBannerMessage(SignUpField.PASSWORD, SignUpFieldError.EMPTY))
    }
    @Test fun passwordTooShort_koreanMessageContainsLengthHint() {
        val msg = errorBannerMessage(SignUpField.PASSWORD, SignUpFieldError.TOO_SHORT)
        assertTrue("password TOO_SHORT 메시지에 '8자' 길이 안내 누락", msg.contains("8자"))
    }
    @Test fun nameEmpty_koreanMessage() {
        assertEquals("이름을 입력해 주세요", errorBannerMessage(SignUpField.NAME, SignUpFieldError.EMPTY))
    }
    @Test fun allMessagesAreKorean() {
        // 모든 12 조합에 한글 1자 이상 포함 — 영어 only 메시지 0건
        SignUpField.values().forEach { f ->
            SignUpFieldError.values().forEach { e ->
                val msg = errorBannerMessage(f, e)
                assertTrue("($f, $e) 메시지에 한글 부재: '$msg'", msg.any { it in '가'..'힣' })
            }
        }
    }
}
