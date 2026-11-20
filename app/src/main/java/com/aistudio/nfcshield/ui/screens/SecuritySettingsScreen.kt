package com.aistudio.nfcshield.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
                text = stringResource(R.string.security_title),
                style = MaterialTheme.typography.headlineMedium,
                color = VaultTextPrimary
            )

            // Account & Encryption Summary
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = VaultCyan
                        )
                        Column {
                            Text(
                                text = userEmail ?: "Google 已认证用户",
                                style = MaterialTheme.typography.titleMedium,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = "云端同步策略：零知识端到端密文存储",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VaultCyan
                            )
                        }
                    }
                    Text(
                        text = "加密套件：AES-256-GCM · PBKDF2-HMAC-SHA256 (120,000 次) · Android Keystore 硬件封装",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultTextSecondary
                    )
                }
            }

            // Authentication & Lock Controls
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "访问控制与解锁策略",
                        style = MaterialTheme.typography.titleMedium,
                        color = VaultTextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null, tint = VaultCyan)
                            Column {
                                Text("生物识别快速解锁", style = MaterialTheme.typography.bodyLarge, color = VaultTextPrimary)
                                Text(
                                    text = when (biometricSealMode) {
                                        BiometricSealMode.RSA_CRYPTO_OBJECT ->
                                            "硬件密钥 · 每次解锁需生物识别认证"
                                        BiometricSealMode.RSA_DEVICE_CREDENTIAL ->
                                            "硬件密钥 · 每次解锁需生物或锁屏密码认证"
                                        BiometricSealMode.AES_KEYSTORE ->
                                            "旧版设备绑定缓存（安全性较低），输入一次 PIN 即可升级"
                                        null -> when {
                                            !bioHardwareReady -> "该设备不支持安全生物识别，仅 PIN 解锁"
                                            else -> "尚未封装密钥，解锁后将启用硬件认证"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VaultTextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = prefs.biometricEnabled && bioHardwareReady,
                            onCheckedChange = onToggleBiometric,
                            enabled = bioHardwareReady,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultObsidian,
                                checkedTrackColor = VaultCyan
                            ),
                            modifier = Modifier.testTag("settings_biometric_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.Nfc, contentDescription = null, tint = VaultCyan)
                            Column {
                                Text("仅应用解锁时允许 HCE 刷卡", style = MaterialTheme.typography.bodyLarge, color = VaultTextPrimary)
                                Text(
                                    text = "开启后未解锁保险库前将拒绝外部读卡器",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VaultTextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = prefs.requireVaultUnlockedForHce,
                            onCheckedChange = onToggleRequireUnlockForHce,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultObsidian,
                                checkedTrackColor = VaultCyan
                            ),
                            modifier = Modifier.testTag("settings_hce_lock_switch")
                        )
                    }

                    val immediateAutoLock = prefs.autoLockMinutes == 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = VaultCyan)
                            Column {
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (immediateAutoLock) VaultCyan else VaultTextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = immediateAutoLock,
                            onCheckedChange = { enabled ->
                                onChangeAutoLockMinutes(if (enabled) 0 else 5)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultObsidian,
                                checkedTrackColor = VaultCyan
                            ),
                            modifier = Modifier.testTag("settings_auto_lock_immediate_switch")
                        )
                    }

                    Text("后台自动锁定延迟策略", style = MaterialTheme.typography.labelLarge, color = VaultTextSecondary)
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
                            FilterChip(
                                selected = prefs.autoLockMinutes == mins,
                                onClick = { onChangeAutoLockMinutes(mins) },
                                label = { Text(title) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultCyanContainer,
                                    selectedLabelColor = VaultCyan,
                                    containerColor = VaultSurfaceElevated,
                                    labelColor = VaultTextSecondary
                                ),
                                modifier = Modifier.testTag("auto_lock_chip_$mins")
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showChangePinDialog = true },
                        border = BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("change_pin_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Key, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("修改 6 位备用 PIN 码", color = VaultTextPrimary)
                    }
                }
            }

            // HCE Diagnostics & Tap Telemetry
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("NFC / HCE 服务诊断", style = MaterialTheme.typography.titleMedium, color = VaultTextPrimary)
                    Text(
                        text = "NFC 硬件: ${if (nfcStatus.nfcSupported) "支持" else "不支持"} · 状态: ${if (nfcStatus.nfcEnabled) "已开启" else "未开启"} · HCE: ${if (nfcStatus.hceSupported) "就绪" else "不可用"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultTextSecondary
                    )
                    if (nfcStatus.registeredAids.isNotEmpty()) {
                        Text(
                            text = "已注册 AID: ${nfcStatus.registeredAids.joinToString(", ")}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultCyan
                        )
                    }
                    if (recentEvents.isNotEmpty()) {
                        Text("近期 APDU 通信日志", style = MaterialTheme.typography.labelLarge, color = VaultTextSecondary)
                        recentEvents.take(4).forEach { ev ->
                            Text(
                                text = "• ${ev.cardLabel} | AID=${ev.aidHex.ifEmpty { "-" }} | SW=${ev.statusWordHex}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ev.accepted) VaultCyan else VaultRose
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenNfcDebug,
                        border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_open_debug_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Nfc, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("打开 NFC 硬件与标签技术栈调试台", color = VaultCyan)
                    }

                    OutlinedButton(
                        onClick = onAddQuickTile,
                        border = BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_add_quick_tile_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Tune, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.qs_tile_add_button), color = VaultTextPrimary)
                    }
                }
            }

            // Lock & Sign out actions
            Button(
                onClick = onLockVaultNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultSurfaceElevated,
                    contentColor = VaultCyan
                ),
                border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_lock_now_button")
            ) {
                Icon(imageVector = Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("立即锁定保险库", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onSignOut,
                border = BorderStroke(1.dp, VaultRose.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_sign_out_button")
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = VaultRose, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.security_sign_out), color = VaultRose, fontWeight = FontWeight.SemiBold)
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
                    Text(errorMsg.orEmpty(), color = VaultRose, style = MaterialTheme.typography.bodyMedium)
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
                Text("确认更新", color = VaultCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = VaultTextSecondary)
            }
        }
    )
}
