package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.auth.BiometricHelper
import com.example.ui.components.PinDotsIndicator
import com.example.ui.components.PinKeypad
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultCyan
import com.example.ui.theme.VaultObsidian
import com.example.ui.theme.VaultRose
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary
import com.example.ui.viewmodel.BiometricUnlockChallenge
import com.example.ui.viewmodel.VaultGateState
import javax.crypto.Cipher

@Composable
fun VaultLockScreen(
    gateState: VaultGateState,
    biometricPrefEnabled: Boolean,
    userEmail: String?,
    onSetupPin: (pin: String, enableBiometric: Boolean, onError: (String) -> Unit) -> Unit,
    onSubmitUnlockPin: (String) -> Unit,
    onPrepareBiometricChallenge: ((BiometricUnlockChallenge) -> Unit) -> Unit,
    onBiometricCryptoSuccess: (cipher: Cipher, sealedDekBase64: String, onFallbackMsg: (String?) -> Unit) -> Unit,
    onBiometricSuccess: (onFallbackMsg: (String?) -> Unit) -> Unit,
    onClearError: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricHardwareReady = remember(context) { BiometricHelper.isBiometricAvailable(context) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VaultObsidian)
            .safeDrawingPadding()
    ) {
        when (gateState) {
            is VaultGateState.Loading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = VaultCyan)
                    Text(
                        text = "正在验证云端保险库密钥…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VaultTextSecondary
                    )
                }
            }

            is VaultGateState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 460.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = VaultRose,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "云端保险库同步受限",
                        style = MaterialTheme.typography.headlineSmall,
                        color = VaultTextPrimary
                    )
                    Text(
                        text = gateState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier.testTag("vault_error_sign_out")
                    ) {
                        Text("切换或退出 Google 账号", color = VaultTextPrimary)
                    }
                }
            }

            is VaultGateState.NeedsPinSetup -> {
                PinSetupContent(
                    biometricHardwareReady = biometricHardwareReady,
                    onSetupPin = onSetupPin,
                    onSignOut = onSignOut
                )
            }

            is VaultGateState.Locked -> {
                val canShowBiometric = biometricHardwareReady && biometricPrefEnabled && gateState.hasBiometricSealedKey
                val lifecycleOwner = LocalLifecycleOwner.current
                var pinInput by remember { mutableStateOf("") }
                var localBioHint by remember { mutableStateOf<String?>(null) }
                var shakeTrigger by remember { mutableIntStateOf(0) }
                var autoPrompted by remember(gateState.config.userId) { mutableStateOf(false) }
                var isResumed by remember(lifecycleOwner) {
                    mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                isResumed = true
                            }
                            Lifecycle.Event.ON_PAUSE -> {
                                isResumed = false
                            }
                            Lifecycle.Event.ON_STOP -> {
                                isResumed = false
                                autoPrompted = false
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val triggerBiometric: () -> Unit = {
                    if (activity != null && canShowBiometric) {
                        onPrepareBiometricChallenge { challenge ->
                            when (challenge) {
                                is BiometricUnlockChallenge.CryptoObjectPrompt -> {
                                    BiometricHelper.authenticateWithCryptoObject(
                                        activity = activity,
                                        cipher = challenge.cipher,
                                        onSuccess = { unlockedCipher ->
                                            onBiometricCryptoSuccess(unlockedCipher, challenge.sealedDekBase64) { fallbackMsg ->
                                                localBioHint = fallbackMsg
                                            }
                                        },
                                        onUsePinFallback = {
                                            localBioHint = "请输入 6 位备用 PIN 码解锁"
                                        },
                                        onError = { err ->
                                            localBioHint = err
                                        }
                                    )
                                }

                                is BiometricUnlockChallenge.LegacyPrompt -> {
                                    BiometricHelper.authenticate(
                                        activity = activity,
                                        onSuccess = {
                                            onBiometricSuccess { fallbackMsg ->
                                                localBioHint = fallbackMsg
                                            }
                                        },
                                        onUsePinFallback = {
                                            localBioHint = "请输入 6 位备用 PIN 码解锁"
                                        },
                                        onError = { err ->
                                            localBioHint = err
                                        }
                                    )
                                }

                                is BiometricUnlockChallenge.RequiresPinRebind -> {
                                    localBioHint = challenge.message
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(isResumed, canShowBiometric) {
                    if (isResumed && canShowBiometric && !autoPrompted) {
                        autoPrompted = true
                        triggerBiometric()
                    }
                }

                LaunchedEffect(gateState.errorMessage) {
                    if (gateState.errorMessage != null) {
                        pinInput = ""
                        shakeTrigger++
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = userEmail ?: "已登录账号",
                            style = MaterialTheme.typography.labelMedium,
                            color = VaultTextSecondary
                        )
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("lock_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = VaultTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("退出账号", color = VaultTextSecondary)
                        }
                    }

                    Column(
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = VaultCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "保险库已锁定",
                                tint = VaultCyan,
                                modifier = Modifier.padding(16.dp).size(32.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.vault_unlock_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = VaultTextPrimary
                        )
                        Text(
                            text = stringResource(R.string.vault_unlock_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (canShowBiometric) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = VaultCyan.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = if (BiometricHelper.isStrongBiometricAvailable(context)) {
                                        "Android Keystore 硬件私钥已绑定生物识别"
                                    } else {
                                        "Keystore 硬件密钥保护已就绪"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultCyan,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        PinDotsIndicator(
                            pinLength = pinInput.length,
                            isError = gateState.errorMessage != null,
                            shakeTrigger = shakeTrigger
                        )

                        val displayStatus = when {
                            gateState.lockoutSecondsRemaining > 0 ->
                                stringResource(R.string.vault_pin_lockout, gateState.lockoutSecondsRemaining)
                            gateState.errorMessage != null -> gateState.errorMessage
                            localBioHint != null -> localBioHint
                            else -> null
                        }

                        if (displayStatus != null) {
                            Text(
                                text = displayStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (gateState.errorMessage != null || gateState.lockoutSecondsRemaining > 0) VaultRose else VaultCyan,
                                textAlign = TextAlign.Center
                            )
                        } else if (gateState.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = VaultCyan
                            )
                        } else {
                            Spacer(Modifier.height(20.dp))
                        }

                        PinKeypad(
                            onDigitClick = { digit ->
                                if (pinInput.length < 6 && gateState.lockoutSecondsRemaining == 0 && !gateState.isBusy) {
                                    onClearError()
                                    localBioHint = null
                                    val next = pinInput + digit
                                    pinInput = next
                                    if (next.length == 6) {
                                        onSubmitUnlockPin(next)
                                    }
                                }
                            },
                            onBackspaceClick = {
                                if (pinInput.isNotEmpty()) {
                                    onClearError()
                                    pinInput = pinInput.dropLast(1)
                                }
                            },
                            onBiometricClick = if (canShowBiometric) triggerBiometric else null,
                            enabled = gateState.lockoutSecondsRemaining == 0 && !gateState.isBusy
                        )

                        if (canShowBiometric) {
                            TextButton(
                                onClick = triggerBiometric,
                                modifier = Modifier.testTag("unlock_biometric_cta")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    tint = VaultCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = stringResource(R.string.vault_use_biometric),
                                    color = VaultCyan
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            is VaultGateState.Unlocked -> Unit
        }
    }
}

@Composable
private fun PinSetupContent(
    biometricHardwareReady: Boolean,
    onSetupPin: (pin: String, enableBiometric: Boolean, onError: (String) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var enableBiometric by remember { mutableStateOf(biometricHardwareReady) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }

    val activePin = if (isConfirmStep) confirmPin else firstPin

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onSignOut) {
                Text("更换 Google 账号", color = VaultTextSecondary)
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = VaultCyan,
                    modifier = Modifier.padding(16.dp).size(32.dp)
                )
            }

            Text(
                text = stringResource(if (isConfirmStep) R.string.vault_setup_confirm_title else R.string.vault_setup_title),
                style = MaterialTheme.typography.headlineMedium,
                color = VaultTextPrimary
            )
            Text(
                text = stringResource(if (isConfirmStep) R.string.vault_setup_confirm_subtitle else R.string.vault_setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultTextSecondary,
                textAlign = TextAlign.Center
            )

            PinDotsIndicator(
                pinLength = activePin.length,
                isError = errorMessage != null,
                shakeTrigger = shakeTrigger
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultRose,
                    textAlign = TextAlign.Center
                )
            }

            if (biometricHardwareReady) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VaultSurfaceElevated,
                    border = BorderStroke(1.dp, VaultBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = null,
                                tint = VaultCyan
                            )
                            Text(
                                text = "同时启用生物识别快速解锁",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VaultTextPrimary
                            )
                        }
                        Switch(
                            checked = enableBiometric,
                            onCheckedChange = { enableBiometric = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultObsidian,
                                checkedTrackColor = VaultCyan
                            ),
                            modifier = Modifier.testTag("setup_biometric_switch")
                        )
                    }
                }
            }

            if (isSaving) {
                CircularProgressIndicator(color = VaultCyan)
            }

            PinKeypad(
                onDigitClick = { digit ->
                    if (isSaving) return@PinKeypad
                    errorMessage = null
                    if (!isConfirmStep) {
                        if (firstPin.length < 6) {
                            val next = firstPin + digit
                            firstPin = next
                            if (next.length == 6) {
                                isConfirmStep = true
                            }
                        }
                    } else {
                        if (confirmPin.length < 6) {
                            val next = confirmPin + digit
                            confirmPin = next
                            if (next.length == 6) {
                                if (next == firstPin) {
                                    isSaving = true
                                    onSetupPin(next, enableBiometric) { err ->
                                        isSaving = false
                                        errorMessage = err
                                        confirmPin = ""
                                        shakeTrigger++
                                    }
                                } else {
                                    errorMessage = "两次输入的 PIN 码不一致，请重新设置"
                                    firstPin = ""
                                    confirmPin = ""
                                    isConfirmStep = false
                                    shakeTrigger++
                                }
                            }
                        }
                    }
                },
                onBackspaceClick = {
                    if (isSaving) return@PinKeypad
                    errorMessage = null
                    if (isConfirmStep) {
                        if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        else isConfirmStep = false
                    } else {
                        if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                    }
                },
                enabled = !isSaving
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}
