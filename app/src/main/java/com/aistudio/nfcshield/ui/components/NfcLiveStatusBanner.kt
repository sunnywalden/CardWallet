package com.aistudio.nfcshield.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.nfc.HceAidManager
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultAmberContainer
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultHairline
import com.aistudio.nfcshield.ui.theme.VaultIceBlue
import com.aistudio.nfcshield.ui.theme.VaultObsidian
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultTextMuted
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary

/**
 * Observes Android's NFC adapter state in real time via ACTION_ADAPTER_STATE_CHANGED broadcast
 * and lifecycle resume refreshes, returning a reactive [NfcHardwareStatus] state.
 */
@Composable
fun rememberLiveNfcStatus(): State<NfcHardwareStatus> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val statusState = remember(context) {
        mutableStateOf(HceAidManager.getHardwareStatus(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                statusState.value = HceAidManager.getHardwareStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                    val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)
                    statusState.value = HceAidManager.getHardwareStatus(
                        context = context,
                        overrideAdapterState = state
                    )
                }
            }
        }
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.isSuccess
        onDispose {
            if (registered) {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    return statusState
}

/**
 * Navigates to Android's system NFC settings screen with graceful fallbacks.
 */
fun openSystemNfcSettings(context: Context): Boolean {
    return runCatching {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }.isSuccess || runCatching {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }.isSuccess || runCatching {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }.isSuccess
}

/**
 * Restrained Linear/Apple-style real-time NFC adapter status bar.
 * Stays sleek and whisper-quiet when NFC is ON; expands with a crisp amber call-to-action
 * to jump directly into Android NFC settings when NFC is OFF.
 */
@Composable
fun NfcLiveStatusBanner(
    nfcStatus: NfcHardwareStatus,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTransitioning = nfcStatus.nfcSupported && (
        nfcStatus.adapterState == NfcAdapter.STATE_TURNING_ON ||
            nfcStatus.adapterState == NfcAdapter.STATE_TURNING_OFF
        )
    val isEnabled = nfcStatus.nfcSupported && nfcStatus.nfcEnabled && !isTransitioning
    val isDisabled = nfcStatus.nfcSupported && !nfcStatus.nfcEnabled && !isTransitioning

    val pulseTransition = rememberInfiniteTransition(label = "nfc_live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nfc_pulse_alpha"
    )

    val accentColor by animateColorAsState(
        targetValue = when {
            isEnabled -> VaultCyan
            isTransitioning -> VaultIceBlue
            isDisabled -> VaultAmber
            else -> VaultTextMuted
        },
        animationSpec = tween(durationMillis = 220),
        label = "nfc_accent"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isDisabled -> VaultAmberContainer
            else -> VaultSurface
        },
        animationSpec = tween(durationMillis = 220),
        label = "nfc_container"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isDisabled -> VaultAmber.copy(alpha = 0.45f)
            isTransitioning -> VaultIceBlue.copy(alpha = 0.35f)
            else -> VaultHairline
        },
        animationSpec = tween(durationMillis = 220),
        label = "nfc_border"
    )

    val titleText = when {
        isEnabled -> stringResource(R.string.nfc_monitor_on_title)
        isTransitioning -> stringResource(R.string.nfc_monitor_turning_title)
        isDisabled -> stringResource(R.string.nfc_monitor_off_title)
        else -> stringResource(R.string.nfc_monitor_unsupported_title)
    }

    val subtitleText = when {
        isEnabled && nfcStatus.hceSupported -> stringResource(R.string.nfc_monitor_on_hce_subtitle)
        isEnabled -> stringResource(R.string.nfc_monitor_on_read_subtitle)
        isTransitioning -> nfcStatus.adapterStateLabel
        isDisabled -> stringResource(R.string.nfc_monitor_off_subtitle)
        else -> stringResource(R.string.nfc_monitor_unsupported_subtitle)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("nfc_live_status_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = if (isDisabled) 8.dp else 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (isDisabled) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isDisabled -> Icons.Filled.WarningAmber
                        isTransitioning -> Icons.Filled.Sync
                        else -> Icons.Filled.Nfc
                    },
                    contentDescription = titleText,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(if (isEnabled || isTransitioning) pulseAlpha else 1f)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelLarge,
                        color = VaultTextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("nfc_live_status_title")
                    )
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDisabled) VaultAmber.copy(alpha = 0.9f) else VaultTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = isDisabled,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Button(
                    onClick = onOpenNfcSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultAmber,
                        contentColor = VaultObsidian
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("nfc_status_open_settings_button")
                ) {
                    Text(
                        text = stringResource(R.string.nfc_monitor_open_settings),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isEnabled) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = VaultCyan.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = stringResource(R.string.nfc_monitor_live_badge),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultCyan,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
