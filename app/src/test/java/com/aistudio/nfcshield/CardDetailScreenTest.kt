package com.aistudio.nfcshield

import android.nfc.NfcAdapter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.ui.screens.CardDetailScreen
import com.aistudio.nfcshield.ui.theme.ObsidianTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class CardDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleCard(
        activeHce: Boolean,
        hceSupported: Boolean = true
    ) = DecryptedAccessCard(
        id = "card-detail-1",
        label = "长安通",
        category = CardCategory.TRANSIT,
        accentColor = CardAccentColor.ROSE,
        primaryTech = if (hceSupported) NfcTechType.ISO_DEP else NfcTechType.MIFARE_CLASSIC,
        maskedUid = "D1:**:**:BD",
        hceSupported = hceSupported,
        activeHce = activeHce,
        emulationCount = 2,
        lastEmulatedAt = null,
        createdAt = null,
        updatedAt = null,
        secrets = CardSecretsPayload(
            uidHex = "D1:EA:D7:BD",
            aidHex = if (hceSupported) "F04E464353484945" else ""
        )
    )

    private val nfcOnStatus = NfcHardwareStatus(
        nfcSupported = true,
        nfcEnabled = true,
        hceSupported = true,
        registeredAids = listOf("F04E464353484945"),
        adapterState = NfcAdapter.STATE_ON,
        adapterStateLabel = "已开启 (STATE_ON)"
    )

    private val nfcOffStatus = NfcHardwareStatus(
        nfcSupported = true,
        nfcEnabled = false,
        hceSupported = true,
        registeredAids = emptyList(),
        adapterState = NfcAdapter.STATE_OFF,
        adapterStateLabel = "已关闭 (STATE_OFF)"
    )

    @Test
    fun `floating HCE switch toggles emulation and updates live indicator status`() {
        var cardState by mutableStateOf(sampleCard(activeHce = false))
        var toggleCount = 0

        composeRule.setContent {
            ObsidianTheme {
                CardDetailScreen(
                    card = cardState,
                    onBack = {},
                    onToggleHce = {
                        toggleCount++
                        cardState = cardState.copy(activeHce = !cardState.activeHce)
                    },
                    onUpdateCard = { _, _, _, _, _, onDone -> onDone(Result.success(Unit)) },
                    onDeleteCard = {},
                    onShowMessage = {},
                    nfcStatus = nfcOnStatus
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_floating_hce_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_hce_live_dot").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_hce_status_text").assertTextContains("HCE 模拟待机")
        composeRule.onNodeWithTag("detail_floating_hce_switch").assertIsOff()

        // Toggle ON -> enters live emulating state with pulsing dot
        composeRule.onNodeWithTag("detail_floating_hce_switch").performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleCount)
        composeRule.onNodeWithTag("detail_floating_hce_switch").assertIsOn()
        composeRule.onNodeWithTag("detail_hce_status_text").assertTextContains("HCE 正在模拟中")
    }

    @Test
    fun `floating HCE indicator warns when card is active but system NFC is off`() {
        composeRule.setContent {
            ObsidianTheme {
                CardDetailScreen(
                    card = sampleCard(activeHce = true),
                    onBack = {},
                    onToggleHce = {},
                    onUpdateCard = { _, _, _, _, _, onDone -> onDone(Result.success(Unit)) },
                    onDeleteCard = {},
                    onShowMessage = {},
                    nfcStatus = nfcOffStatus
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_floating_hce_switch").assertIsOn()
        composeRule.onNodeWithTag("detail_hce_status_text")
            .assertTextContains("HCE 已开启 · 系统 NFC 未启用")
    }

    @Test
    fun `floating HCE switch is disabled for physical read-only cards`() {
        composeRule.setContent {
            ObsidianTheme {
                CardDetailScreen(
                    card = sampleCard(activeHce = false, hceSupported = false),
                    onBack = {},
                    onToggleHce = {},
                    onUpdateCard = { _, _, _, _, _, onDone -> onDone(Result.success(Unit)) },
                    onDeleteCard = {},
                    onShowMessage = {},
                    nfcStatus = nfcOnStatus
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_floating_hce_switch").assertIsNotEnabled()
        composeRule.onNodeWithTag("detail_hce_status_text")
            .assertTextContains("物理只读卡片 · 不支持 HCE")
    }
}
