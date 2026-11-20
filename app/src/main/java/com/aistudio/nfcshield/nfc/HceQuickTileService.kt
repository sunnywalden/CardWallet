package com.aistudio.nfcshield.nfc

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.aistudio.nfcshield.MainActivity
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.data.local.LocalVaultStore
import com.aistudio.nfcshield.data.model.ActiveHceCardConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class QuickTilePresentation(
    val tileState: Int,
    val label: String,
    val subtitle: String,
    val contentDescription: String
)

enum class QuickTileAction {
    TOGGLE_ON,
    TOGGLE_OFF,
    OPEN_NFC_SETTINGS,
    OPEN_APP_TO_UNLOCK_OR_SELECT,
    UNAVAILABLE
}

class HceQuickTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var localStore: LocalVaultStore

    override fun onCreate() {
        super.onCreate()
        localStore = LocalVaultStore(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTileUi()
    }

    override fun onClick() {
        super.onClick()
        tileScope.launch {
            val status = HceAidManager.getHardwareStatus(applicationContext)
            val configuredCard = HceRuntimeBus.getCachedActiveCard() ?: localStore.readConfiguredHceCard()
            val quickEnabled = localStore.isQuickHceEnabled()
            val vaultUnlocked = HceRuntimeBus.isVaultUnlocked()

            when (determineClickAction(status, configuredCard, quickEnabled, vaultUnlocked)) {
                QuickTileAction.TOGGLE_ON -> {
                    val card = configuredCard ?: return@launch
                    localStore.setQuickHceEnabled(true)
                    HceRuntimeBus.updateCachedActiveCard(card)
                    HceAidManager.syncDynamicAid(applicationContext, card.aidHex)
                    launch(Dispatchers.Main) {
                        applyPresentation(
                            evaluatePresentation(status, card, quickEnabled = true, vaultUnlocked = vaultUnlocked)
                        )
                    }
                }

                QuickTileAction.TOGGLE_OFF -> {
                    localStore.setQuickHceEnabled(false)
                    HceRuntimeBus.updateCachedActiveCard(null)
                    launch(Dispatchers.Main) {
                        applyPresentation(
                            evaluatePresentation(status, configuredCard, quickEnabled = false, vaultUnlocked = vaultUnlocked)
                        )
                    }
                }

                QuickTileAction.OPEN_NFC_SETTINGS -> {
                    launch(Dispatchers.Main) {
                        openIntentAndCollapse(Intent(Settings.ACTION_NFC_SETTINGS))
                    }
                }

                QuickTileAction.OPEN_APP_TO_UNLOCK_OR_SELECT -> {
                    val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    launch(Dispatchers.Main) {
                        if (isSecure) {
                            unlockAndRun { openIntentAndCollapse(launchIntent) }
                        } else {
                            openIntentAndCollapse(launchIntent)
                        }
                    }
                }

                QuickTileAction.UNAVAILABLE -> {
                    launch(Dispatchers.Main) {
                        applyPresentation(
                            evaluatePresentation(status, configuredCard, quickEnabled = false, vaultUnlocked = vaultUnlocked)
                        )
                    }
                }
            }
        }
    }

    private fun refreshTileUi() {
        tileScope.launch {
            val status = HceAidManager.getHardwareStatus(applicationContext)
            val configuredCard = HceRuntimeBus.getCachedActiveCard() ?: localStore.readConfiguredHceCard()
            val quickEnabled = localStore.isQuickHceEnabled()
            val vaultUnlocked = HceRuntimeBus.isVaultUnlocked()
            val presentation = evaluatePresentation(status, configuredCard, quickEnabled, vaultUnlocked)
            launch(Dispatchers.Main) {
                applyPresentation(presentation)
            }
        }
    }

    private fun applyPresentation(presentation: QuickTilePresentation) {
        val tile = qsTile ?: return
        tile.label = presentation.label
        tile.contentDescription = presentation.contentDescription
        tile.state = presentation.tileState
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_qs_nfc_shield)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = presentation.subtitle
        }
        tile.updateTile()
    }

    private fun openIntentAndCollapse(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                REQUEST_CODE_OPEN_FROM_TILE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching { startActivityAndCollapse(pendingIntent) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { startActivityAndCollapse(intent) }
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_OPEN_FROM_TILE = 2049

        fun evaluatePresentation(
            status: NfcHardwareStatus,
            configuredCard: ActiveHceCardConfig?,
            quickEnabled: Boolean,
            vaultUnlocked: Boolean
        ): QuickTilePresentation {
            if (!status.nfcSupported || !status.hceSupported) {
                return QuickTilePresentation(
                    tileState = Tile.STATE_UNAVAILABLE,
                    label = "NFC 模拟刷卡",
                    subtitle = "设备不支持 HCE",
                    contentDescription = "当前设备不支持 NFC HCE 卡模拟"
                )
            }
            if (!status.nfcEnabled) {
                return QuickTilePresentation(
                    tileState = Tile.STATE_INACTIVE,
                    label = "NFC 模拟刷卡",
                    subtitle = "NFC 未开启 · 点击去开启",
                    contentDescription = "系统 NFC 已关闭，点击打开 NFC 设置"
                )
            }
            if (configuredCard == null) {
                return QuickTilePresentation(
                    tileState = Tile.STATE_INACTIVE,
                    label = "NFC 模拟刷卡",
                    subtitle = "点击选择 HCE 门禁卡",
                    contentDescription = "尚未激活模拟门禁卡，点击打开盾匙选择卡片"
                )
            }
            if (configuredCard.requireVaultUnlocked && !vaultUnlocked) {
                return QuickTilePresentation(
                    tileState = Tile.STATE_INACTIVE,
                    label = configuredCard.cardLabel,
                    subtitle = "需先解锁保险库",
                    contentDescription = "${configuredCard.cardLabel} 需要先解锁保险库"
                )
            }
            return if (quickEnabled) {
                QuickTilePresentation(
                    tileState = Tile.STATE_ACTIVE,
                    label = configuredCard.cardLabel,
                    subtitle = "HCE 模拟中 · 贴近刷卡",
                    contentDescription = "正在模拟 ${configuredCard.cardLabel}，点击暂停"
                )
            } else {
                QuickTilePresentation(
                    tileState = Tile.STATE_INACTIVE,
                    label = configuredCard.cardLabel,
                    subtitle = "已暂停 · 点击开启模拟",
                    contentDescription = "${configuredCard.cardLabel} 已暂停，点击一键开启模拟"
                )
            }
        }

        fun determineClickAction(
            status: NfcHardwareStatus,
            configuredCard: ActiveHceCardConfig?,
            quickEnabled: Boolean,
            vaultUnlocked: Boolean
        ): QuickTileAction {
            if (!status.nfcSupported || !status.hceSupported) {
                return QuickTileAction.UNAVAILABLE
            }
            if (!status.nfcEnabled) {
                return QuickTileAction.OPEN_NFC_SETTINGS
            }
            if (configuredCard == null) {
                return QuickTileAction.OPEN_APP_TO_UNLOCK_OR_SELECT
            }
            if (configuredCard.requireVaultUnlocked && !vaultUnlocked) {
                return QuickTileAction.OPEN_APP_TO_UNLOCK_OR_SELECT
            }
            return if (quickEnabled) QuickTileAction.TOGGLE_OFF else QuickTileAction.TOGGLE_ON
        }

        fun requestTileSync(context: Context) {
            runCatching {
                val component = ComponentName(context, HceQuickTileService::class.java)
                requestListeningState(context.applicationContext, component)
            }
        }

        fun requestAddTileToSystem(context: Context, onMessage: (String) -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                if (statusBarManager != null) {
                    val component = ComponentName(context, HceQuickTileService::class.java)
                    val label = context.getString(R.string.qs_tile_nfc_label)
                    val icon = Icon.createWithResource(context, R.drawable.ic_qs_nfc_shield)
                    statusBarManager.requestAddTileService(
                        component,
                        label,
                        icon,
                        ContextCompat.getMainExecutor(context)
                    ) { resultCode ->
                        val msg = when (resultCode) {
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                                "已将「$label」添加到系统快捷设置栏"
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                                "「$label」已在您的快捷设置面板中"
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                                "未添加磁贴，您可随时从下拉菜单手动编辑添加"
                            else -> "请从屏幕顶部下拉两次，点击编辑将「$label」拖入快捷面板"
                        }
                        onMessage(msg)
                    }
                    return
                }
            }
            onMessage("请从屏幕顶部下拉两次打开快捷设置，点击编辑并将「NFC 模拟刷卡」拖入面板")
        }
    }
}
