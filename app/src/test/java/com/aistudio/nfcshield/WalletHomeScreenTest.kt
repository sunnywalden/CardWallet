package com.aistudio.nfcshield

import android.nfc.NfcAdapter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.data.model.UiState
import com.aistudio.nfcshield.data.remote.AppCheckFailureKind
import com.aistudio.nfcshield.data.remote.SyncDiagnosticReport
import com.aistudio.nfcshield.data.remote.SyncProbeStatus
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.ui.screens.WalletHomeScreen
import com.aistudio.nfcshield.ui.theme.ObsidianTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WalletHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleCard(
        id: String,
        label: String,
        activeHce: Boolean,
        hceSupported: Boolean = true,
        tags: List<String> = emptyList()
    ) = DecryptedAccessCard(
        id = id,
        label = label,
        category = CardCategory.OFFICE,
        accentColor = CardAccentColor.CYAN,
        primaryTech = if (hceSupported) NfcTechType.ISO_DEP else NfcTechType.MIFARE_CLASSIC,
        hceSupported = hceSupported,
        activeHce = activeHce,
        maskedUid = "04:**:**:A1",
        emulationCount = 3,
        lastEmulatedAt = null,
        createdAt = null,
        updatedAt = null,
        secrets = CardSecretsPayload(
            uidHex = "04:11:22:A1",
            aidHex = if (hceSupported) "F04E464353484945" else "",
            tags = tags
        )
    )

    private val readyNfcStatus = NfcHardwareStatus(
        nfcSupported = true,
        nfcEnabled = true,
        hceSupported = true,
        registeredAids = listOf("F04E464353484945"),
        adapterState = NfcAdapter.STATE_ON,
        adapterStateLabel = "已开启 (STATE_ON)"
    )

    @Test
    fun `renders top hero focal card and clean non-duplicated below-hero list`() {
        val activeHero = sampleCard(id = "hero-1", label = "总部工牌", activeHce = true)
        val secondaryCard = sampleCard(id = "list-2", label = "东门小区", activeHce = false)
        var toggledCardId: String? = null
        var clickedCardId: String? = null

        composeRule.setContent {
            ObsidianTheme {
                WalletHomeScreen(
                    cardsState = UiState.Success(listOf(secondaryCard, activeHero)),
                    nfcStatus = readyNfcStatus,
                    recentTapEvents = emptyList(),
                    onCardClick = { clickedCardId = it.id },
                    onToggleHce = { toggledCardId = it.id },
                    onNavigateToScan = {},
                    onOpenNfcSettings = {},
                    onLockVaultNow = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Hero stage + LazyColumn + secondary card list all render without duplicate card tags
        composeRule.onNodeWithTag("wallet_lazy_column").assertIsDisplayed()
        composeRule.onNodeWithTag("wallet_hero_focal_card").assertIsDisplayed()
        composeRule.onNodeWithTag("wallet_card_list_section").assertIsDisplayed()
        composeRule.onNodeWithTag("access_card_hero-1").assertIsDisplayed()
        composeRule.onNodeWithTag("access_card_list-2").assertIsDisplayed()

        // Clicking HCE toggle on the list row dispatches the correct card
        composeRule.onNodeWithTag("toggle_hce_list-2").performClick()
        assertEquals("list-2", toggledCardId)

        // Clicking hero card opens detail
        composeRule.onNodeWithTag("access_card_hero-1").performClick()
        assertEquals("hero-1", clickedCardId)
    }

    @Test
    fun `offline banner appears with local cache and opens the diagnostics dialog`() {
        var retries = 0
        val report = SyncDiagnosticReport(
            authStatus = SyncProbeStatus.OK,
            authDetail = null,
            appCheckStatus = SyncProbeStatus.FAILED,
            appCheckFailure = AppCheckFailureKind.NETWORK,
            appCheckDetail = "timeout",
            networkAvailable = true,
            firestoreCode = "UNAVAILABLE",
            capturedAtMillis = 1L
        )

        composeRule.setContent {
            ObsidianTheme {
                WalletHomeScreen(
                    cardsState = UiState.Success(listOf(sampleCard(id = "hero-1", label = "总部工牌", activeHce = true))),
                    nfcStatus = readyNfcStatus,
                    recentTapEvents = emptyList(),
                    onCardClick = {},
                    onToggleHce = {},
                    onRetrySync = { retries++ },
                    syncIssue = report,
                    onNavigateToScan = {},
                    onOpenNfcSettings = {},
                    onLockVaultNow = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("offline_sync_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("offline_sync_retry").performClick()
        assertEquals(1, retries)

        composeRule.onNodeWithTag("offline_sync_details").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sync_diagnostics_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_diag_appcheck").assertIsDisplayed()
    }

    @Test
    fun `lazyColumn smoothly scrolls to lower list cards below hero stage`() {
        val cards = listOf(
            sampleCard(id = "hero-active", label = "研发中心门禁", activeHce = true),
            sampleCard(id = "card-2", label = "地下车库 A 区", activeHce = false),
            sampleCard(id = "card-3", label = "南门社区门禁", activeHce = false),
            sampleCard(id = "card-4", label = "健身会所卡", activeHce = false),
            sampleCard(id = "card-5", label = "图书馆借阅卡", activeHce = false),
            sampleCard(id = "card-6", label = "档案室凭证", activeHce = false)
        )

        composeRule.setContent {
            ObsidianTheme {
                WalletHomeScreen(
                    cardsState = UiState.Success(cards),
                    nfcStatus = readyNfcStatus,
                    recentTapEvents = emptyList(),
                    onCardClick = {},
                    onToggleHce = {},
                    onNavigateToScan = {},
                    onOpenNfcSettings = {},
                    onLockVaultNow = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wallet_lazy_column")
            .performScrollToNode(hasTestTag("access_card_card-6"))
        composeRule.onNodeWithTag("access_card_card-6").assertIsDisplayed()
    }

    @Test
    fun `left swipe on list card opens delete confirmation dialog and respects cancel and confirm`() {
        val activeHero = sampleCard(id = "hero-1", label = "总部工牌", activeHce = true)
        val swipeTarget = sampleCard(id = "list-2", label = "东门小区", activeHce = false)
        var deletedCardId: String? = null

        composeRule.setContent {
            ObsidianTheme {
                WalletHomeScreen(
                    cardsState = UiState.Success(listOf(activeHero, swipeTarget)),
                    nfcStatus = readyNfcStatus,
                    recentTapEvents = emptyList(),
                    onCardClick = {},
                    onToggleHce = {},
                    onDeleteCard = { deletedCardId = it.id },
                    onNavigateToScan = {},
                    onOpenNfcSettings = {},
                    onLockVaultNow = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Swipe left on the secondary list row -> dialog opens, card is not deleted yet
        composeRule.onNodeWithTag("swipe_delete_container_list-2").performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_card_confirm_dialog").assertIsDisplayed()
        assertNull(deletedCardId)

        // Cancel dismisses dialog and preserves the card without calling onDeleteCard
        composeRule.onNodeWithTag("cancel_delete_card_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_card_confirm_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("access_card_list-2").assertIsDisplayed()
        assertNull(deletedCardId)

        // Swipe left again and confirm -> triggers onDeleteCard with target card ID
        composeRule.onNodeWithTag("swipe_delete_container_list-2").performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_card_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_card_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_card_confirm_dialog").assertDoesNotExist()
        assertEquals("list-2", deletedCardId)
    }

    @Test
    fun `tag filter chip group filters cards by tag and resets cleanly`() {
        val commuteHero = sampleCard(
            id = "hero-commute",
            label = "长安通公交卡",
            activeHce = true,
            tags = listOf("通勤", "高频")
        )
        val officeCard = sampleCard(
            id = "card-office",
            label = "总部研发门禁",
            activeHce = false,
            tags = listOf("办公", "高频")
        )
        val homeCard = sampleCard(
            id = "card-home",
            label = "南门小区门禁",
            activeHce = false,
            tags = listOf("家门")
        )

        composeRule.setContent {
            ObsidianTheme {
                WalletHomeScreen(
                    cardsState = UiState.Success(listOf(commuteHero, officeCard, homeCard)),
                    nfcStatus = readyNfcStatus,
                    recentTapEvents = emptyList(),
                    onCardClick = {},
                    onToggleHce = {},
                    onNavigateToScan = {},
                    onOpenNfcSettings = {},
                    onLockVaultNow = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Tag filter row and tag chips render
        composeRule.onNodeWithTag("wallet_tag_filter_row").assertIsDisplayed()
        composeRule.onNodeWithTag("tag_filter_all_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("tag_filter_chip_高频").assertIsDisplayed()
        composeRule.onNodeWithTag("tag_filter_chip_通勤").assertIsDisplayed()

        // Selecting "通勤" filters the results list down to only the matching card
        composeRule.onNodeWithTag("tag_filter_chip_通勤").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("swipe_delete_container_hero-commute").assertIsDisplayed()
        composeRule.onNodeWithTag("swipe_delete_container_card-office").assertDoesNotExist()
        composeRule.onNodeWithTag("swipe_delete_container_card-home").assertDoesNotExist()

        // Selecting "全部标签" restores full card list and top focal card
        composeRule.onNodeWithTag("tag_filter_all_chip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wallet_hero_focal_card").assertIsDisplayed()
        composeRule.onNodeWithTag("swipe_delete_container_card-office").assertIsDisplayed()
        composeRule.onNodeWithTag("swipe_delete_container_card-home").assertIsDisplayed()
    }
}
