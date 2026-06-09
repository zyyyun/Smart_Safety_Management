package com.example.smart_safety_management.mobileai

object MobileYoloOutputParser {
    fun summarizeRows(rows: Array<FloatArray>): MobileYoloScoreSummary {
        var maxScore = Float.NEGATIVE_INFINITY
        var maxClassValue = Float.NEGATIVE_INFINITY
        var maxCombinedScore = Float.NEGATIVE_INFINITY
        var scoreAbove02 = 0
        var scoreAbove03 = 0
        var scoreAbove05 = 0
        var bestScoreRow: FloatArray? = null
        var bestCombinedRow: FloatArray? = null

        for (row in rows) {
            if (row.size < ROW_SIZE) continue

            val score = row[SCORE_INDEX]
            val classValue = row[CLASS_ID_INDEX]
            val combined = score * classValue

            if (score >= 0.20f) scoreAbove02 += 1
            if (score >= 0.30f) scoreAbove03 += 1
            if (score >= 0.50f) scoreAbove05 += 1
            if (score > maxScore) {
                maxScore = score
                bestScoreRow = row
            }
            if (classValue > maxClassValue) {
                maxClassValue = classValue
            }
            if (combined > maxCombinedScore) {
                maxCombinedScore = combined
                bestCombinedRow = row
            }
        }

        return MobileYoloScoreSummary(
            maxScore = if (maxScore.isFinite()) maxScore else 0f,
            maxClassValue = if (maxClassValue.isFinite()) maxClassValue else 0f,
            maxCombinedScore = if (maxCombinedScore.isFinite()) maxCombinedScore else 0f,
            scoreAbove02 = scoreAbove02,
            scoreAbove03 = scoreAbove03,
            scoreAbove05 = scoreAbove05,
            bestScoreRow = bestScoreRow,
            bestCombinedRow = bestCombinedRow
        )
    }

    fun parseNmsRows(
        rows: Array<FloatArray>,
        labels: List<String>,
        threshold: Float
    ): MobileFireResult {
        val best = rows
            .asSequence()
            .filter { row -> row.size >= ROW_SIZE && row[SCORE_INDEX] >= threshold }
            .maxByOrNull { row -> row[SCORE_INDEX] }
            ?: return MobileFireResult(detected = false)

        val classId = coerceClassId(best[CLASS_ID_INDEX], labels)
        val box = MobileFireBox(
            left = (best[X_CENTER_INDEX] - best[WIDTH_INDEX] / 2f).coerceIn(0f, 1f),
            top = (best[Y_CENTER_INDEX] - best[HEIGHT_INDEX] / 2f).coerceIn(0f, 1f),
            right = (best[X_CENTER_INDEX] + best[WIDTH_INDEX] / 2f).coerceIn(0f, 1f),
            bottom = (best[Y_CENTER_INDEX] + best[HEIGHT_INDEX] / 2f).coerceIn(0f, 1f),
            score = best[SCORE_INDEX],
            classId = classId,
            label = labels.getOrNull(classId).orEmpty()
        )
        return MobileFireResult(
            detected = true,
            confidence = best[SCORE_INDEX],
            box = box
        )
    }

    private fun coerceClassId(rawClassId: Float, labels: List<String>): Int {
        if (labels.isEmpty()) return 0
        return rawClassId.toInt().coerceIn(0, labels.lastIndex)
    }

    private const val ROW_SIZE = 6
    private const val X_CENTER_INDEX = 0
    private const val Y_CENTER_INDEX = 1
    private const val WIDTH_INDEX = 2
    private const val HEIGHT_INDEX = 3
    private const val SCORE_INDEX = 4
    private const val CLASS_ID_INDEX = 5
}

data class MobileYoloScoreSummary(
    val maxScore: Float,
    val maxClassValue: Float,
    val maxCombinedScore: Float,
    val scoreAbove02: Int,
    val scoreAbove03: Int,
    val scoreAbove05: Int,
    val bestScoreRow: FloatArray?,
    val bestCombinedRow: FloatArray?
)
