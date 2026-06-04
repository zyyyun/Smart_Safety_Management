package com.example.smart_safety_management.mobileai

object MobileYoloOutputParser {
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
