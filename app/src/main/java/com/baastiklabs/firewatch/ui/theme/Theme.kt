package com.baastiklabs.firewatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A2F),
    onPrimary = Color(0xFF2A1206),
    primaryContainer = Color(0xFF4A2412),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFFFB35C),
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = Color(0xFF3F2C1E),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF8FC7B8),
    onTertiary = Color(0xFF00201A),
    background = Color(0xFF140E0C),
    onBackground = Color(0xFFF3E7DF),
    surface = Color(0xFF140E0C),
    onSurface = Color(0xFFF3E7DF),
    surfaceVariant = Color(0xFF2A201B),
    onSurfaceVariant = Color(0xFFD3C2B8),
    surfaceContainerLowest = Color(0xFF0F0A08),
    surfaceContainerLow = Color(0xFF1A1310),
    surfaceContainer = Color(0xFF1F1714),
    surfaceContainerHigh = Color(0xFF2A201B),
    surfaceContainerHighest = Color(0xFF352924),
    outline = Color(0xFF9C8B80),
    outlineVariant = Color(0xFF4A3D36),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB8480F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF3A1402),
    secondary = Color(0xFF8A5100),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE3C4),
    onSecondaryContainer = Color(0xFF2C1700),
    tertiary = Color(0xFF2E6B5E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFBEBE2),
    surfaceContainerHigh = Color(0xFFF5E4DA),
    surfaceContainerHighest = Color(0xFFEFDED4),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD8C2B7),
)

/** Calendar heat: from clear (level 0) to wildfire (level 6). */
object Heat {
    private val dark = listOf(
        Color(0xFF241B17), Color(0xFF4A3526), Color(0xFF6E3F1F), Color(0xFF9A4A1C),
        Color(0xFFC8581C), Color(0xFFE86A24), Color(0xFFFF8F3A),
    )
    private val light = listOf(
        Color(0xFFF1E6DF), Color(0xFFFFE0C2), Color(0xFFFFC08F), Color(0xFFFF9E5E),
        Color(0xFFF27A36), Color(0xFFD9591B), Color(0xFFB23F0B),
    )

    fun color(level: Int, dark: Boolean): Color = (if (dark) this.dark else light)[level.coerceIn(0, 6)]

    fun onColor(level: Int, dark: Boolean): Color =
        if (dark) Color(0xFFF3E7DF) else if (level >= 4) Color.White else Color(0xFF221A16)
}

/** Craving intensity 1 (calm teal) to 10 (red). */
fun cravingColor(intensity: Int): Color =
    lerp(Color(0xFF5FA893), Color(0xFFE0443A), ((intensity.coerceIn(1, 10) - 1) / 9f))

@Composable
fun FirewatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
