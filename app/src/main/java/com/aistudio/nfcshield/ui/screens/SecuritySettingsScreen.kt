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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.auth.BiometricHelper
import com.aistudio.nfcshield.data.local.LocalSecurityPreferences
import com.aistudio.nfcshield.data.model.BiometricSealMode
import com.aistudio.nfcshield.data.model.HceTapEvent
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.VaultBorderSubtle
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultHairline
import com.aistudio.nfcshield.ui.theme.VaultHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultObsidian
import com.aistudio.nfcshield.ui.theme.VaultOnPrimaryCta
import com.aistudio.nfcshield.ui.theme.VaultPrimaryCta
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextMuted
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecuritySettingsScreen(
    userEmail: String?,
    prefs: LocalSecurityPreferences,
    biometricSealMode: BiometricSealMode? = null,
    nfcStatus: NfcHardwareStatus,
    recentEvents: List<HceTapEvent>,
    onToggleBiometric: (Boolean) -> Unit,
    onChangeAutoLockMinutes: (Int) -> Unit,
    onToggleRequireUnlockForHce: (Boolean) -> Unit,
    onChangePin: (currentPin: String, newPin: String, onDone: (Result<Unit>) -> Unit) -> Unit,
    onLockVaultNow: () -> Unit,
    onSignOut: () -> Unit,
    onOpenNfcDebug: () -> Unit = {},
    onAddQuickTile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bioHardwareReady = remember(context) {
        BiometricHelper.isStrongBiometricAvailable(context) ||
            BiometricHelper.isDeviceCredentialBiometricAvailable(context)
    }
    var showChangePinDialog by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(R.string.security_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onBackground
            )

            // Account & Zero-Knowledge Encryption Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, VaultHairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VaultCyan.copy(alpha = 0.12f))
                                .border(1.dp, VaultCyan.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = VaultCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = userEmail ?: "Google 已认证用户",
                                style = MaterialTheme.typography.titleMedium,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = "零知识端到端密文同步已启用",
                                style = MaterialTheme.typography.bodySmall,
                                color = VaultCyan
                            )
                        }
                    }
                    Text(
                        text = "AES-256-GCM · PBKDF2-HMAC-SHA256 (120,000 次) · Keystore 硬件封装",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultTextMuted
                    )
                }
            }

            // Section 1: Security & Auto-Lock
            SettingsSectionLabel("访问控制与锁屏")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultHairline)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Biometric Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            SettingsIconBadge(icon = Icons.Filled.Fingerprint, tint = VaultTextPrimary)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("生物识别快速解锁", style = MaterialTheme.typography.bodyLarge, color = VaultTextPrimary)
                                Text(
                                    text = when (biometricSealMode) {
                                        BiometricSealMode.RSA_CRYPTO_OBJECT ->
                                            "硬件私钥 · 每次解锁需生物识别认证"
                                        BiometricSealMode.RSA_DEVICE_CREDENTIAL ->
                                            "硬件私钥 · 支持生物或锁屏密码认证"
                                        BiometricSealMode.AES_KEYSTORE ->
                                            "旧版缓存模式，输入一次 PIN 即可升级"
                                        null -> if (!bioHardwareReady) {
                                            "该设备不支持安全生物识别，仅 PIN 解锁"
                                        } else {
                                            "解锁后将自动启用 Keystore 硬件认证"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VaultTextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = prefs.biometricEnabled && bioHardwareReady,
                            onCheckedChange = onToggleBiometric,
                            enabled = bioHardwareReady,
                            colors = vaultSwitchColors(),
                            modifier = Modifier.testTag("settings_biometric_switch")
                        )
                    }

                    HorizontalDivider(color = VaultBorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))

                    // HCE Unlock Requirement Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            SettingsIconBadge(icon = Icons.Filled.Nfc, tint = VaultTextPrimary)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("仅解锁时允许 HCE 刷卡", style = MaterialTheme.typography.bodyLarge, color = VaultTextPrimary)
                                Text(
                                    text = "未解锁保险库前将拒绝外部读卡器响应",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VaultTextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = prefs.requireVaultUnlockedForHce,
                            onCheckedChange = onToggleRequireUnlockForHce,
                            colors = vaultSwitchColors(),
                            modifier = Modifier.testTag("settings_hce_lock_switch")
                        )
                    }

                    HorizontalDivider(color = VaultBorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))

                    // Immediate Background Auto-Lock Toggle
                    val immediateAutoLock = prefs.autoLockMinutes == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Filled.Lock,
                                tint = if (immediateAutoLock) VaultCyan else VaultTextPrimary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.security_auto_lock_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VaultTextPrimary
                                )
                                Text(
                                    text = stringResource(
                                        if (immediateAutoLock) {
                                            R.string.security_auto_lock_desc_on
                                        } else {
                                            R.string.security_auto_lock_desc_off
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (immediateAutoLock) VaultCyan else VaultTextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = immediateAutoLock,
                            onCheckedChange = { enabled ->
                                onChangeAutoLockMinutes(if (enabled) 0 else 5)
                            },
                            colors = vaultSwitchColors(),
                            modifier = Modifier.testTag("settings_auto_lock_immediate_switch")
                        )
                    }

                    HorizontalDivider(color = VaultBorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))

                    // Auto-Lock Delay Chips
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "后台自动锁定延迟",
                            style = MaterialTheme.typography.labelLarge,
                            color = VaultTextSecondary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0 to "切后台立即锁定",
                                1 to "1 分钟",
                                5 to "5 分钟",
                                15 to "15 分钟",
                                -1 to "仅手动锁定"
                            ).forEach { (mins, title) ->
                                val selected = prefs.autoLockMinutes == mins
                                FilterChip(
                                    selected = selected,
                                    onClick = { onChangeAutoLockMinutes(mins) },
                                    label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VaultPrimaryCta,
                                        selectedLabelColor = VaultOnPrimaryCta,
                                        containerColor = VaultSurfaceElevated,
                                        labelColor = VaultTextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = VaultHairline,
                                        selectedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier.testTag("auto_lock_chip_$mins")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = VaultBorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))

                    // Change PIN Row
                    SettingsActionRow(
                        icon = Icons.Filled.Key,
                        title = "修改 6 位备用 PIN 码",
                        subtitle = "用于跨设备云端密钥恢复与备用解锁",
                        onClick = { showChangePinDialog = true },
                        testTag = "change_pin_button"
                    )
                }
            }

            // Section 2: NFC System Integration & Diagnostics (Nested Debug Console!)
            SettingsSectionLabel("NFC 与硬件诊断")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultHairline)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsActionRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.qs_tile_add_button),
                        subtitle = "无需打开应用即可在系统下拉面板一键启停模拟",
                        onClick = onAddQuickTile,
                        testTag = "settings_add_quick_tile_button"
                    )

                    HorizontalDivider(color = VaultBorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsActionRow(
                        icon = Icons.Filled.BugReport,
                        iconTint = VaultCyan,
                        title = "NFC 硬件与标签技术栈调试台",
                        subtitle = buildString {
                            append(if (nfcStatus.nfcSupported) "NFC 支持" else "无 NFC 硬件")
                            append(" · ")
                            append(if (nfcStatus.hceSupported) "HCE 就绪" else "无 HCE")
                            if (recentEvents.isNotEmpty()) {
                                append(" · ${recentEvents.size} 条 APDU 记录")
                            }
                        },
                        onClick = onOpenNfcDebug,
                        testTag = "settings_open_debug_button"
                    )
                }
            }

            // Primary Security Session Actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onLockVaultNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultSurfaceElevated,
                        contentColor = VaultTextPrimary
                    ),
                    border = BorderStroke(1.dp, VaultHairlineStrong),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_lock_now_button")
                ) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("立即锁定保险库", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onSignOut,
                    border = BorderStroke(1.dp, VaultRose.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = VaultRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.security_sign_out),
                        color = VaultRose,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onSubmit = { oldPin, newPin, onErr ->
                onChangePin(oldPin, newPin) { res ->
                    if (res.isSuccess) {
                        showChangePinDialog = false
                    } else {
                        onErr(res.exceptionOrNull()?.localizedMessage ?: "修改 PIN 失败")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = VaultTextMuted,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color = VaultTextPrimary
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VaultSurfaceElevated)
            .border(1.dp, VaultHairline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    iconTint: Color = VaultTextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIconBadge(icon = icon, tint = iconTint)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = VaultTextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = VaultTextSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = VaultTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun vaultSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = VaultObsidian,
    checkedTrackColor = VaultCyan,
    uncheckedThumbColor = VaultTextSecondary,
    uncheckedTrackColor = VaultSurfaceElevated,
    uncheckedBorderColor = VaultHairlineStrong
)

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPin: String, newPin: String, onError: (String) -> Unit) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurfaceElevated,
        title = { Text("修改 6 位保险库 PIN 码", color = VaultTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPin = it },
                    label = { Text("当前 6 位 PIN 码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = vaultTextFieldColors()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("新 6 位 PIN 码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = vaultTextFieldColors()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("确认新 PIN 码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = vaultTextFieldColors()
                )
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = VaultRose, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        currentPin.length != 6 -> errorMsg = "请输入 6 位当前 PIN 码"
                        newPin.length != 6 -> errorMsg = "请输入 6 位新 PIN 码"
                        newPin != confirmPin -> errorMsg = "两次输入的新 PIN 码不一致"
                        else -> onSubmit(currentPin, newPin) { errorMsg = it }
                    }
                }
            ) {
                Text("确认更新", color = VaultPrimaryCta, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = VaultTextSecondary)
            }
        }
    )
}
