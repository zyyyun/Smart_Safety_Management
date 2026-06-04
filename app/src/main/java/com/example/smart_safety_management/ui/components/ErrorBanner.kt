package com.example.smart_safety_management.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.auth.SignUpField
import com.example.smart_safety_management.auth.SignUpFieldError
import com.example.smart_safety_management.ui.SsmColors

/**
 * Phase 11 / 11-02 Sub-task 2.2 (UX-01) — 입구 흐름 공통 에러 표시.
 *
 * errorBannerMessage(field, error) 는 pure 함수 — 12 조합 (4 field × 3 error) 에 대해
 * 결정적 한국어 메시지 반환. ErrorBanner Composable 은 빈 message 면 표시 안 함.
 *
 * 신규 코드는 Compose 람다 early-exit (b2d8745 lesson) 절대 사용 금지 — if/else 양분만.
 * docstring 에서도 `return` + `@` 인접 표현 피함 (Plan 11-01 deviation 3 의 grep 함정).
 */

fun errorBannerMessage(field: SignUpField, error: SignUpFieldError): String {
    return when (field) {
        SignUpField.EMAIL -> when (error) {
            SignUpFieldError.EMPTY -> "이메일을 입력해 주세요"
            SignUpFieldError.INVALID_FORMAT -> "올바른 이메일 형식이 아닙니다"
            SignUpFieldError.TOO_SHORT -> "이메일이 너무 짧습니다"
        }
        SignUpField.PHONE -> when (error) {
            SignUpFieldError.EMPTY -> "휴대폰 번호를 입력해 주세요"
            SignUpFieldError.INVALID_FORMAT -> "휴대폰 번호 형식이 아닙니다 (010-XXXX-XXXX)"
            SignUpFieldError.TOO_SHORT -> "휴대폰 번호가 너무 짧습니다 (010-XXXX-XXXX)"
        }
        SignUpField.PASSWORD -> when (error) {
            SignUpFieldError.EMPTY -> "비밀번호를 입력해 주세요"
            SignUpFieldError.INVALID_FORMAT -> "비밀번호 형식이 올바르지 않습니다"
            SignUpFieldError.TOO_SHORT -> "비밀번호는 8자 이상이어야 합니다"
        }
        SignUpField.NAME -> when (error) {
            SignUpFieldError.EMPTY -> "이름을 입력해 주세요"
            SignUpFieldError.INVALID_FORMAT -> "이름 형식이 올바르지 않습니다"
            SignUpFieldError.TOO_SHORT -> "이름이 너무 짧습니다"
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    // Compose 람다 early-exit 금지 — if/else 양분 (b2d8745 lesson).
    if (message.isBlank()) {
        // 표시 안 함 (빈 컴포저 — 본문 없음).
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SsmColors.TextDanger)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "경고",
                tint = Color.White,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}
