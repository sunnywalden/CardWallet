package com.aistudio.nfcshield

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aistudio.nfcshield.ui.screens.VaultLockScreen
import com.aistudio.nfcshield.ui.theme.ObsidianTheme
import com.aistudio.nfcshield.ui.viewmodel.VaultGateState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class VaultLockScreenErrorRecoveryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `permission denied lock screen shows recovery hint and retry button`() {
        var retryClicks = 0
        var signOutClicks = 0

        composeRule.setContent {
            ObsidianTheme {
                VaultLockScreen(
                    gateState = VaultGateState.Error("PERMISSION_DENIED: Missing or insufficient permissions."),
                    biometricPrefEnabled = true,
                    userEmail = "user@example.com",
                    onSetupPin = { _, _, _ -> },
                    onSubmitUnlockPin = {},
                    onPrepareBiometricChallenge = {},
                    onBiometricCryptoSuccess = { _, _, _ -> },
                    onBiometricSuccess = {},
                    onClearError = {},
                    onSignOut = { signOutClicks++ },
                    onRetrySync = { retryClicks++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("云端保险库同步受限").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_error_retry_sync").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_error_sign_out").assertIsDisplayed()

        composeRule.onNodeWithTag("vault_error_retry_sync").performClick()
        assertEquals(1, retryClicks)
        assertEquals(0, signOutClicks)
    }
}
