package com.aistudio.nfcshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.ui.theme.ObsidianHairline
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultRose

/**
 * Wraps a wallet card list row with a left-swipe (EndToStart) delete gesture.
 *
 * Returning `false` from `confirmValueChange` lets the card snap back cleanly while the
 * confirmation dialog is shown, preventing premature visual removal before user confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteCardContainer(
    cardId: String,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            if (targetValue == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRequestDelete()
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.30f }
    )

    val isSwipingLeft = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
    val isThresholdReached = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isThresholdReached -> VaultRose.copy(alpha = 0.26f)
            isSwipingLeft -> VaultRose.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160),
        label = "swipe_delete_bg_color"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isThresholdReached -> VaultRose.copy(alpha = 0.55f)
            isSwipingLeft -> VaultRose.copy(alpha = 0.28f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160),
        label = "swipe_delete_border_color"
    )

    val actionScale by animateFloatAsState(
        targetValue = if (isThresholdReached) 1.08f else 0.92f,
        animationSpec = tween(durationMillis = 150),
        label = "swipe_delete_icon_scale"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .testTag("swipe_delete_container_$cardId"),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(backgroundColor)
                    .border(
                        border = BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 20.dp)
                    .testTag("swipe_delete_background_$cardId"),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSwipingLeft || isThresholdReached) {
                    Row(
                        modifier = Modifier.scale(actionScale),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.wallet_swipe_delete_cd),
                            tint = VaultRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.wallet_swipe_delete_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = VaultRose,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) {
        content()
    }
}

@Composable
fun DeleteCardConfirmDialog(
    card: DecryptedAccessCard,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_card_confirm_dialog"),
        shape = RoundedCornerShape(22.dp),
        containerColor = colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(VaultRose.copy(alpha = 0.14f))
                    .border(1.dp, VaultRose.copy(alpha = 0.30f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.wallet_swipe_delete_cd),
                    tint = VaultRose,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.wallet_delete_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.wallet_delete_dialog_message, card.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                if (card.activeHce) {
                    Text(
                        text = stringResource(R.string.wallet_delete_dialog_active_hce_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultAmber
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = VaultRose),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_delete_card_button")
            ) {
                Text(
                    text = stringResource(R.string.wallet_delete_dialog_confirm),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.onSurfaceVariant),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .border(1.dp, ObsidianHairline, RoundedCornerShape(10.dp))
                    .testTag("cancel_delete_card_button")
            ) {
                Text(text = stringResource(R.string.wallet_delete_dialog_cancel))
            }
        }
    )
}
