package com.aistudio.nfcshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.data.model.HceTapEvent
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.ObsidianHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultObsidian

private enum class DetailHceStatusMode {
    EMULATING_LIVE,
    ACTIVE_NFC_OFF,
    ACTIVE_NO_HCE_HW,
    STANDBY,
    UNSUPPORTED_CARD
}

/**
 * Floating Obsidian bottom dock for the card detail screen, displaying a real-time
 * NFC/HCE emulation status indicator (with a subtle pulsing dot animation while active)
 * alongside a quick HCE toggle switch.
 */
@Composable
fun FloatingHceEmulationBar(
    card: DecryptedAccessCard,
    nfcStatus: NfcHardwareStatus?,
    latestTapEvent: HceTapEvent?,
    onToggleHce: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    val mode = when {
        !card.hceSupported -> DetailHceStatusMode.UNSUPPORTED_CARD
        !card.activeHce -> DetailHceStatusMode.STANDBY
        nfcStatus != null && nfcStatus.nfcSupported && !nfcStatus.nfcEnabled ->
            DetailHceStatusMode.ACTIVE_NFC_OFF
        nfcStatus != null && !nfcStatus.hceSupported ->
            DetailHceStatusMode.ACTIVE_NO_HCE_HW
        else -> DetailHceStatusMode.EMULATING_LIVE
    }

    val isLivePulse = mode == DetailHceStatusMode.EMULATING_LIVE
    val isWarningPulse = mode == DetailHceStatusMode.ACTIVE_NFC_OFF

    val dotColor by animateColorAsState(
        targetValue = when (mode) {
            DetailHceStatusMode.EMULATING_LIVE -> VaultCyan
            DetailHceStatusMode.ACTIVE_NFC_OFF, DetailHceStatusMode.ACTIVE_NO_HCE_HW -> VaultAmber
            DetailHceStatusMode.STANDBY, DetailHceStatusMode.UNSUPPORTED_CARD ->
                colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "detail_hce_dot_color"
    )

    val borderColor by animateColorAsState(
        targetValue = when (mode) {
            DetailHceStatusMode.EMULATING_LIVE -> VaultCyan.copy(alpha = 0.48f)
            DetailHceStatusMode.ACTIVE_NFC_OFF, DetailHceStatusMode.ACTIVE_NO_HCE_HW ->
                VaultAmber.copy(alpha = 0.45f)
            DetailHceStatusMode.STANDBY, DetailHceStatusMode.UNSUPPORTED_CARD ->
                ObsidianHairlineStrong
        },
        animationSpec = tween(durationMillis = 220),
        label = "detail_hce_dock_border"
    )

    val titleText = when (mode) {
        DetailHceStatusMode.EMULATING_LIVE -> stringResource(R.string.detail_hce_emulating_title)
        DetailHceStatusMode.ACTIVE_NFC_OFF -> stringResource(R.string.detail_hce_nfc_off_title)
        DetailHceStatusMode.ACTIVE_NO_HCE_HW -> stringResource(R.string.detail_hce_hw_unsupported_title)
        DetailHceStatusMode.STANDBY -> stringResource(R.string.detail_hce_standby_title)
        DetailHceStatusMode.UNSUPPORTED_CARD -> stringResource(R.string.detail_hce_readonly_title)
    }

    val subtitleText = when (mode) {
        DetailHceStatusMode.EMULATING_LIVE -> {
            if (latestTapEvent != null && latestTapEvent.cardLabel == card.label) {
                "APDU SW=${latestTapEvent.statusWordHex} · ${if (latestTapEvent.accepted) "刷卡通过" else "已拒绝"}"
            } else {
                val aidPreview = card.secrets.aidHex.take(10).ifBlank { "DEFAULT" }
                stringResource(R.string.detail_hce_emulating_subtitle, aidPreview)
            }
        }
        DetailHceStatusMode.ACTIVE_NFC_OFF -> stringResource(R.string.detail_hce_nfc_off_subtitle)
        DetailHceStatusMode.ACTIVE_NO_HCE_HW -> stringResource(R.string.detail_hce_hw_unsupported_subtitle)
        DetailHceStatusMode.STANDBY -> stringResource(R.string.detail_hce_standby_subtitle)
        DetailHceStatusMode.UNSUPPORTED_CARD ->
            stringResource(R.string.detail_hce_readonly_subtitle, card.primaryTech.name)
    }

    val indicatorDescription = if (isLivePulse) {
        stringResource(R.string.detail_hce_indicator_emulating_cd)
    } else {
        stringResource(R.string.detail_hce_indicator_idle_cd)
    }
    val switchDescription = stringResource(R.string.detail_hce_switch_cd)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("detail_floating_hce_bar"),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainer,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HceLivePulseDot(
                color = dotColor,
                pulsing = isLivePulse || isWarningPulse,
                contentDesc = indicatorDescription,
                modifier = Modifier.testTag("detail_hce_live_dot")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("detail_hce_status_text")
                    )
                    if (isLivePulse) {
                        Surface(
                            shape = CircleShape,
                            color = VaultCyan.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = stringResource(R.string.nfc_monitor_live_badge),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    } else if (isWarningPulse) {
                        Surface(
                            shape = CircleShape,
                            color = VaultAmber.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VaultAmber.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenNfcSettings)
                                .testTag("detail_hce_open_nfc_settings")
                        ) {
                            Text(
                                text = stringResource(R.string.nfc_monitor_open_settings),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = VaultAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWarningPulse) VaultAmber else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = card.hceSupported && card.activeHce,
                onCheckedChange = { onToggleHce() },
                enabled = card.hceSupported,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VaultObsidian,
                    checkedTrackColor = if (isWarningPulse) VaultAmber else VaultCyan,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = colorScheme.surface,
                    uncheckedBorderColor = ObsidianHairlineStrong,
                    disabledCheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    disabledUncheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    disabledUncheckedTrackColor = colorScheme.surface.copy(alpha = 0.6f),
                    disabledUncheckedBorderColor = ObsidianHairlineStrong.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = switchDescription }
                    .testTag("detail_floating_hce_switch")
            )
        }
    }
}

@Composable
private fun HceLivePulseDot(
    color: Color,
    pulsing: Boolean,
    contentDesc: String,
    modifier: Modifier = Modifier
) {
    val boxModifier = modifier
        .size(28.dp)
        .semantics { contentDescription = contentDesc }

    if (pulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "hce_live_dot_pulse")
        val ringScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 2.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "hce_pulse_ring_scale"
        )
        val ringAlpha by infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "hce_pulse_ring_alpha"
        )
        val coreScale by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hce_pulse_core_scale"
        )

        Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(ringScale)
                    .alpha(ringAlpha)
                    .clip(CircleShape)
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(coreScale)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    } else {
        Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
