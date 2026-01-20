package com.example.smart_safety_management.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = MainOrange,
    primaryVariant = MainOrangeDark,
    secondary = StatusGreen,
    background = Color(0xFF121212),
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorPalette = lightColors(
    primary = MainOrange,
    primaryVariant = MainOrangeDark,
    secondary = StatusGreen,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextDark,
    onSurface = Color.Black
)

@Composable
fun Smart_Safety_ManagementTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val materialColors = if (darkTheme) DarkColorPalette else LightColorPalette
    val safeColors = if (darkTheme) DarkSafeColors else LightSafeColors

    CompositionLocalProvider(LocalSafeColors provides safeColors) {
        MaterialTheme(
            colors = materialColors,
            typography = Typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}
