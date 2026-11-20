package com.aistudio.nfcshield.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.BuildConfig
import com.aistudio.nfcshield.data.remote.SyncDiagnosticReport
import com.aistudio.nfcshield.data.remote.SyncProbeStatus
import com.aistudio.nfcshield.data.remote.toDiagnosticText
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultHairline
import com.aistudio.nfcshield.ui.theme.VaultHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary

/**
 * Structured Auth / App Check / network probe card used when cloud sync is refused.
 * Debug builds additionally show the active App Check debug token with a copy action.
 */
@Composable
fun SyncDiagnosticsCard(
    report: SyncDiagnosticReport?,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showRetryButton: Boolean = true
) {
    val clipboard = LocalClipboardManager.current
    var copiedReport by remember(report) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VaultSurface,
        border = BorderStroke(1.dp, VaultHairline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_diag_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "云端同步诊断",
                style = MaterialTheme.typography.titleSmall,
                color = VaultTextPrimary
            )
            Text(
                text = if (BuildConfig.DEBUG) {
                    "构建: Debug · App Check Debug Provider"
                } else {
                    "构建: Release · Play Integrity"
                },
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = VaultTextSecondary,
                modifier = Modifier.testTag("sync_diag_build")
            )
            ProbeRow(
                label = "Google 登录会话",
                ok = report?.authStatus == SyncProbeStatus.OK,
                detail = report?.authDetail.orEmpty(),
                tag = "sync_diag_auth"
            )
            ProbeRow(
                label = "App Check 设备证明",
                ok = report?.appCheckStatus == SyncProbeStatus.OK,
                detail = when {
                    report == null -> ""
                    report.appCheckStatus == SyncProbeStatus.OK -> "设备证明正常"
                    else -> report.appCheckFailure.displayHint(BuildConfig.DEBUG)
                },
                tag = "sync_diag_appcheck"
            )
            ProbeRow(
                label = "网络连接",
                ok = report?.networkAvailable != false,
                detail = if (report?.networkAvailable == false) "当前无可用网络" else "",
                tag = "sync_diag_network"
            )
            val code = report?.firestoreCode
            if (!code.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Firestore: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultTextSecondary
                    )
                    Text(
                        text = code,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultAmber
                    )
                }
            }

            // Debug builds surface the raw attestation error; release users can copy the
            // report (which includes it) for support without exposing it on screen.
            val detail = report?.appCheckDetail
            if (BuildConfig.DEBUG && !detail.isNullOrBlank()) {
                Text(
                    text = "App Check 详情: ${detail.take(240)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultTextSecondary,
                    modifier = Modifier.testTag("sync_diag_appcheck_detail")
                )
            }

            TextButton(
                onClick = {
                    val snapshot = report
                    if (snapshot != null) {
                        clipboard.setText(AnnotatedString(snapshot.toDiagnosticText()))
                        copiedReport = true
                    }
                },
                enabled = report != null,
                modifier = Modifier.testTag("sync_diag_copy_report")
            ) {
                Text(if (copiedReport) "诊断信息已复制" else "复制诊断信息")
            }

            val debugInfo = report?.debugTokenInfo
            if (BuildConfig.DEBUG && debugInfo != null) {
                DebugTokenBox(token = debugInfo.token, sourceLabel = debugInfo.source.displayName)
            }

            if (showRetryButton) {
                Button(
                    onClick = { if (!isRetrying) onRetry() },
                    enabled = !isRetrying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_diag_retry")
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("正在刷新令牌并重连…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("重新连接云端保险库", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeRow(label: String, ok: Boolean, detail: String, tag: String) {
    val dotColor = if (ok) VaultCyan else VaultRose
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = dotColor.copy(alpha = 0.16f),
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = VaultTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (ok) "正常" else "受限",
                style = MaterialTheme.typography.labelMedium,
                color = dotColor
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultTextSecondary
                )
            }
        }
    }
}

@Composable
private fun DebugTokenBox(token: String, sourceLabel: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(token) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VaultHairlineStrong),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_debug_token_box")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Debug Token 来源：$sourceLabel",
                style = MaterialTheme.typography.labelMedium,
                color = VaultAmber
            )
            Text(
                text = token,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.bodySmall,
                color = VaultTextPrimary
            )
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(token))
                    copied = true
                },
                modifier = Modifier.testTag("sync_debug_token_copy")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (copied) "已复制，请粘贴到 Firebase Console" else "复制 Debug Token")
            }
        }
    }
}

/**
 * Compact amber degradation bar shown above the wallet content when the local encrypted
 * cache is serving data while cloud sync is unavailable.
 */
@Composable
fun OfflineSyncBanner(
    report: SyncDiagnosticReport,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onShowDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VaultAmber.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, VaultAmber.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_sync_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = VaultAmber,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "云端暂不可达 · 已启用本地加密缓存",
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultTextPrimary
                )
                Text(
                    text = offlineReason(report),
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultTextSecondary
                )
            }
            TextButton(
                onClick = { if (!isRetrying) onRetry() },
                enabled = !isRetrying,
                modifier = Modifier.testTag("offline_sync_retry")
            ) {
                Text(if (isRetrying) "重试中…" else "重试")
            }
            TextButton(
                onClick = onShowDiagnostics,
                modifier = Modifier.testTag("offline_sync_details")
            ) {
                Text("诊断")
            }
        }
    }
}

@Composable
fun SyncDiagnosticsDialog(
    report: SyncDiagnosticReport?,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = {
            Text("同步诊断", color = VaultTextPrimary)
        },
        text = {
            SyncDiagnosticsCard(report = report, isRetrying = isRetrying, onRetry = onRetry)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = VaultTextPrimary)
            }
        },
        modifier = Modifier.testTag("sync_diagnostics_dialog")
    )
}

private fun offlineReason(report: SyncDiagnosticReport): String = when {
    !report.networkAvailable -> "网络不可用，联网后将自动恢复同步"
    report.appCheckStatus == SyncProbeStatus.FAILED ->
        report.appCheckFailure.displayHint(BuildConfig.DEBUG)
    report.authStatus == SyncProbeStatus.FAILED -> "登录会话待刷新"
    else -> "云端同步正在重试"
}