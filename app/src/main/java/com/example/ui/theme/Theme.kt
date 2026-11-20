package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VaultDarkColorScheme = darkColorScheme(
    primary = VaultCyan,
    onPrimary = VaultCyanDark,
    primaryContainer = VaultCyanContainer,
    onPrimaryContainer = VaultCyan,
    secondary = VaultIceBlue,
    onSecondary = VaultObsidian,
    secondaryContainer = VaultIceBlueContainer,
    onSecondaryContainer = VaultIceBlue,
    tertiary = VaultAmber,
    onTertiary = VaultObsidian,
    tertiaryContainer = VaultAmberContainer,
    onTertiaryContainer = VaultAmber,
    background = VaultObsidian,
    onBackground = VaultTextPrimary,
    surface = VaultSurface,
    onSurface = VaultTextPrimary,
    surfaceVariant = VaultSurfaceElevated,
    onSurfaceVariant = VaultTextSecondary,
    surfaceContainerLow = VaultSurface,
    surfaceContainer = VaultSurfaceElevated,
    surfaceContainerHigh = VaultSurfaceHighest,
    outline = VaultBorder,
    outlineVariant = VaultBorderSubtle,
    error = VaultRose,
    onError = VaultObsidian,
    errorContainer = VaultErrorContainer,
    onErrorContainer = VaultRose
)

private val VaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VaultDarkColorScheme,
        typography = Typography,
        shapes = VaultShapes,
        content = content
    )
}
