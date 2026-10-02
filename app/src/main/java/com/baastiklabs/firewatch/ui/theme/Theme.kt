package com.baastiklabs.firewatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.baastiklabs.firewatch.core.Themes
import com.baastiklabs.firewatch.core.model.Settings

fun hexColor(h: String): Color = Color(0xFF000000 or h.removePrefix("#").toLong(16))

/**
 * The palette in use (shared with the web app via core [Themes]). Observable state, so charts and
 * the calendar redraw when the theme changes, even inside draw code.
 */
object ThemeState {
    var palette by mutableStateOf(Themes.palette(Themes.DEFAULT, "dark", true))
}

private fun scheme(p: Themes.Palette) = if (p.dark) darkColorScheme(
    primary = hexColor(p.primary), onPrimary = hexColor(p.onPrimary),
    primaryContainer = hexColor(p.primarySoft), onPrimaryContainer = hexColor(p.onPrimarySoft),
    secondary = hexColor(p.secondary), onSecondary = hexColor(p.bg),
    secondaryContainer = hexColor(p.surface3), onSecondaryContainer = hexColor(p.text),
    tertiary = hexColor(p.tertiary), onTertiary = hexColor(p.bg),
    background = hexColor(p.bg), onBackground = hexColor(p.text),
    surface = hexColor(p.bg), onSurface = hexColor(p.text),
    surfaceVariant = hexColor(p.surface2), onSurfaceVariant = hexColor(p.muted),
    surfaceContainerLowest = hexColor(p.bg), surfaceContainerLow = hexColor(Themes.mix(p.bg, p.surface, 0.5)),
    surfaceContainer = hexColor(p.surface), surfaceContainerHigh = hexColor(p.surface2), surfaceContainerHighest = hexColor(p.surface3),
    outline = hexColor(p.muted), outlineVariant = hexColor(p.line),
) else lightColorScheme(
    primary = hexColor(p.primary), onPrimary = hexColor(p.onPrimary),
    primaryContainer = hexColor(p.primarySoft), onPrimaryContainer = hexColor(p.onPrimarySoft),
    secondary = hexColor(p.secondary), onSecondary = Color.White,
    secondaryContainer = hexColor(p.surface3), onSecondaryContainer = hexColor(p.text),
    tertiary = hexColor(p.tertiary), onTertiary = Color.White,
    background = hexColor(p.bg), onBackground = hexColor(p.text),
    surface = hexColor(p.bg), onSurface = hexColor(p.text),
    surfaceVariant = hexColor(p.surface2), onSurfaceVariant = hexColor(p.muted),
    surfaceContainerLowest = Color.White, surfaceContainerLow = hexColor(Themes.mix(p.bg, p.surface, 0.5)),
    surfaceContainer = hexColor(p.surface), surfaceContainerHigh = hexColor(p.surface2), surfaceContainerHighest = hexColor(p.surface3),
    outline = hexColor(p.muted), outlineVariant = hexColor(p.line),
)

/** Calendar heat: from clear (level 0) to the heaviest (level 6), in the current theme. */
object Heat {
    @Suppress("UNUSED_PARAMETER")
    fun color(level: Int, dark: Boolean): Color = hexColor(ThemeState.palette.heat[level.coerceIn(0, 6)])

    @Suppress("UNUSED_PARAMETER")
    fun onColor(level: Int, dark: Boolean): Color = hexColor(ThemeState.palette.onHeat[level.coerceIn(0, 6)])
}

/** Craving intensity 1 (calm) to 10 (strong), in the current theme (no red with calmer colours). */
fun cravingColor(intensity: Int): Color =
    lerp(hexColor(ThemeState.palette.cravingLow), hexColor(ThemeState.palette.cravingHigh), ((intensity.coerceIn(1, 10) - 1) / 9f))

/** Product colour in the current theme. */
fun kindHex(kind: String): Color = hexColor(ThemeState.palette.kinds[kind] ?: "#b0a49c")

@Composable
fun FirewatchTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val p = remember(settings.theme, settings.themeMode, settings.trueBlack, settings.calmColours, settings.colourBlindCharts, systemDark) {
        Themes.palette(settings.theme, settings.themeMode, systemDark, settings.trueBlack, settings.calmColours, settings.colourBlindCharts)
    }
    ThemeState.palette = p
    MaterialTheme(colorScheme = scheme(p), content = content)
}
