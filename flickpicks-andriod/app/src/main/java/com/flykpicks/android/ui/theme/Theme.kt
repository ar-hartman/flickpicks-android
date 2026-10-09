package com.flykpicks.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FlykColorScheme = lightColorScheme(
    primary = FlykColors.Brass,
    onPrimary = FlykColors.Canvas,
    background = FlykColors.Canvas,
    onBackground = FlykColors.Ink,
    surface = FlykColors.Surface,
    onSurface = FlykColors.Ink,
    surfaceVariant = FlykColors.Canvas,
    onSurfaceVariant = FlykColors.InkMuted,
    // Menus, dialogs and sheets draw on these; Material's defaults are lavender-tinted.
    surfaceContainerLowest = FlykColors.Surface,
    surfaceContainerLow = FlykColors.Surface,
    surfaceContainer = FlykColors.Surface,
    surfaceContainerHigh = FlykColors.Surface,
    surfaceContainerHighest = FlykColors.Canvas,
    outline = FlykColors.Ink.copy(alpha = 0.2f),
    outlineVariant = FlykColors.Ink.copy(alpha = 0.08f),
    secondary = FlykColors.Sapphire,
    tertiary = FlykColors.Jade,
    error = FlykColors.DangerRed,
)

/** Blueprint §3.7. The app is light-only, matching iOS. */
@Composable
fun FlykPicksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlykColorScheme,
        typography = FlykTypography,
        shapes = FlykShapes,
        content = content,
    )
}
