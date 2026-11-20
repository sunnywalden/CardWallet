package com.aistudio.nfcshield.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * "Obsidian" Material 3 ColorScheme.
 * Built around a deep #0A0A0A dark-gray OLED background and low-saturation #E0E0E0
 * primary emphasis with verified WCAG AA/AAA accessibility contrast ratios across
 * all surface tiers and semantic roles.
 */
val ObsidianColorScheme: ColorScheme = darkColorScheme(
    primary = ObsidianPrimary,
    onPrimary = ObsidianOnPrimary,
    primaryContainer = ObsidianPrimaryContainer,
    onPrimaryContainer = ObsidianOnPrimaryContainer,
    inversePrimary = ObsidianBackground,

    secondary = ObsidianSecondary,
    onSecondary = ObsidianOnSecondary,
    secondaryContainer = ObsidianSecondaryContainer,
    onSecondaryContainer = ObsidianOnSecondaryContainer,

    tertiary = ObsidianTertiary,
    onTertiary = ObsidianOnTertiary,
    tertiaryContainer = ObsidianTertiaryContainer,
    onTertiaryContainer = ObsidianOnTertiaryContainer,

    background = ObsidianBackground,
    onBackground = ObsidianOnBackground,

    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    surfaceTint = ObsidianPrimary,
    inverseSurface = ObsidianPrimary,
    inverseOnSurface = ObsidianBackground,

    surfaceContainerLowest = ObsidianSurfaceContainerLowest,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceContainerHighest,

    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    scrim = ObsidianBackground,

    error = ObsidianError,
    onError = ObsidianOnError,
    errorContainer = ObsidianErrorContainer,
    onErrorContainer = ObsidianOnErrorContainer
)

private val ObsidianShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ObsidianTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = Typography,
        shapes = ObsidianShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    ObsidianTheme(content = content)
}
