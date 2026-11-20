package com.example

import android.nfc.NfcAdapter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.nfc.NfcHardwareStatus
import com.example.ui.components.NfcLiveStatusBanner
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NfcLiveStatusBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows enabled state without settings shortcut when nfc is on`() {
        val status = NfcHardwareStatus(
            nfcSupported = true,
            nfcEnabled = true,
            hceSupported = true,
            registeredAids = listOf("F04E464353484945"),
            adapterState = NfcAdapter.STATE_ON,
            adapterStateLabel = "已开启 (STATE_ON)"
        )

        composeRule.setContent {
            MyApplicationTheme {
                NfcLiveStatusBanner(
                    nfcStatus = status,
                    onOpenNfcSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nfc_live_status_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("nfc_live_status_title").assertTextEquals("NFC 适配器已开启")
        composeRule.onNodeWithTag("nfc_status_open_settings_button").assertDoesNotExist()
    }

    @Test
    fun `shows disabled state with clickable system settings shortcut when nfc is off`() {
        var settingsClicked = false
        val status = NfcHardwareStatus(
            nfcSupported = true,
            nfcEnabled = false,
            hceSupported = true,
            registeredAids = emptyList(),
            adapterState = NfcAdapter.STATE_OFF,
            adapterStateLabel = "已关闭 (STATE_OFF)"
        )

        composeRule.setContent {
            MyApplicationTheme {
                NfcLiveStatusBanner(
                    nfcStatus = status,
                    onOpenNfcSettings = { settingsClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nfc_live_status_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("nfc_live_status_title").assertTextEquals("NFC 适配器已关闭")
        composeRule.onNodeWithTag("nfc_status_open_settings_button")
            .assertIsDisplayed()
            .performClick()

        assertTrue("Clicking settings shortcut should trigger callback", settingsClicked)
    }
}
