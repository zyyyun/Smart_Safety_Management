package com.example.smart_safety_management.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.SsmColors

/**
 * Phase 11 / 11-01 — section header 라벨 포맷팅 pure 함수.
 * Quick #03 의 "진행중 (1개)" / "종료 (1개)" 한국어 단위 (T-11-04 mitigation) 를 single source-of-truth 로.
 *
 * - count == null → label 그대로 ("진행중")
 * - count >= 0    → "label (N개)" 한국어 ("진행중 (1개)", "종료 (0개)")
 *
 * 호출처가 count 표시 원치 않으면 null 전달. count=0 도 명시적 "(0개)" 표시 (호출처 책임).
 */
fun sectionHeaderLabel(label: String, count: Int?): String =
    if (count == null) label else "$label (${count}개)"

/**
 * 공통 SectionHeader Composable — Quick #03 TbmDashboardScreen 의 private SectionHeader 추출.
 * Plan 11-02 의 28+ 화면 일괄 적용 시 import 하여 사용.
 *
 * 시각 사양 (Quick #03 e557fb4 기준):
 *   - Row + verticalAlignment = CenterVertically
 *   - Icon size 18.dp, tint = iconTint
 *   - Spacer width 6.dp
 *   - Text fontWeight = SemiBold, fontSize = 14.sp, color = iconTint
 *
 * Compose 람다 early-exit 금지 (b2d8745 lesson) — if/else 양분 패턴 강제.
 */
@Composable
fun SectionHeader(
    icon: ImageVector,
    label: String,
    count: Int? = null,
    iconTint: Color = SsmColors.TextMuted,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            sectionHeaderLabel(label, count),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = iconTint,
        )
    }
}
