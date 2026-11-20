package com.aistudio.nfcshield.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.data.model.HceTapEvent
import com.aistudio.nfcshield.nfc.HceRuntimeBus
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.aistudio.nfcshield.nfc.ScannedNfcTagInfo
import com.aistudio.nfcshield.ui.components.NfcPermissionMonitorCard
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultAmberContainer
import com.aistudio.nfcshield.ui.theme.VaultBorder
import com.aistudio.nfcshield.ui.theme.VaultBorderSubtle
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultCyanContainer
import com.aistudio.nfcshield.ui.theme.VaultErrorContainer
import com.aistudio.nfcshield.ui.theme.VaultIceBlue
import com.aistudio.nfcshield.ui.theme.VaultIceBlueContainer
import com.aistudio.nfcshield.ui.theme.VaultObsidian
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextMuted
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NfcDebugScreen(
    nfcStatus: NfcHardwareStatus,
    readerModeEnabled: Boolean,
    tagLogs: List<ScannedNfcTagInfo>,
    hceEvents: List<HceTapEvent>,
    onToggleReaderMode: (Boolean) -> Unit,
    onRefreshStatus: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenAppPermissionSettings: () -> Unit,
    onClearLogs: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        onRefreshStatus()
        while (true) {
            delay(2000L)
            onRefreshStatus()
        }
    }

    LaunchedEffect(tagLogs.size) {
        if (tagLogs.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultObsidian)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 660.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = VaultTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.debug_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultTextSecondary
                    )
                }
                IconButton(
                    onClick = {
                        onRefreshStatus()
                        onShowMessage("已刷新设备 NFC 与 HCE 状态")
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("debug_refresh_status_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新 NFC 状态",
                        tint = VaultCyan
                    )
                }
            }

            // Real-time NFC Permission & Adapter State Monitor
            NfcPermissionMonitorCard(
                nfcStatus = nfcStatus,
                onOpenNfcSettings = onOpenNfcSettings,
                onOpenAppPermissionSettings = onOpenAppPermissionSettings
            )

            // Hardware & Card Emulation Status Matrix
            NfcDeviceDiagnosticsCard(
                nfcStatus = nfcStatus,
                readerModeEnabled = readerModeEnabled,
                onToggleReaderMode = onToggleReaderMode,
                onOpenNfcSettings = onOpenNfcSettings
            )

            // Action toolbar for logs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val report = NfcTagParser.formatDebugReport(nfcStatus, tagLogs)
                        copyTextToClipboard(context, "NFC 调试报告", report)
                        onShowMessage("已复制 NFC 状态与技术栈报告到剪贴板")
                    },
                    border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("debug_copy_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.debug_copy_report), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        onClearLogs()
                        onShowMessage("已清空调试日志")
                    },
                    enabled = tagLogs.isNotEmpty() || hceEvents.isNotEmpty(),
                    border = BorderStroke(1.dp, VaultBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultTextSecondary),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("debug_clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.debug_clear_logs))
                }
            }

            // Scanned Tag Tech Stack Console
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "标签技术栈日志 (${tagLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = VaultTextPrimary
                )
                Text(
                    text = if (readerModeEnabled && nfcStatus.nfcEnabled) "监听中 · 请贴卡" else "被动/待机",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (readerModeEnabled && nfcStatus.nfcEnabled) VaultCyan else VaultTextMuted
                )
            }

            if (tagLogs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = VaultSurface,
                    border = BorderStroke(1.dp, VaultBorder),
                    modifier = Modifier.testTag("debug_empty_state_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(VaultCyanContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = VaultCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "等待捕获 NFC 标签",
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultTextPrimary
                        )
                        Text(
                            text = stringResource(R.string.debug_empty_logs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultTextSecondary
                        )
                    }
                }
            } else {
                tagLogs.forEachIndexed { index, log ->
                    TagDebugLogCard(
                        index = index,
                        totalCount = tagLogs.size,
                        log = log,
                        timeFormatted = timeFormatter.format(Date(log.timestampMillis)),
                        initiallyExpanded = index == 0
                    )
                }
            }

            // HCE APDU Live Stream
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SimCard,
                            contentDescription = null,
                            tint = VaultIceBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "HCE 卡模拟 APDU 通信日志 (${hceEvents.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultTextPrimary
                        )
                    }
                    if (hceEvents.isEmpty()) {
                        Text(
                            text = "尚无外部读卡器触发 APDU 交互。如需测试模拟刷卡，可关闭上方「主动读卡监听」开关后，将手机贴近支持 ISO-DEP 的读卡器。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultTextSecondary
                        )
                    } else {
                        hceEvents.take(8).forEach { ev ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = VaultSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (ev.accepted) VaultCyan.copy(alpha = 0.35f) else VaultRose.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${ev.cardLabel} · ${timeFormatter.format(Date(ev.timestampMillis))}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = VaultTextPrimary
                                        )
                                        Text(
                                            text = "SW=${ev.statusWordHex}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (ev.accepted) VaultCyan else VaultRose
                                        )
                                    }
                                    Text(
                                        text = "AID: ${ev.aidHex.ifEmpty { "-" }} | CMD: ${ev.commandHex.ifEmpty { "-" }}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VaultTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NfcDeviceDiagnosticsCard(
    nfcStatus: NfcHardwareStatus,
    readerModeEnabled: Boolean,
    onToggleReaderMode: (Boolean) -> Unit,
    onOpenNfcSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = VaultSurfaceElevated,
        border = BorderStroke(1.dp, VaultBorder),
        modifier = Modifier.testTag("debug_hardware_status_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = null,
                    tint = VaultCyan
                )
                Column {
                    Text(
                        text = "设备 NFC 与 HCE 能力诊断",
                        style = MaterialTheme.typography.titleMedium,
                        color = VaultTextPrimary
                    )
                    Text(
                        text = nfcStatus.deviceSummary,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultTextSecondary
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusDiagnosticBadge(
                    title = "NFC 硬件",
                    value = if (nfcStatus.nfcSupported) "支持" else "未检测到",
                    isHealthy = nfcStatus.nfcSupported,
                    isWarning = false
                )
                StatusDiagnosticBadge(
                    title = "NFC 开关",
                    value = when {
                        !nfcStatus.nfcSupported -> "不可用"
                        nfcStatus.nfcEnabled -> "已开启"
                        else -> "已关闭"
                    },
                    isHealthy = nfcStatus.nfcSupported && nfcStatus.nfcEnabled,
                    isWarning = nfcStatus.nfcSupported && !nfcStatus.nfcEnabled
                )
                StatusDiagnosticBadge(
                    title = "HCE 卡模拟",
                    value = if (nfcStatus.hceSupported) "支持 (ISO-DEP)" else "不支持",
                    isHealthy = nfcStatus.hceSupported,
                    isWarning = false
                )
                StatusDiagnosticBadge(
                    title = "HCE-F (FeliCa)",
                    value = if (nfcStatus.hceFSupported) "支持" else "不支持",
                    isHealthy = nfcStatus.hceFSupported,
                    isWarning = !nfcStatus.hceFSupported && nfcStatus.nfcSupported
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "FEATURE_NFC 声明: ${if (nfcStatus.nfcFeatureDeclared) "true" else "false"} · 默认 HCE 服务: ${if (nfcStatus.hceDefaultService) "是" else "否"}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultTextSecondary
                    )
                    Text(
                        text = "HCE 路由策略: ${nfcStatus.hceSelectionMode}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultTextSecondary
                    )
                    Text(
                        text = "已注册 AID: ${nfcStatus.registeredAids.ifEmpty { listOf("无") }.joinToString(", ")}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (nfcStatus.registeredAids.isNotEmpty()) VaultCyan else VaultTextMuted
                    )
                }
            }

            if (nfcStatus.nfcSupported && !nfcStatus.nfcEnabled) {
                OutlinedButton(
                    onClick = onOpenNfcSettings,
                    border = BorderStroke(1.dp, VaultAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("debug_open_nfc_settings_button")
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

            if (!nfcStatus.nfcSupported) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VaultAmberContainer,
                    border = BorderStroke(1.dp, VaultAmber.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "当前运行环境（如云端浏览器 Preview 模拟器）未配备物理 NFC 硬件；请导出 APK 安装到真机，即可在此处看到实时扫描出的技术栈（如 IsoDep、NfcA、NfcF、MifareClassic）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultAmber,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider(color = VaultBorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "主动读卡监听 (ReaderMode)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VaultTextPrimary
                    )
                    Text(
                        text = if (readerModeEnabled) {
                            "开启时优先捕获贴卡并打印技术栈（NFC-A/B/F/V）"
                        } else {
                            "关闭时释放读卡器锁，允许外部刷卡机测试本机 HCE 模拟"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultTextSecondary
                    )
                }
                Switch(
                    checked = readerModeEnabled,
                    onCheckedChange = onToggleReaderMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VaultObsidian,
                        checkedTrackColor = VaultCyan
                    ),
                    modifier = Modifier.testTag("debug_reader_mode_switch")
                )
            }
        }
    }
}

