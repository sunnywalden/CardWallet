package com.aistudio.nfcshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.CardIconOption
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.SpaceGroteskFontFamily
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultEmerald
import com.aistudio.nfcshield.ui.theme.VaultHairline
import com.aistudio.nfcshield.ui.theme.VaultHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultIndigo
import com.aistudio.nfcshield.ui.theme.VaultOnPrimaryCta
import com.aistudio.nfcshield.ui.theme.VaultPrimaryCta
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultTextMuted
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary

fun CardAccentColor.toColor(): Color = when (this) {
    CardAccentColor.CYAN -> VaultCyan
    CardAccentColor.EMERALD -> VaultEmerald
    CardAccentColor.INDIGO -> VaultIndigo
    CardAccentColor.AMBER -> VaultAmber
    CardAccentColor.ROSE -> VaultRose
}

fun CardCategory.toIcon(): ImageVector = when (this) {
    CardCategory.RESIDENCE -> Icons.Filled.Home
    CardCategory.OFFICE -> Icons.Filled.Apartment
    CardCategory.CAMPUS -> Icons.Filled.School
    CardCategory.PARKING -> Icons.Filled.DirectionsCar
    CardCategory.TRANSIT -> Icons.Filled.Train
    CardCategory.OTHER -> Icons.Filled.CreditCard
}

fun CardIconOption.toIcon(fallbackCategory: CardCategory): ImageVector = when (this) {
    CardIconOption.AUTO -> fallbackCategory.toIcon()
    CardIconOption.KEY -> Icons.Filled.VpnKey
    CardIconOption.HOME -> Icons.Filled.Home
    CardIconOption.OFFICE -> Icons.Filled.Apartment
    CardIconOption.BADGE -> Icons.Filled.Badge
    CardIconOption.CAR -> Icons.Filled.DirectionsCar
    CardIconOption.TRANSIT -> Icons.Filled.Train
    CardIconOption.CAMPUS -> Icons.Filled.School
    CardIconOption.GYM -> Icons.Filled.FitnessCenter
    CardIconOption.STORE -> Icons.Filled.Storefront
    CardIconOption.SHIELD -> Icons.Filled.Shield
}

fun DecryptedAccessCard.resolvedIcon(): ImageVector =
    CardIconOption.fromRaw(secrets.iconKey).toIcon(category)

/**
 * Apple Wallet / Linear-inspired physical anodized titanium access card.
 * When [heroGlow] is enabled (used for the top focal card on the Wallet home screen),
 * renders a restrained translucent backlight halo, top-edge glass rim sheen, and
 * gentle ambient breathing luminance when active in HCE.
 */
