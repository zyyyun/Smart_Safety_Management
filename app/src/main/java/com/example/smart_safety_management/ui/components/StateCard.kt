package com.example.smart_safety_management.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.smart_safety_management.ui.SsmColors
import com.example.smart_safety_management.ui.SsmSpacing

/**
 * Phase 11 / 11-01 — 카드 상태 sealed class.
 * Quick #03 TBM 대시보드의 inline 카드 톤 분기 (active/ended) 를 추출하고 4종 추가.
 *
 * Mapping:
 *   Active       — 작업 진행중 (orange border + white bg, e.g. TBM active session)
 *   Ended        — 완료/비활성 (gray bg + muted text, e.g. TBM ended session)
 *   InProgress   — Active 와 동일 색이지만 의미 분리 (지속 작업 중)
 *   Success      — 성공/checkin (green border + green text)
 *   Error        — 위험/에러 (red border + red text)
 *   Neutral      — 기본 (border/bg 없음, black text)
 */
sealed class CardState {
    object Active : CardState()
    object Ended : CardState()
    object InProgress : CardState()
    object Success : CardState()
    object Error : CardState()
    object Neutral : CardState()
}

/**
 * 카드 시각 token 묶음. borderColor / backgroundColor 가 null 이면 default (Card 기본값).
 */
data class CardTokens(
    val borderColor: Color?,
    val backgroundColor: Color?,
    val textColor: Color,
)

/**
 * Pure mapping: CardState → CardTokens (시각 token 결정).
 * Phase 11-02 의 Home 카드 5종 + Setting* 일괄 적용 시 이 함수만 호출하면 충분.
 */
fun stateToCardTokens(state: CardState): CardTokens = when (state) {
    CardState.Active     -> CardTokens(borderColor = SsmColors.ActiveOrange, backgroundColor = null,             textColor = Color.Black)
    CardState.Ended      -> CardTokens(borderColor = null,                   backgroundColor = SsmColors.EndedBg, textColor = SsmColors.TextMuted)
    CardState.InProgress -> CardTokens(borderColor = SsmColors.ActiveOrange, backgroundColor = null,             textColor = Color.Black)
    CardState.Success    -> CardTokens(borderColor = SsmColors.SuccessGreen, backgroundColor = null,             textColor = SsmColors.SuccessGreen)
    CardState.Error      -> CardTokens(borderColor = SsmColors.TextDanger,   backgroundColor = null,             textColor = SsmColors.TextDanger)
    CardState.Neutral    -> CardTokens(borderColor = null,                   backgroundColor = null,             textColor = Color.Black)
}

/**
 * 공통 StateCard Composable — Phase 11-02 의 28+ 화면 카드 일괄 적용용.
 * stateToCardTokens 결과를 androidx.compose.material3.Card 의 border + colors 로 적용.
 *
 * Compose 람다 early-exit 금지 (b2d8745 lesson) — if/else 양분 패턴 강제.
 */
@Composable
fun StateCard(
    state: CardState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = stateToCardTokens(state)
    val border = if (tokens.borderColor != null) BorderStroke(2.dp, tokens.borderColor) else null
    val colors = if (tokens.backgroundColor != null) {
        CardDefaults.cardColors(containerColor = tokens.backgroundColor)
    } else {
        CardDefaults.cardColors()
    }
    val baseModifier = modifier.fillMaxWidth()
    val finalModifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier

    Card(
        modifier = finalModifier,
        border = border,
        colors = colors,
    ) {
        Column(modifier = Modifier.padding(SsmSpacing.cardPadding)) {
            content()
        }
    }
}
