package com.example.smart_safety_management.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Phase 11 / 11-01 — 공통 시각 언어 token.
 * Quick #03 TbmDashboardScreen 의 inline COLOR_* val 5종을 추출하고 SuccessGreen 한 색 추가.
 * Plan 11-02 의 28+ 화면 일괄 적용 시 의존하는 single source-of-truth.
 *
 * 색은 11-CONTEXT.md "공통 컴포넌트 추출 대상" 섹션에 명시된 hex 와 byte-equal.
 */
object SsmColors {
    val ActiveOrange = Color(0xFFF59E0B)     // 진행중 강조 (TBM Active 카드 border)
    val EndedBg      = Color(0xFFF3F4F6)     // 종료/비활성 카드 배경
    val TextMuted    = Color(0xFF6B7280)     // 보조 텍스트
    val TextInfo     = Color(0xFF2563EB)     // 정보 메시지
    val TextDanger   = Color(0xFFEF4444)     // 위험/에러
    val SuccessGreen = Color(0xFF22C55E)     // 성공/checkin
}

object SsmSpacing {
    val cardPadding     = 12.dp
    val sectionSpacing  = 16.dp
    val rowSpacing      = 8.dp
}

object SsmTypography {
    // Phase 11 out of scope — future hook (Plan 11-02 typography overhaul 검토 시 채움)
}