@Composable
fun AccessCardVisual(
    card: DecryptedAccessCard,
    onClick: () -> Unit,
    onToggleHce: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    heroGlow: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = card.accentColor.toColor()
    val glowHue = if (card.activeHce) accent else colorScheme.primary

    val glowPulseTransition = rememberInfiniteTransition(label = "hero_card_glow_pulse")
    val glowPulse by glowPulseTransition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_glow_alpha_factor"
    )
    val effectivePulse = if (heroGlow && card.activeHce) glowPulse else 1f

    val borderColor by animateColorAsState(
        targetValue = when {
            card.activeHce && heroGlow -> accent.copy(alpha = 0.42f)
            card.activeHce -> accent.copy(alpha = 0.36f)
            heroGlow -> colorScheme.primary.copy(alpha = 0.22f)
            else -> VaultHairlineStrong
        },
        animationSpec = tween(260),
        label = "card_border"
    )

    val cardBaseGradient = Brush.linearGradient(
        colors = listOf(
            colorScheme.surfaceContainerHigh,
            colorScheme.surface,
            colorScheme.background
        ),
        start = Offset(0f, 0f),
        end = Offset(920f, 640f)
    )

    // Outer ambient translucent backlight behind the hero focal card
    val ambientGlowModifier = if (heroGlow) {
        Modifier.drawBehind {
            val haloCenter = Offset(size.width * 0.56f, size.height * 0.42f)
            val haloRadius = size.maxDimension * 0.68f
            val baseAlpha = (if (card.activeHce) 0.17f else 0.09f) * effectivePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowHue.copy(alpha = baseAlpha),
                        glowHue.copy(alpha = baseAlpha * 0.42f),
                        Color.Transparent
                    ),
                    center = haloCenter,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = haloCenter
            )
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(ambientGlowModifier)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .testTag("access_card_${card.id}"),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = if (heroGlow) 14.dp else if (card.activeHce) 8.dp else 3.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 172.dp else 212.dp)
                    .background(cardBaseGradient)
            ) {
                // Physical anodized surface sheen, subtle inner translucent glow & precision NFC arcs
                Canvas(modifier = Modifier.matchParentSize()) {
                    // Subtle upper-right translucent internal bloom
                    val innerBloomAlpha = when {
                        heroGlow && card.activeHce -> 0.21f * effectivePulse
                        heroGlow -> 0.12f
                        card.activeHce -> 0.16f
                        else -> 0.08f
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowHue.copy(alpha = innerBloomAlpha),
                                glowHue.copy(alpha = innerBloomAlpha * 0.28f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.86f, size.height * 0.14f),
                            radius = size.width * 0.66f
                        ),
                        radius = size.width * 0.66f,
                        center = Offset(size.width * 0.86f, size.height * 0.14f)
                    )

                    // Subtle lower-left translucent counter-glow for frosted glass depth on hero card
                    if (heroGlow) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.06f * effectivePulse),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.14f, size.height * 0.92f),
                                radius = size.width * 0.52f
                            ),
                            radius = size.width * 0.52f,
                            center = Offset(size.width * 0.14f, size.height * 0.92f)
                        )
                    }

                    // Diagonal titanium specular sheen
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (heroGlow) 0.065f else 0.045f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.025f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        )
                    )

                    // Top-edge hairline specular light catch for focal tactile polish
                    if (heroGlow) {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.24f),
                                    glowHue.copy(alpha = 0.30f * effectivePulse),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(size.width * 0.08f, 1.dp.toPx()),
                            end = Offset(size.width * 0.92f, 1.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Precision concentric NFC arcs radiating from top-right
                    val arcCenter = Offset(size.width * 0.92f, size.height * 0.22f)
                    listOf(0.22f, 0.34f, 0.46f).forEachIndexed { idx, factor ->
                        drawCircle(
                            color = Color.White.copy(alpha = (if (heroGlow) 0.055f else 0.04f) - idx * 0.01f),
                            radius = size.width * factor,
                            center = arcCenter,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = if (compact) 18.dp else 22.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Row: Card emblem + Title + Contactless symbol
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.11f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = card.resolvedIcon(),
                                    contentDescription = card.category.displayName,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = card.label,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VaultTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${card.category.displayName} · ${card.primaryTech.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VaultTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Filled.Contactless,
                            contentDescription = "NFC 非接触式感应",
                            tint = if (card.activeHce) accent else Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.height(if (compact) 18.dp else 28.dp))

                    // Middle Row: Brushed EMV chip + Card Credential Identifier
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SmartCardChip(accentColor = accent, active = card.activeHce)
                        Text(
                            text = if (card.decryptionFailed) {
                                "密钥不匹配"
                            } else {
                                card.maskedUid.ifEmpty { "HCE VIRTUAL PASS" }
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (card.decryptionFailed) VaultRose else VaultTextPrimary.copy(alpha = 0.92f),
                            letterSpacing = 1.6.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(if (compact) 16.dp else 24.dp))

                    // Bottom Row: Zero-Knowledge Encryption Tag + Action Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "AES-256-GCM 加密",
                                tint = VaultCyan.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "AES-256-GCM · 已刷 ${card.emulationCount} 次",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultTextSecondary
                            )
                        }

                        if (onToggleHce != null) {
                            val isInteractive = card.hceSupported
                            val pillContainer = when {
                                card.activeHce -> accent.copy(alpha = 0.18f)
                                card.hceSupported -> VaultPrimaryCta
                                else -> Color.White.copy(alpha = 0.06f)
                            }
                            val pillContent = when {
                                card.activeHce -> accent
                                card.hceSupported -> VaultOnPrimaryCta
                                else -> VaultTextMuted
                            }
                            val pillBorder = when {
                                card.activeHce -> BorderStroke(1.dp, accent.copy(alpha = 0.50f))
                                card.hceSupported -> null
                                else -> BorderStroke(1.dp, VaultHairline)
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = isInteractive, onClick = onToggleHce)
                                    .testTag("toggle_hce_${card.id}"),
                                shape = CircleShape,
                                color = pillContainer,
                                border = pillBorder
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (card.activeHce) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                    } else if (!card.hceSupported) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = pillContent,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    Text(
                                        text = when {
                                            card.activeHce -> "模拟刷卡中"
                                            card.hceSupported -> "启用模拟"
                                            else -> "仅读取"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = pillContent,
                                        fontWeight = FontWeight.SemiBold
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

@Composable
private fun SmartCardChip(accentColor: Color, active: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val metallicLight = if (active) colorScheme.primary else colorScheme.secondary
    val metallicDark = colorScheme.outline
    val chipCore = colorScheme.surface
    Canvas(modifier = Modifier.size(width = 36.dp, height = 26.dp)) {
        val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    metallicLight.copy(alpha = 0.30f),
                    metallicDark.copy(alpha = 0.25f),
                    metallicLight.copy(alpha = 0.20f)
                )
            ),
            cornerRadius = radius
        )
        drawRoundRect(
            color = if (active) accentColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.22f),
            cornerRadius = radius,
            style = Stroke(width = 1.dp.toPx())
        )
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.24f)
        drawLine(lineColor, Offset(w * 0.34f, 0f), Offset(w * 0.34f, h), strokeWidth = 0.8.dp.toPx())
        drawLine(lineColor, Offset(w * 0.66f, 0f), Offset(w * 0.66f, h), strokeWidth = 0.8.dp.toPx())
        drawLine(lineColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 0.8.dp.toPx())
        drawRoundRect(
            color = chipCore,
            topLeft = Offset(w * 0.34f, h * 0.30f),
            size = Size(w * 0.32f, h * 0.40f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}