@Composable
private fun StatusDiagnosticBadge(
    title: String,
    value: String,
    isHealthy: Boolean,
    isWarning: Boolean
) {
    val accent: Color
    val container: Color
    val icon: ImageVector
    when {
        isHealthy -> {
            accent = VaultCyan
            container = VaultCyanContainer
            icon = Icons.Filled.CheckCircle
        }
        isWarning -> {
            accent = VaultAmber
            container = VaultAmberContainer
            icon = Icons.Filled.WarningAmber
        }
        else -> {
            accent = VaultRose
            container = VaultErrorContainer
            icon = Icons.Filled.ErrorOutline
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultTextSecondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagDebugLogCard(
    index: Int,
    totalCount: Int,
    log: ScannedNfcTagInfo,
    timeFormatted: String,
    initiallyExpanded: Boolean
) {
    var expanded by remember(log.timestampMillis) { mutableStateOf(initiallyExpanded) }
    val recordNumber = totalCount - index

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = VaultSurface,
        border = BorderStroke(1.dp, if (index == 0) VaultCyan.copy(alpha = 0.6f) else VaultBorder),
        modifier = Modifier.testTag("debug_tag_log_card_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#$recordNumber · ${log.primaryTech.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = VaultTextPrimary
                    )
                    Text(
                        text = "$timeFormatted · 来源: ${log.captureSource}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultTextSecondary
                    )
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = if (expanded) "收起" else "展开详情",
                        color = VaultCyan
                    )
                }
            }

            // UID & Short Tech Chips
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VaultSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "UID (${log.uidLengthBytes} B): ${log.uidHex.ifEmpty { "空" }}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultCyan
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        log.techList.forEach { tech ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VaultIceBlueContainer,
                                border = BorderStroke(1.dp, VaultIceBlue.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = tech,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultIceBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Raw Tech List
                    if (log.rawTechList.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VaultObsidian,
                            border = BorderStroke(1.dp, VaultBorderSubtle)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Tag.getTechList() 原始类名栈:",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultTextMuted
                                )
                                log.rawTechList.forEachIndexed { idx, rawClass ->
                                    Text(
                                        text = "[$idx] $rawClass",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VaultTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Per-tech detailed parameters
                    log.techEntries.forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VaultSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (entry.probeError != null) VaultRose.copy(alpha = 0.6f) else VaultBorderSubtle
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = entry.shortName,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VaultCyan
                                )
                                if (entry.details.isEmpty()) {
                                    Text(
                                        text = "无附加元数据",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VaultTextMuted
                                    )
                                } else {
                                    entry.details.forEach { (k, v) ->
                                        Text(
                                            text = "$k: $v",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VaultTextPrimary
                                        )
                                    }
                                }
                                if (entry.probeError != null) {
                                    Text(
                                        text = "探测异常: ${entry.probeError}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VaultRose
                                    )
                                }
                            }
                        }
                    }

                    if (log.ndefSummary.isNotBlank()) {
                        Text(
                            text = "NDEF 内容: ${log.ndefSummary}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultIceBlue
                        )
                    }
                    if (log.mifareSummary.isNotBlank()) {
                        Text(
                            text = "MIFARE 摘要: ${log.mifareSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VaultAmber
                        )
                    }
                    if (log.diagnosticSummary.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VaultCyanContainer,
                            border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "排查分析：${log.diagnosticSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VaultTextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
