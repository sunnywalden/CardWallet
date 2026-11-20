package com.aistudio.nfcshield.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// "Obsidian" Material 3 Color Tokens
// Ultra-restrained pure dark-gray OLED palette (#0A0A0A canvas, #E0E0E0 accent)
// All foreground/background pairs are mathematically tuned for WCAG AA/AAA contrast.
// =============================================================================

// Neutral Achromatic Surfaces (zero blue cast)
val ObsidianBackground = Color(0xFF0A0A0A)
val ObsidianSurface = Color(0xFF121212)
val ObsidianSurfaceContainerLowest = Color(0xFF0A0A0A)
val ObsidianSurfaceContainerLow = Color(0xFF121212)
val ObsidianSurfaceContainer = Color(0xFF181818)
val ObsidianSurfaceContainerHigh = Color(0xFF202020)
val ObsidianSurfaceContainerHighest = Color(0xFF2A2A2A)

// Low-Saturation Primary & Secondary Emphasis
val ObsidianPrimary = Color(0xFFE0E0E0)            // 15.0:1 on #0A0A0A (WCAG AAA)
val ObsidianOnPrimary = Color(0xFF0A0A0A)          // 15.0:1 on #E0E0E0 (WCAG AAA)
val ObsidianPrimaryContainer = Color(0xFF242424)
val ObsidianOnPrimaryContainer = Color(0xFFEAEAEA) // 12.3:1 on #242424 (WCAG AAA)

val ObsidianSecondary = Color(0xFFB3B3B3)          // 9.4:1 on #0A0A0A (WCAG AAA)
val ObsidianOnSecondary = Color(0xFF0A0A0A)
val ObsidianSecondaryContainer = Color(0xFF1F1F1F)
val ObsidianOnSecondaryContainer = Color(0xFFD6D6D6)

// Desaturated Sage-Mint Tertiary (semantic active / NFC ready / verified)
val ObsidianTertiary = Color(0xFF8ABFA9)           // 9.7:1 on #0A0A0A (WCAG AAA)
val ObsidianOnTertiary = Color(0xFF0A0A0A)
val ObsidianTertiaryContainer = Color(0xFF16241F)
val ObsidianOnTertiaryContainer = Color(0xFFAED9C7)

// Accessible Neutral Typography & Hairlines
val ObsidianOnBackground = Color(0xFFE8E8E8)       // 16.2:1 on #0A0A0A (WCAG AAA)
val ObsidianOnSurface = Color(0xFFE8E8E8)          // 15.4:1 on #121212 (WCAG AAA)
val ObsidianOnSurfaceVariant = Color(0xFFA0A0A0)   // 7.1:1 on #121212 (WCAG AAA)
val ObsidianTextMuted = Color(0xFF858585)          // 5.0:1 on #121212 (WCAG AA)

val ObsidianOutline = Color(0xFF333333)
val ObsidianOutlineVariant = Color(0xFF1E1E1E)
val ObsidianHairline = Color(0x18FFFFFF)
val ObsidianHairlineStrong = Color(0x2EFFFFFF)

// Desaturated High-Contrast Semantic Status Colors
val ObsidianWarning = Color(0xFFE5B567)            // 10.6:1 on #0A0A0A (WCAG AAA)
val ObsidianWarningContainer = Color(0xFF241D11)
val ObsidianError = Color(0xFFE58A94)              // 8.0:1 on #0A0A0A (WCAG AAA)
val ObsidianOnError = Color(0xFF0A0A0A)
val ObsidianErrorContainer = Color(0xFF261316)
val ObsidianOnErrorContainer = Color(0xFFF2B6BC)

val ObsidianSteelBlue = Color(0xFF8FB0D6)          // Desaturated steel blue (8.5:1 on #0A0A0A)
val ObsidianSteelBlueContainer = Color(0xFF151E28)
val ObsidianSageGreen = Color(0xFF7EC49E)          // Desaturated emerald (9.7:1 on #0A0A0A)
val ObsidianMutedIndigo = Color(0xFFA1A6E0)        // Desaturated indigo (8.3:1 on #0A0A0A)

// =============================================================================
// Backwards-Compatible Token Aliases (mapped directly to Obsidian tokens)
// =============================================================================
val VaultObsidian = ObsidianBackground
val VaultSurface = ObsidianSurface
val VaultSurfaceElevated = ObsidianSurfaceContainer
val VaultSurfaceHighest = ObsidianSurfaceContainerHigh
val VaultBorder = ObsidianOutline
val VaultBorderSubtle = ObsidianOutlineVariant
val VaultHairline = ObsidianHairline
val VaultHairlineStrong = ObsidianHairlineStrong

val VaultPrimaryCta = ObsidianPrimary
val VaultOnPrimaryCta = ObsidianOnPrimary

val VaultCyan = ObsidianTertiary
val VaultCyanDark = ObsidianOnTertiary
val VaultCyanContainer = ObsidianTertiaryContainer

val VaultIceBlue = ObsidianSteelBlue
val VaultIceBlueContainer = ObsidianSteelBlueContainer

val VaultAmber = ObsidianWarning
val VaultAmberContainer = ObsidianWarningContainer

val VaultEmerald = ObsidianSageGreen
val VaultIndigo = ObsidianMutedIndigo
val VaultRose = ObsidianError
val VaultErrorContainer = ObsidianErrorContainer

val VaultTextPrimary = ObsidianOnBackground
val VaultTextSecondary = ObsidianOnSurfaceVariant
val VaultTextMuted = ObsidianTextMuted
