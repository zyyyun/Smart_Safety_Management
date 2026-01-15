package com.example.safe.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private fun safeToMaterialScheme(c: SafeColors, isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = Color(0xFFFF7A00),
        background = c.bg,
        surface = c.surface,
        surfaceVariant = c.chip,
        outline = c.border,
        onPrimary = Color.White,
        onBackground = c.text,
        onSurface = c.text,
        onSurfaceVariant = c.text,
    )
} else {
    lightColorScheme(
        primary = Color(0xFFFF7A00),
        background = c.bg,
        surface = c.surface,
        surfaceVariant = c.chip,
        outline = c.border,
        onPrimary = Color.White,
        onBackground = c.text,
        onSurface = c.text,
        onSurfaceVariant = c.text,
    )
}

@Composable
fun SafeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val safe = if (darkTheme) DarkSafeColors else LightSafeColors

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    CompositionLocalProvider(LocalSafeColors provides safe) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
