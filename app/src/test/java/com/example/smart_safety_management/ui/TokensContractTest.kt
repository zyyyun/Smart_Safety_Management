package com.example.smart_safety_management.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TokensContractTest {
    @Test fun ssmColors_activeOrange_matchesContextHex() {
        assertEquals(Color(0xFFF59E0B), SsmColors.ActiveOrange)
    }
    @Test fun ssmColors_endedBg_matchesContextHex() {
        assertEquals(Color(0xFFF3F4F6), SsmColors.EndedBg)
    }
    @Test fun ssmColors_textMuted_matchesContextHex() {
        assertEquals(Color(0xFF6B7280), SsmColors.TextMuted)
    }
    @Test fun ssmColors_textInfo_matchesContextHex() {
        assertEquals(Color(0xFF2563EB), SsmColors.TextInfo)
    }
    @Test fun ssmColors_textDanger_matchesContextHex() {
        assertEquals(Color(0xFFEF4444), SsmColors.TextDanger)
    }
    @Test fun ssmColors_successGreen_matchesContextHex() {
        assertEquals(Color(0xFF22C55E), SsmColors.SuccessGreen)
    }
    @Test fun ssmColors_allSixAreDistinct() {
        val set = setOf(
            SsmColors.ActiveOrange, SsmColors.EndedBg, SsmColors.TextMuted,
            SsmColors.TextInfo, SsmColors.TextDanger, SsmColors.SuccessGreen,
        )
        assertEquals(6, set.size)  // 모두 distinct = 시각 분리 가능
    }
    @Test fun ssmSpacing_threeValuesAreDistinct() {
        val set = setOf(SsmSpacing.cardPadding, SsmSpacing.sectionSpacing, SsmSpacing.rowSpacing)
        assertEquals(3, set.size)
    }
    @Test fun ssmSpacing_cardPadding_is12dp() {
        assertEquals(12.dp, SsmSpacing.cardPadding)
    }
}
