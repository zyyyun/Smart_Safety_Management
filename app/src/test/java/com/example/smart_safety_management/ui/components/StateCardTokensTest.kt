package com.example.smart_safety_management.ui.components

import androidx.compose.ui.graphics.Color
import com.example.smart_safety_management.ui.SsmColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateCardTokensTest {
    @Test fun active_hasOrangeBorderAndNoBackground() {
        val t = stateToCardTokens(CardState.Active)
        assertEquals(SsmColors.ActiveOrange, t.borderColor)
        assertNull(t.backgroundColor)
        assertEquals(Color.Black, t.textColor)
    }
    @Test fun ended_hasNoBorderAndGrayBackground() {
        val t = stateToCardTokens(CardState.Ended)
        assertNull(t.borderColor)
        assertEquals(SsmColors.EndedBg, t.backgroundColor)
        assertEquals(SsmColors.TextMuted, t.textColor)
    }
    @Test fun inProgress_sameAsActive_orangeBorder() {
        val t = stateToCardTokens(CardState.InProgress)
        assertEquals(SsmColors.ActiveOrange, t.borderColor)
    }
    @Test fun success_hasGreenBorderAndGreenText() {
        val t = stateToCardTokens(CardState.Success)
        assertEquals(SsmColors.SuccessGreen, t.borderColor)
        assertEquals(SsmColors.SuccessGreen, t.textColor)
    }
    @Test fun error_hasDangerBorderAndDangerText() {
        val t = stateToCardTokens(CardState.Error)
        assertEquals(SsmColors.TextDanger, t.borderColor)
        assertEquals(SsmColors.TextDanger, t.textColor)
    }
    @Test fun neutral_hasNoBorderNoBackgroundBlackText() {
        val t = stateToCardTokens(CardState.Neutral)
        assertNull(t.borderColor)
        assertNull(t.backgroundColor)
        assertEquals(Color.Black, t.textColor)
    }
    @Test fun activeAndEnded_textColorsDiffer_visualSeparability() {
        assertEquals(false, stateToCardTokens(CardState.Active).textColor == stateToCardTokens(CardState.Ended).textColor)
    }
}
