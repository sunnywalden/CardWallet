package com.example.ui.components

import android.nfc.NfcAdapter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.nfc.NfcHardwareStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VaultAmber
import com.example.ui.theme.VaultAmberContainer
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultCyan
import com.example.ui.theme.VaultCyanContainer
import com.example.ui.theme.VaultErrorContainer
import com.example.ui.theme.VaultObsidian
import com.example.ui.theme.VaultRose
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NfcPermissionMonitorCard(
    nfcStatus: NfcHardwareStatus,
    onOpenNfcSettings: () -> Unit,
    onOpenAppPermissionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val pulse = rememberInfiniteTransition(label = "perm_monitor_pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val needsPermissionFix = !nfcStatus.nfcPermissionDeclared || !nfcStatus.nfcPermissionGranted
    val needsSystemToggle = nfcStatus.nfcSupported && !nfcStatus.nfcEnabled
    val isTurning = nfcStatus.adapterState == NfcAdapter.STATE_TURNING_ON ||
        nfcStatus.adapterState == NfcAdapter.STATE_TURNING_OFF

    val borderColor by animateColorAsState(
        targetValue = when {
            needsPermissionFix -> VaultRose.copy(alpha = 0.7f)
            needsSystemToggle -> VaultAmber.copy(alpha = 0.7f)
            nfcStatus.nfcSupported && nfcStatus.nfcEnabled -> VaultCyan.copy(alpha = 0.5f)
            else -> VaultBorder
        },
        label = "perm_border"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = VaultSurfaceElevated,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("debug_permission_monitor_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (needsPermissionFix) VaultRose else VaultCyan
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.debug_permission_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultTextPrimary
                        )
                        Text(
                            text = "上次检测: ${timeFormatter.format(Date(nfcStatus.lastCheckedAtMillis))}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = VaultCyanContainer,
                    border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(dotAlpha)
                                .clip(CircleShape)
                                .background(VaultCyan)
                        )
                        Text(
                            text = "实时监测",
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Permission & Adapter state rows
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermissionMonitorRow(
                        label = "Manifest 权限声明 (NFC)",
                        statusText = if (nfcStatus.nfcPermissionDeclared) "已声明 android.permission.NFC" else "缺失声明",
                        healthy = nfcStatus.nfcPermissionDeclared,
                        warning = false
                    )
                    PermissionMonitorRow(
                        label = "运行时权限状态 (checkSelfPermission)",
                        statusText = if (nfcStatus.nfcPermissionGranted) "已授权 (PERMISSION_GRANTED)" else "未授权 / 被系统限制",
                        healthy = nfcStatus.nfcPermissionGranted,
                        warning = false
                    )
                    PermissionMonitorRow(
                        label = "HCE 服务权限 (BIND_NFC_SERVICE)",
                        statusText = if (nfcStatus.hceBindServiceDeclared) "服务权限已就绪" else "未绑定服务权限",
                        healthy = nfcStatus.hceBindServiceDeclared,
                        warning = !nfcStatus.hceBindServiceDeclared
                    )
                    PermissionMonitorRow(
                        label = "系统 NFC 开关实时状态",
                        statusText = nfcStatus.adapterStateLabel,
                        healthy = nfcStatus.nfcSupported && nfcStatus.nfcEnabled,
                        warning = nfcStatus.nfcSupported && (!nfcStatus.nfcEnabled || isTurning)
                    )
                }
            }

            // Contextual Guidance
            when {
                needsPermissionFix -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VaultErrorContainer,
                        border = BorderStroke(1.dp, VaultRose.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "检测到 NFC 权限未授予或被系统安全策略拦截。请前往「应用权限设置」开启 NFC 相关权限，返回本页面后将自动刷新生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = VaultRose,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                needsSystemToggle -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VaultAmberContainer,
                        border = BorderStroke(1.dp, VaultAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "NFC 权限已声明且就绪，但系统 NFC 开关当前处于关闭状态。请点击下方按钮跳转系统设置开启 NFC，返回后状态会自动实时更新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = VaultAmber,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                nfcStatus.nfcSupported && nfcStatus.nfcEnabled -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VaultCyanContainer,
                        border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = VaultCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Android NFC 权限（普通安装时权限）与系统 NFC 适配器均处于正常开启状态，可随时贴卡调试或进行 HCE 模拟。",
                                style = MaterialTheme.typography.bodySmall,
                                color = VaultTextPrimary
                            )
                        }
                    }
                }
            }

            // Direct System Settings Jump Actions
            if (needsSystemToggle || needsPermissionFix) {
                Button(
                    onClick = if (needsPermissionFix) onOpenAppPermissionSettings else onOpenNfcSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (needsPermissionFix) VaultRose else VaultAmber,
                        contentColor = VaultObsidian
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("debug_fix_nfc_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (needsPermissionFix) "去应用设置开启权限" else "跳转系统 NFC 设置开启",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenNfcSettings,
                    border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("debug_jump_nfc_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = VaultCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("系统 NFC 设置", color = VaultCyan)
                }

                OutlinedButton(
                    onClick = onOpenAppPermissionSettings,
                    border = BorderStroke(1.dp, VaultBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("debug_jump_app_permissions_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AppSettingsAlt,
                        contentDescription = null,
                        tint = VaultTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.debug_open_app_permissions), color = VaultTextPrimary)
                }
            }
        }
    }
}

@Composable
private fun PermissionMonitorRow(
    label: String,
    statusText: String,
    healthy: Boolean,
    warning: Boolean
) {
    val color: Color = when {
        healthy -> VaultCyan
        warning -> VaultAmber
        else -> VaultRose
    }
    val icon = when {
        healthy -> Icons.Filled.CheckCircle
        warning -> Icons.Filled.WarningAmber
        else -> Icons.Filled.ErrorOutline
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VaultTextSecondary,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = statusText,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}
