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

val DarkSafeColors = SafeColors(
    isDark = true,

    bg = Color(0xFF0B0D10),
    topBar = Color(0xFF000000), // ✅ TopBar를 "완전 진한 검정"으로
    surface = Color(0xFF1E232B),
    border = Color(0xFF2A303A),
    text = Color.White,
    sub = Color(0xFFC5CCD6),
    bottomBar = Color(0xFF0B0D10),
    chip = Color(0xFF2A303A),
    chipText = Color.White,
    divider = Color(0xFF2A303A),

    selectedBg = ORANGE.copy(alpha = 0.22f),
    tableHeaderBg = ORANGE.copy(alpha = 0.22f),
)

val LocalSafeColors = staticCompositionLocalOf { LightSafeColors }
