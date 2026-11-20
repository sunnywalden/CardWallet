package com.aistudio.nfcshield.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary

@Composable
fun NfcRadarScanner(
    isScanning: Boolean,
    hasDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nfc_radar")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_1"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0.33f,
        targetValue = 1.33f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_2"
    )
    val phase3 by transition.animateFloat(
        initialValue = 0.66f,
        targetValue = 1.66f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_3"
    )

    val ringColor = if (hasDetected) VaultCyan else Color.White

    Box(
        modifier = modifier.size(184.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(184.dp)) {
            val maxRadius = size.minDimension / 2f
            val baseRadius = maxRadius * 0.36f
            if (isScanning && !hasDetected) {
                listOf(phase1 % 1f, phase2 % 1f, phase3 % 1f).forEach { p ->
                    val radius = baseRadius + (maxRadius - baseRadius) * p
                    val alpha = (1f - p).coerceIn(0f, 1f) * 0.36f
                    drawCircle(
                        color = ringColor.copy(alpha = alpha),
                        radius = radius,
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }
            } else {
                drawCircle(
                    color = VaultCyan.copy(alpha = 0.25f),
                    radius = maxRadius * 0.72f,
                    style = Stroke(width = 1.25.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (hasDetected) VaultCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                            VaultSurfaceElevated
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (hasDetected) VaultCyan.copy(alpha = 0.65f) else VaultHairlineStrong,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasDetected) Icons.Filled.CheckCircle else Icons.Filled.Nfc,
                contentDescription = if (hasDetected) "已读取门禁卡" else "等待 NFC 门禁卡贴近",
                tint = if (hasDetected) VaultCyan else VaultTextPrimary,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
