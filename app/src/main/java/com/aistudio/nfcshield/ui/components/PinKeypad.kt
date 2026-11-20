package com.aistudio.nfcshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.nfcshield.ui.theme.SpaceGroteskFontFamily
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultHairline
import com.aistudio.nfcshield.ui.theme.VaultHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultPrimaryCta
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary
import kotlin.math.roundToInt

@Composable
fun PinDotsIndicator(
    pinLength: Int,
    maxDigits: Int = 6,
    isError: Boolean = false,
    shakeTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            val deltas = listOf(-16f, 16f, -12f, 12f, -6f, 6f, 0f)
            deltas.forEach { target ->
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 42))
            }
        }
    }

    Row(
        modifier = modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxDigits) { index ->
            val filled = index < pinLength
            val dotScale by animateFloatAsState(
                targetValue = if (filled) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "pin_dot_scale_$index"
            )
            val fillColor by animateColorAsState(
                targetValue = when {
                    isError -> VaultRose
                    filled -> VaultPrimaryCta
                    else -> Color.Transparent
                },
                animationSpec = tween(140),
                label = "pin_dot_fill_$index"
            )
            val borderColor by animateColorAsState(
                targetValue = when {
                    isError -> VaultRose
                    filled -> VaultPrimaryCta
                    else -> VaultHairlineStrong
                },
                animationSpec = tween(140),
                label = "pin_dot_border_$index"
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(1.5.dp, borderColor, CircleShape)
            )
        }
    }
}

@Composable
fun PinKeypad(
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )

    Column(
        modifier = modifier.widthIn(max = 312.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowDigits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowDigits.forEach { digit ->
                    KeypadDigitButton(
                        digit = digit,
                        enabled = enabled,
                        onClick = { onDigitClick(digit) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBiometricClick != null) {
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled, onClick = onBiometricClick)
                        .testTag("pin_biometric_button"),
                    shape = CircleShape,
                    color = VaultSurface,
                    border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "使用生物识别解锁",
                            tint = VaultCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }

            KeypadDigitButton(
                digit = '0',
                enabled = enabled,
                onClick = { onDigitClick('0') }
            )

            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onBackspaceClick)
                    .testTag("pin_backspace_button"),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, VaultHairline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "删除一位",
                        tint = VaultTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("pin_digit_$digit"),
        shape = CircleShape,
        color = VaultSurfaceElevated,
        border = BorderStroke(1.dp, VaultHairline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) VaultTextPrimary else VaultTextSecondary
            )
        }
    }
}
