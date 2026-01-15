package com.example.smart_safety_management.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SafeColors(
    val isDark: Boolean,

    val bg: Color,
    val topBar: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val sub: Color,
    val bottomBar: Color,
    val chip: Color,
    val chipText: Color,
    val divider: Color,

    // ✅ 추가: "선택된 항" 배경 (주황 틴트)
    val selectedBg: Color,

    // ✅ 추가: Location 표 헤더 배경(요청한 옅은/진한 주황)
    val tableHeaderBg: Color,
)

private val ORANGE = Color(0xFFFF7A00)

val LightSafeColors = SafeColors(
    isDark = false,

    bg = Color(0xFFF4F6F9),
    topBar = Color(0xFFF4F6F9),
    surface = Color.White,
    border = Color(0xFFE5E7EB),
    text = Color(0xFF111827),
    sub = Color(0xFF6B7280),
    bottomBar = Color.White,
    chip = Color(0xFFF1F3F7),
    chipText = Color(0xFF3A4353),
    divider = Color(0xFFE5E7EB),

    // ✅ 라이트: 옅은 주황(요청한 느낌)
    selectedBg = ORANGE.copy(alpha = 0.12f),
    tableHeaderBg = ORANGE.copy(alpha = 0.12f),
)

val DarkSafeColors = SafeColors(
    isDark = true,

    bg = Color(0xFF0B0D10),
    topBar = Color(0xFF0B0D10),
    surface = Color(0xFF1E232B),
    border = Color(0xFF2A303A),
    text = Color(0xFFFFFFFF),
    sub = Color(0xFFC5CCD6),
    bottomBar = Color(0xFF0B0D10),
    chip = Color(0xFF2A303A),
    chipText = Color(0xFFFFFFFF),
    divider = Color(0xFF2A303A),

    // ✅ 다크: 더 진한 주황 틴트(요청한 “다크는 진하게”)
    selectedBg = ORANGE.copy(alpha = 0.22f),
    tableHeaderBg = ORANGE.copy(alpha = 0.22f),
)

val LocalSafeColors = staticCompositionLocalOf { LightSafeColors }
