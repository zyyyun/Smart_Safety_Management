package com.example.smart_safety_management.mobileai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileYoloOutputParserTest {
    @Test
    fun parsesHighestFireDetectionAboveThreshold() {
        val rows = arrayOf(
            floatArrayOf(0.5f, 0.5f, 0.2f, 0.4f, 0.91f, 0f),
            floatArrayOf(0.4f, 0.4f, 0.2f, 0.2f, 0.60f, 1f)
        )
        val result = MobileYoloOutputParser.parseNmsRows(
            rows = rows,
            labels = listOf("fire", "smoke"),
            threshold = 0.50f
        )
        assertTrue(result.detected)
        assertEquals(0.91f, result.confidence!!, 0.001f)
        assertEquals("fire", result.box!!.label)
        assertEquals(0.4f, result.box!!.left, 0.001f)
        assertEquals(0.3f, result.box!!.top, 0.001f)
        assertEquals(0.6f, result.box!!.right, 0.001f)
        assertEquals(0.7f, result.box!!.bottom, 0.001f)
    }

    @Test
    fun ignoresRowsBelowThreshold() {
        val rows = arrayOf(floatArrayOf(0.5f, 0.5f, 0.2f, 0.4f, 0.49f, 0f))
        val result = MobileYoloOutputParser.parseNmsRows(
            rows = rows,
            labels = listOf("fire", "smoke"),
            threshold = 0.50f
        )
        assertFalse(result.detected)
    }

    @Test
    fun clampsBoxCoordinatesToUnitRange() {
        val rows = arrayOf(floatArrayOf(0.0f, 1.0f, 0.6f, 0.8f, 0.80f, 0f))
        val result = MobileYoloOutputParser.parseNmsRows(
            rows = rows,
            labels = listOf("fire", "smoke"),
            threshold = 0.50f
        )
        assertEquals(0.0f, result.box!!.left, 0.001f)
        assertEquals(0.6f, result.box!!.top, 0.001f)
        assertEquals(0.3f, result.box!!.right, 0.001f)
        assertEquals(1.0f, result.box!!.bottom, 0.001f)
    }

    @Test
    fun summarizesRawScoresForDiagnostics() {
        val bestScore = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.52f, 0.5f)
        val bestCombined = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.40f, 0.9f)
        val rows = arrayOf(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.19f, 1f),
            floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.31f, 0.5f),
            bestScore,
            bestCombined
        )

        val summary = MobileYoloOutputParser.summarizeRows(rows)

        assertEquals(0.52f, summary.maxScore, 0.001f)
        assertEquals(1.0f, summary.maxClassValue, 0.001f)
        assertEquals(0.36f, summary.maxCombinedScore, 0.001f)
        assertEquals(3, summary.scoreAbove02)
        assertEquals(3, summary.scoreAbove03)
        assertEquals(1, summary.scoreAbove05)
        assertTrue(summary.bestScoreRow === bestScore)
        assertTrue(summary.bestCombinedRow === bestCombined)
    }
}
