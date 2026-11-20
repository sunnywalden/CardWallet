package com.aistudio.nfcshield.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.nfc.HceRuntimeBus
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.aistudio.nfcshield.nfc.ScannedNfcTagInfo
import com.aistudio.nfcshield.ui.components.NfcRadarScanner
import com.aistudio.nfcshield.ui.components.toColor
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultAmberContainer
import com.aistudio.nfcshield.ui.theme.VaultBorder
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultCyanContainer
import com.aistudio.nfcshield.ui.theme.VaultObsidian
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NfcScanScreen(
    nfcStatus: NfcHardwareStatus,
    physicalScanRequestNonce: Int = 0,
    onOpenNfcSettings: () -> Unit,
    onSaveCard: (
        label: String,
        category: CardCategory,
        accentColor: CardAccentColor,
        primaryTech: NfcTechType,
        hceSupported: Boolean,
        activateHceNow: Boolean,
        secrets: CardSecretsPayload,
        onDone: (Result<String>) -> Unit
    ) -> Unit,
    onNavigateBackToWallet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var selectedTab by remember(nfcStatus.nfcSupported) {
        mutableIntStateOf(if (nfcStatus.nfcSupported || physicalScanRequestNonce > 0) 0 else 1)
    } // 0 = Hardware Scan, 1 = Manual HCE
    var scannedTag by remember { mutableStateOf<ScannedNfcTagInfo?>(null) }

    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CardCategory.RESIDENCE) }
    var accentColor by remember { mutableStateOf(CardAccentColor.CYAN) }
    var uidHexInput by remember { mutableStateOf("") }
    var aidHexInput by remember { mutableStateOf(NfcTagParser.DEFAULT_HCE_AID) }
    var apduResponseInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var activateHceNow by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(physicalScanRequestNonce) {
        if (physicalScanRequestNonce > 0) {
            selectedTab = 0
            scannedTag = null
            HceRuntimeBus.consumeScannedTagInfo()
            label = ""
            uidHexInput = ""
            aidHexInput = NfcTagParser.DEFAULT_HCE_AID
            apduResponseInput = ""
            notesInput = ""
            formError = null
        }
    }

    LaunchedEffect(Unit) {
        HceRuntimeBus.pendingScannedTagInfo.collect { parsed ->
            if (parsed != null) {
                HceRuntimeBus.consumeScannedTagInfo()
                scannedTag = parsed
                selectedTab = 0
                uidHexInput = parsed.uidHex
                if (parsed.aidHex.isNotBlank()) {
                    aidHexInput = parsed.aidHex
                }
                if (notesInput.isBlank() && parsed.ndefSummary.isNotBlank()) {
                    notesInput = "NDEF: ${parsed.ndefSummary}"
                }
                activateHceNow = parsed.hceSupported
                formError = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val isManualMode = selectedTab == 1
    val effectivePrimaryTech = if (isManualMode) NfcTechType.ISO_DEP else (scannedTag?.primaryTech ?: NfcTechType.ISO_DEP)
    val effectiveHceSupported = if (isManualMode) true else (scannedTag?.hceSupported == true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultObsidian)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.headlineMedium,
                color = VaultTextPrimary
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = VaultCyanContainer,
                        activeContentColor = VaultCyan,
                        inactiveContainerColor = VaultSurface,
                        inactiveContentColor = VaultTextSecondary
                    ),
                    modifier = Modifier.testTag("scan_mode_hardware_tab")
                ) {
                    Text(stringResource(R.string.scan_mode_hardware))
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = VaultCyanContainer,
                        activeContentColor = VaultCyan,
                        inactiveContainerColor = VaultSurface,
                        inactiveContentColor = VaultTextSecondary
                    ),
                    modifier = Modifier.testTag("scan_mode_manual_tab")
                ) {
                    Text(stringResource(R.string.scan_mode_manual))
                }
            }

            if (selectedTab == 0) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = VaultSurfaceElevated,
                    border = BorderStroke(1.dp, if (scannedTag != null) VaultCyan else VaultBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when {
                            !nfcStatus.nfcSupported -> {
                                Text(
                                    text = "未检测到本机 NFC 硬件。如果您正在浏览器 Preview（云端模拟器）中预览，浏览器无法透传手机背部的物理 NFC 信号；请导出 APK 安装到三星等真机后即可贴卡读取。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VaultAmber,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = { selectedTab = 1 },
                                    border = BorderStroke(1.dp, VaultCyan),
                                    modifier = Modifier.testTag("switch_to_manual_hce_button")
                                ) {
                                    Text("切换至「手动录入 HCE 卡」", color = VaultCyan)
                                }
                            }

                            !nfcStatus.nfcEnabled -> {
                                Text(
                                    text = stringResource(R.string.scan_nfc_disabled),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VaultAmber,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = onOpenNfcSettings,
                                    border = BorderStroke(1.dp, VaultAmber)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null,
                                        tint = VaultAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.scan_open_nfc_settings), color = VaultAmber)
                                }
                            }

                            else -> {
                                NfcRadarScanner(
                                    isScanning = scannedTag == null,
                                    hasDetected = scannedTag != null
                                )
                                Text(
                                    text = if (scannedTag == null) {
                                        stringResource(R.string.scan_waiting_hint)
                                    } else {
                                        stringResource(R.string.scan_success_badge)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (scannedTag == null) VaultTextPrimary else VaultCyan,
                                    textAlign = TextAlign.Center
                                )
                                if (scannedTag != null) {
                                    val tagInfo = scannedTag!!
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = VaultObsidian,
                                        border = BorderStroke(1.dp, VaultBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "UID: ${tagInfo.uidHex.ifEmpty { "未读取" }}",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = VaultCyan
                                            )
                                            Text(
                                                text = "主协议: ${tagInfo.primaryTech.displayName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = VaultTextPrimary
                                            )
                                            if (tagInfo.atqaHex.isNotEmpty() || tagInfo.sakHex.isNotEmpty()) {
                                                Text(
                                                    text = "ATQA: ${tagInfo.atqaHex}   SAK: ${tagInfo.sakHex}",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = VaultTextSecondary
                                                )
                                            }
                                            if (tagInfo.mifareSummary.isNotEmpty()) {
                                                Text(
                                                    text = tagInfo.mifareSummary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = VaultTextSecondary
                                                )
                                            }
                                            if (tagInfo.ndefSummary.isNotEmpty()) {
                                                Text(
                                                    text = "NDEF: ${tagInfo.ndefSummary}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = VaultTextSecondary
                                                )
                                            }
                                        }
                                    }

                                    if (!tagInfo.hceSupported) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = VaultAmberContainer,
                                            border = BorderStroke(1.dp, VaultAmber.copy(alpha = 0.45f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Info,
                                                    contentDescription = null,
                                                    tint = VaultAmber,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "此物理卡依赖固定硬件 UID 或加密扇区（${tagInfo.primaryTech.displayName}）。Android HCE 每次刷卡会随机化 UID，因此本卡将作为加密凭证备份保存（仅读取），如需 HCE 模拟刷卡请使用支持 ISO-DEP 动态 AID 的门禁系统。",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = VaultTextPrimary
                                                )
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { scannedTag = null },
                                        border = BorderStroke(1.dp, VaultBorder),
                                        modifier = Modifier.testTag("rescan_nfc_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            tint = VaultCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Text("重新贴卡扫描", color = VaultCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Encrypted Card Form
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = VaultCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "加密凭证信息（AES-256-GCM 端到端保护）",
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultTextPrimary
                        )
                    }

                    OutlinedTextField(
                        value = label,
                        onValueChange = { if (it.length <= 40) label = it },
                        label = { Text("门禁卡名称（必填，如：东门门禁）") },
                        singleLine = true,
                        colors = vaultTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("card_label_input")
                    )

                    Text("场所分类", style = MaterialTheme.typography.labelLarge, color = VaultTextSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CardCategory.entries.forEach { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = { category = item },
                                label = { Text(item.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultCyanContainer,
                                    selectedLabelColor = VaultCyan,
                                    containerColor = VaultSurfaceElevated,
                                    labelColor = VaultTextSecondary
                                )
                            )
                        }
                    }

                    Text("卡片主题色", style = MaterialTheme.typography.labelLarge, color = VaultTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardAccentColor.entries.forEach { accent ->
                            val c = accent.toColor()
                            val selected = accentColor == accent
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(c.copy(alpha = 0.22f))
                                    .border(if (selected) 2.5.dp else 1.dp, if (selected) c else VaultBorder, CircleShape)
                                    .clickable { accentColor = accent },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = accent.displayName,
                                        tint = c,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uidHexInput,
                        onValueChange = { uidHexInput = it },
                        label = { Text("卡片 UID / 卡号（十六进制，可选）") },
                        placeholder = { Text("例如 04:A1:B2:C3") },
                        singleLine = true,
                        colors = vaultTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("card_uid_input")
                    )

                    if (effectiveHceSupported || isManualMode) {
                        OutlinedTextField(
                            value = aidHexInput,
                            onValueChange = { aidHexInput = it },
                            label = { Text("HCE 应用标识 AID（5~16 字节 Hex）") },
                            placeholder = { Text(NfcTagParser.DEFAULT_HCE_AID) },
                            singleLine = true,
                            colors = vaultTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("card_aid_input")
                        )

                        OutlinedTextField(
                            value = apduResponseInput,
                            onValueChange = { apduResponseInput = it },
                            label = { Text("APDU 响应令牌载荷（十六进制，可选）") },
                            placeholder = { Text("例如 4B45593031（将附加 9000 返回）") },
                            singleLine = true,
                            colors = vaultTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("card_apdu_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "保存后立即设为当前 HCE 模拟卡",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VaultTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = activateHceNow,
                                onCheckedChange = { activateHceNow = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VaultObsidian,
                                    checkedTrackColor = VaultCyan
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it.take(MAX_NOTES_LENGTH) },
                        label = { Text("加密门禁备注（物业楼栋、权限说明等）") },
                        supportingText = {
                            Text("${notesInput.length} / $MAX_NOTES_LENGTH")
                        },
                        minLines = 2,
                        maxLines = 4,
                        colors = vaultTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("card_notes_input")
                    )

                    if (formError != null) {
                        Text(
                            text = formError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultRose
                        )
                    }

                    Button(
                        onClick = {
                            val cleanLabel = label.trim()
                            if (cleanLabel.isEmpty()) {
                                formError = "请输入门禁卡名称"
                                return@Button
                            }
                            if (!isManualMode && scannedTag == null && uidHexInput.isBlank()) {
                                formError = "请先贴近物理 NFC 门禁卡扫描，或切换至「手动录入 HCE 卡」"
                                return@Button
                            }
                            val cleanAid = NfcTagParser.normalizeHex(aidHexInput)
                            if (effectiveHceSupported && cleanAid.isNotEmpty() && !NfcTagParser.isValidAidHex(cleanAid)) {
                                formError = "AID 必须为 10~32 位偶数十六进制字符（5~16 字节）"
                                return@Button
                            }
                            val cleanApdu = NfcTagParser.normalizeHex(apduResponseInput)
                            if (cleanApdu.isNotEmpty() && cleanApdu.length % 2 != 0) {
                                formError = "APDU 响应十六进制长度必须为偶数"
                                return@Button
                            }

                            formError = null
                            isSaving = true
                            val tagSnapshot = scannedTag
                            val secrets = CardSecretsPayload(
                                uidHex = uidHexInput.trim(),
                                atqaHex = tagSnapshot?.atqaHex.orEmpty(),
                                sakHex = tagSnapshot?.sakHex.orEmpty(),
                                historicalBytesHex = tagSnapshot?.historicalBytesHex.orEmpty(),
                                aidHex = if (effectiveHceSupported) cleanAid.ifEmpty { NfcTagParser.DEFAULT_HCE_AID } else "",
                                apduResponseHex = cleanApdu,
                                ndefSummary = tagSnapshot?.ndefSummary.orEmpty(),
                                techList = tagSnapshot?.techList ?: listOf(effectivePrimaryTech.name),
                                notes = notesInput.trim()
                            )
                            onSaveCard(
                                cleanLabel,
                                category,
                                accentColor,
                                effectivePrimaryTech,
                                effectiveHceSupported,
                                activateHceNow && effectiveHceSupported,
                                secrets
                            ) { result ->
                                isSaving = false
                                if (result.isSuccess) {
                                    onNavigateBackToWallet()
                                } else {
                                    formError = result.exceptionOrNull()?.localizedMessage ?: "保存失败，请重试"
                                }
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultCyan,
                            contentColor = VaultObsidian
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_encrypted_card_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = VaultObsidian
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.scan_save_encrypted),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun vaultTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VaultCyan,
    unfocusedBorderColor = VaultBorder,
    focusedLabelColor = VaultCyan,
    unfocusedLabelColor = VaultTextSecondary,
    focusedTextColor = VaultTextPrimary,
    unfocusedTextColor = VaultTextPrimary,
    focusedContainerColor = VaultSurfaceElevated,
    unfocusedContainerColor = VaultSurfaceElevated
)

private const val MAX_NOTES_LENGTH = 1500
