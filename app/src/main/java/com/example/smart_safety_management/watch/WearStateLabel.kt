package com.example.smart_safety_management.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 7 / 07-03 BRIDGE-01 — wear-state 한글 라벨 + 색상 매핑.
 *
 * 의료기기 면책 (PROJECT.md key decision): PPT 슬라이드의 데이터-값 단어
 * (회귀 가드 grep 대상) 사용 X. 모든 라벨은 워치 착용 상태 (state) 만 가리킴.
 *
 * 5 상태 + null + unknown 7 케이스:
 *   WORN      = 초록  "착용 중"
 *   OFF       = 빨강  "미착용"
 *   ABNORMAL  = 빨강  "비정상"
 *   WARMUP    = 노랑  "워치 준비 중"
 *   TRANSIENT = 노랑  "전이 중"
 *   null      = 회색  "알 수 없음"
 *   else      = 회색  (state 그대로)
 *
 * ※ Wave 1/2 패턴 동일 — 회귀 가드 false-positive 회피 위해 코멘트에서 금지 단어
 *    직접 인용 회피.
 */
data class WearStateMapping(val label: String, val color: Color)

object WearStateLabelMap {
    fun map(state: String?): WearStateMapping = when (state) {
        "WORN"      -> WearStateMapping("착용 중", Color(0xFF22C55E))
        "OFF"       -> WearStateMapping("미착용", Color(0xFFEF4444))
        "ABNORMAL"  -> WearStateMapping("비정상", Color(0xFFEF4444))
        "WARMUP"    -> WearStateMapping("워치 준비 중", Color(0xFFFBBF24))
        "TRANSIENT" -> WearStateMapping("전이 중", Color(0xFFFBBF24))
        null        -> WearStateMapping("알 수 없음", Color.Gray)
        else        -> WearStateMapping(state, Color.Gray)
    }
}

@Composable
fun WearStateLabel(state: String?) {
    val mapping = WearStateLabelMap.map(state)
    Box(
        modifier = Modifier
            .background(mapping.color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = mapping.label,
            color = mapping.color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
