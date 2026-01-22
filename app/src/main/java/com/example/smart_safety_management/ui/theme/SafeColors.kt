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

    val selectedBg: Color,
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

    selectedBg = ORANGE.copy(alpha = 0.12f),
    tableHeaderBg = ORANGE.copy(alpha = 0.12f),
)

// SafeColors.kt

val DarkSafeColors = SafeColors(
    isDark = true,

    bg = Color(0xFF000000),
    surface = Color(0xFF111318),
    topBar = Color(0xFF111318),

    // ✅ 여기 핵심
    bottomBar = Color(0xFF131416),

    border = Color(0xFF2A2F37),
    divider = Color(0xFF1F2937),
    text = Color.White,
    sub = Color(0xFF9CA3AF),
    chip = Color(0xFF1F2937),
    chipText = Color(0xFF9CA3AF),
    selectedBg = Color(0xFF1F2937),
    tableHeaderBg = Color(0xFF131416)
)


val LocalSafeColors = staticCompositionLocalOf { LightSafeColors }
