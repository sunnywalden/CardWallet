package com.aistudio.nfcshield

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenInfo
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenSource
import com.aistudio.nfcshield.data.remote.AppCheckFailureKind
import com.aistudio.nfcshield.data.remote.SyncDiagnosticReport
import com.aistudio.nfcshield.data.remote.SyncProbeStatus
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

    @Test
    fun `permission denied lock screen renders structured diagnostics and debug token copy action`() {
        var retryClicks = 0
        val report = SyncDiagnosticReport(
            authStatus = SyncProbeStatus.FAILED,
            authDetail = "登录会话刷新失败",
            appCheckStatus = SyncProbeStatus.FAILED,
            appCheckFailure = AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED,
            appCheckDetail = "403",
            networkAvailable = true,
            firestoreCode = "PERMISSION_DENIED",
            capturedAtMillis = 1L,
            debugTokenInfo = AppCheckDebugTokenInfo(
                token = "11111111-2222-3333-4444-555555555555",
                source = AppCheckDebugTokenSource.SECRETS
            )
        )

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
                    onSignOut = {},
                    onRetrySync = { retryClicks++ },
                    syncDiagnostic = report,
                    isRetryingSync = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sync_diag_card").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_diag_build").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_diag_copy_report").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_diag_auth").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_diag_appcheck").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_diag_network").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_debug_token_box").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_debug_token_copy").assertIsDisplayed()
        composeRule.onNodeWithText("Debug Token 来源：AI Studio Secrets (.env)").assertIsDisplayed()

        composeRule.onNodeWithTag("vault_error_retry_sync").performClick()
        assertEquals(1, retryClicks)
    }
}
