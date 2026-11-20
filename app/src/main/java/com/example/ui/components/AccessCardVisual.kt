package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import com.example.data.model.CardAccentColor
import com.example.data.model.CardCategory
import com.example.data.model.CardIconOption
import com.example.data.model.DecryptedAccessCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VaultAmber
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultCyan
import com.example.ui.theme.VaultEmerald
import com.example.ui.theme.VaultIceBlue
import com.example.ui.theme.VaultIndigo
import com.example.ui.theme.VaultObsidian
import com.example.ui.theme.VaultRose
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary

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

@Composable
fun AccessCardVisual(
    card: DecryptedAccessCard,
    onClick: () -> Unit,
    onToggleHce: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val accent = card.accentColor.toColor()
    val borderColor by animateColorAsState(
        targetValue = if (card.activeHce) accent else VaultBorder,
        label = "card_border"
    )
    val gradient = Brush.linearGradient(
        colors = listOf(
            VaultSurfaceElevated,
            VaultSurface,
            accent.copy(alpha = if (card.activeHce) 0.18f else 0.08f)
        ),
        start = Offset.Zero,
        end = Offset(900f, 500f)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("access_card_${card.id}"),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(if (card.activeHce) 1.5.dp else 1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(if (compact) 16.dp else 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = 0.14f))
                                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = card.resolvedIcon(),
                                contentDescription = card.category.displayName,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = card.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = VaultTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${card.category.displayName} · ${card.primaryTech.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VaultTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Smart chip + contactless icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmartCardChip(accentColor = accent)
                        Icon(
                            imageVector = Icons.Filled.Contactless,
                            contentDescription = "NFC 非接触式感应",
                            tint = if (card.activeHce) accent else VaultTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (card.decryptionFailed) "密钥不匹配" else card.maskedUid.ifEmpty { "HCE 虚拟凭证" },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (card.decryptionFailed) VaultRose else VaultTextPrimary,
                            letterSpacing = 1.2.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "AES-256-GCM 加密",
                                tint = VaultCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "AES-256-GCM 已加密",
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultCyan
                            )
                        }
                    }

                    if (onToggleHce != null) {
                        val badgeBg = when {
                            card.activeHce -> accent.copy(alpha = 0.2f)
                            card.hceSupported -> VaultSurfaceElevated
                            else -> VaultObsidian.copy(alpha = 0.6f)
                        }
                        val badgeText = when {
                            card.activeHce -> "模拟刷卡中"
                            card.hceSupported -> "启用模拟"
                            else -> "仅读取"
                        }
                        val badgeColor = when {
                            card.activeHce -> accent
                            card.hceSupported -> VaultTextPrimary
                            else -> VaultTextSecondary
                        }
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onToggleHce)
                                .testTag("toggle_hce_${card.id}"),
                            shape = CircleShape,
                            color = badgeBg,
                            border = BorderStroke(
                                1.dp,
                                if (card.activeHce) accent else VaultBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (!card.hceSupported) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
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

@Composable
private fun SmartCardChip(accentColor: Color) {
    Canvas(modifier = Modifier.size(width = 30.dp, height = 22.dp)) {
        drawRoundRect(
            color = accentColor.copy(alpha = 0.22f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        drawRoundRect(
            color = accentColor.copy(alpha = 0.7f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        val w = size.width
        val h = size.height
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(w * 0.35f, 0f),
            end = Offset(w * 0.35f, h),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(w * 0.65f, 0f),
            end = Offset(w * 0.65f, h),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = 0.8.dp.toPx()
        )
        drawRoundRect(
            color = VaultObsidian,
            topLeft = Offset(w * 0.35f, h * 0.32f),
            size = Size(w * 0.3f, h * 0.36f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}
