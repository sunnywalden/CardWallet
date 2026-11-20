package com.aistudio.nfcshield.nfc

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import androidx.core.content.ContextCompat

data class NfcHardwareStatus(
    val nfcSupported: Boolean,
    val nfcEnabled: Boolean,
    val hceSupported: Boolean,
    val registeredAids: List<String>,
    val nfcFeatureDeclared: Boolean = nfcSupported,
    val hceFSupported: Boolean = false,
    val hceSelectionMode: String = "未初始化",
    val hceDefaultService: Boolean = false,
    val deviceSummary: String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val nfcPermissionDeclared: Boolean = false,
    val nfcPermissionGranted: Boolean = false,
    val hceBindServiceDeclared: Boolean = false,
    val adapterState: Int = -1,
    val adapterStateLabel: String = "未检测到适配器",
    val lastCheckedAtMillis: Long = System.currentTimeMillis()
)

object HceAidManager {
    fun getHardwareStatus(
        context: Context,
        overrideAdapterState: Int? = null
    ): NfcHardwareStatus {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        val pm = context.packageManager
        val hasNfcFeature = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        val hasHce = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        val hasHceF = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)

        val nfcPermDeclared = runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
            pkgInfo.requestedPermissions?.contains(Manifest.permission.NFC) == true
        }.getOrDefault(false)

        val nfcPermGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NFC
        ) == PackageManager.PERMISSION_GRANTED

        val component = ComponentName(context, AccessCardHceService::class.java)
        val hceServiceDeclared = runCatching {
            val serviceInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getServiceInfo(component, 0)
            }
            serviceInfo.permission == Manifest.permission.BIND_NFC_SERVICE
        }.getOrDefault(false)

        val effectiveAdapterState = when {
            adapter == null -> -1
            overrideAdapterState != null && overrideAdapterState != -1 -> overrideAdapterState
            adapter.isEnabled -> NfcAdapter.STATE_ON
            else -> NfcAdapter.STATE_OFF
        }
        val stateLabel = when (effectiveAdapterState) {
            NfcAdapter.STATE_ON -> "已开启 (STATE_ON)"
            NfcAdapter.STATE_TURNING_ON -> "正在开启 (STATE_TURNING_ON)"
            NfcAdapter.STATE_OFF -> "已关闭 (STATE_OFF)"
            NfcAdapter.STATE_TURNING_OFF -> "正在关闭 (STATE_TURNING_OFF)"
            else -> "无硬件适配器"
        }

        var aids = emptyList<String>()
        var selectionModeLabel = if (hasHce) "等待 NFC 适配器" else "硬件不支持 HCE"
        var isDefaultService = false

        if (adapter != null && hasHce) {
            runCatching {
                val emulation = CardEmulation.getInstance(adapter)
                aids = emulation.getAidsForService(component, CardEmulation.CATEGORY_OTHER).orEmpty()
                isDefaultService = runCatching {
                    emulation.isDefaultServiceForCategory(component, CardEmulation.CATEGORY_OTHER)
                }.getOrDefault(false)
                selectionModeLabel = when (
                    runCatching { emulation.getSelectionModeForCategory(CardEmulation.CATEGORY_OTHER) }.getOrNull()
                ) {
                    CardEmulation.SELECTION_MODE_PREFER_DEFAULT -> "优先默认服务 (PREFER_DEFAULT)"
                    CardEmulation.SELECTION_MODE_ALWAYS_ASK -> "冲突时询问 (ALWAYS_ASK)"
                    CardEmulation.SELECTION_MODE_ASK_IF_CONFLICT -> "AID 冲突时询问 (ASK_IF_CONFLICT)"
                    else -> "标准路由"
                }
            }
        }

        return NfcHardwareStatus(
            nfcSupported = adapter != null,
            nfcEnabled = effectiveAdapterState == NfcAdapter.STATE_ON,
            hceSupported = hasHce,
            registeredAids = aids,
            nfcFeatureDeclared = hasNfcFeature,
            hceFSupported = hasHceF,
            hceSelectionMode = selectionModeLabel,
            hceDefaultService = isDefaultService,
            nfcPermissionDeclared = nfcPermDeclared,
            nfcPermissionGranted = nfcPermGranted,
            hceBindServiceDeclared = hceServiceDeclared,
            adapterState = effectiveAdapterState,
            adapterStateLabel = stateLabel,
            lastCheckedAtMillis = System.currentTimeMillis()
        )
    }

    fun syncDynamicAid(context: Context, aidHex: String?): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            return false
        }
        return runCatching {
            val emulation = CardEmulation.getInstance(adapter)
            val component = ComponentName(context, AccessCardHceService::class.java)
            // Register the full builtin set plus the card AID. Replacing the list with only
            // DEFAULT + card AID would silently drop the statically declared AIDs.
            emulation.registerAidsForService(
                component,
                CardEmulation.CATEGORY_OTHER,
                HceAids.registeredAidsFor(aidHex)
            )
        }.getOrDefault(false)
    }
}
