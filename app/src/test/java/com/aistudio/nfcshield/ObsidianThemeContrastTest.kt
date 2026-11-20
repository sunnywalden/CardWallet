package com.aistudio.nfcshield

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.aistudio.nfcshield.ui.theme.ObsidianBackground
import com.aistudio.nfcshield.ui.theme.ObsidianColorScheme
import com.aistudio.nfcshield.ui.theme.ObsidianPrimary
import com.aistudio.nfcshield.ui.theme.ObsidianTextMuted
import com.aistudio.nfcshield.ui.theme.ObsidianTheme
import com.aistudio.nfcshield.ui.theme.ObsidianWarning
import com.aistudio.nfcshield.ui.theme.ObsidianWarningContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

/**
 * Verifies WCAG 2.1 accessibility contrast ratios and Compose Material 3 binding
 * for the custom 'Obsidian' ColorScheme (#0A0A0A deep dark background + #E0E0E0
 * low-saturation accent).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObsidianThemeContrastTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.luminance().toDouble()
        val l2 = background.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `obsidian core palette uses #0A0A0A background and #E0E0E0 primary accent`() {
        assertEquals(Color(0xFF0A0A0A), ObsidianBackground)
        assertEquals(Color(0xFFE0E0E0), ObsidianPrimary)
        assertEquals(ObsidianBackground, ObsidianColorScheme.background)
        assertEquals(ObsidianPrimary, ObsidianColorScheme.primary)
    }

    @Test
    fun `obsidian color pairs meet WCAG AA and AAA contrast requirements`() {
        val scheme = ObsidianColorScheme

        // Primary emphasis & inverse CTA pairs (AAA >= 7.0)
        assertTrue(
            "primary vs background must meet AAA (>= 7.0)",
            contrastRatio(scheme.primary, scheme.background) >= 7.0
        )
        assertTrue(
            "onPrimary vs primary must meet AAA (>= 7.0)",
            contrastRatio(scheme.onPrimary, scheme.primary) >= 7.0
        )
        assertTrue(
            "onPrimaryContainer vs primaryContainer must meet AAA (>= 7.0)",
            contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 7.0
        )

        // Neutral typography across all dark surface tiers (AAA >= 7.0, AA >= 4.5)
        assertTrue(
            "onBackground vs background must meet AAA (>= 7.0)",
            contrastRatio(scheme.onBackground, scheme.background) >= 7.0
        )
        assertTrue(
            "onSurface vs surface must meet AAA (>= 7.0)",
            contrastRatio(scheme.onSurface, scheme.surface) >= 7.0
        )
        assertTrue(
            "onSurface vs surfaceContainerHigh must meet AAA (>= 7.0)",
            contrastRatio(scheme.onSurface, scheme.surfaceContainerHigh) >= 7.0
        )
        assertTrue(
            "onSurfaceVariant vs surface must meet AA+ (>= 4.5)",
            contrastRatio(scheme.onSurfaceVariant, scheme.surface) >= 4.5
        )
        assertTrue(
            "onSurfaceVariant vs surfaceContainer must meet AA+ (>= 4.5)",
            contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainer) >= 4.5
        )
        assertTrue(
            "muted metadata text vs surface must meet WCAG AA (>= 4.5)",
            contrastRatio(ObsidianTextMuted, scheme.surface) >= 4.5
        )

        // Low-saturation secondary, tertiary, warning, and error pairs
        assertTrue(
            "secondary vs background must meet AAA (>= 7.0)",
            contrastRatio(scheme.secondary, scheme.background) >= 7.0
        )
        assertTrue(
            "tertiary vs background must meet AAA (>= 7.0)",
            contrastRatio(scheme.tertiary, scheme.background) >= 7.0
        )
        assertTrue(
            "onTertiaryContainer vs tertiaryContainer must meet AAA (>= 7.0)",
            contrastRatio(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 7.0
        )
        assertTrue(
            "warning vs warningContainer must meet AAA (>= 7.0)",
            contrastRatio(ObsidianWarning, ObsidianWarningContainer) >= 7.0
        )
        assertTrue(
            "error vs background must meet AAA (>= 7.0)",
            contrastRatio(scheme.error, scheme.background) >= 7.0
        )
        assertTrue(
            "onErrorContainer vs errorContainer must meet AAA (>= 7.0)",
            contrastRatio(scheme.onErrorContainer, scheme.errorContainer) >= 7.0
        )
    }

    @Test
    fun `obsidian theme exposes ObsidianColorScheme via MaterialTheme`() {
        var resolvedScheme: ColorScheme? = null
        composeRule.setContent {
            ObsidianTheme {
                resolvedScheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()

        assertEquals(ObsidianColorScheme, resolvedScheme)
    }
}
