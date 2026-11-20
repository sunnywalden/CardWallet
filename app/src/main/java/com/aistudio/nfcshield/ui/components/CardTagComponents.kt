package com.aistudio.nfcshield.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.data.model.MAX_TAGS_PER_CARD
import com.aistudio.nfcshield.data.model.MAX_TAG_LENGTH
import com.aistudio.nfcshield.data.model.PRESET_CARD_TAGS
import com.aistudio.nfcshield.data.model.normalizeCardTags
import com.aistudio.nfcshield.ui.theme.ObsidianHairline
import com.aistudio.nfcshield.ui.theme.ObsidianHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultCyan

/**
 * Horizontal chip group on the home wallet screen for fast filtering across multiple cards by tag.
 */
@Composable
fun CardTagFilterRow(
    tagCounts: List<Pair<String, Int>>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wallet_tag_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "tag_filter_all") {
            val allSelected = selectedTag == null
            FilterChip(
                selected = allSelected,
                onClick = { onSelectTag(null) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.wallet_tag_filter_all),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VaultCyan.copy(alpha = 0.16f),
                    selectedLabelColor = VaultCyan,
                    selectedLeadingIconColor = VaultCyan,
                    containerColor = colorScheme.surface,
                    labelColor = colorScheme.onSurfaceVariant,
                    iconColor = colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = allSelected,
                    borderColor = ObsidianHairline,
                    selectedBorderColor = VaultCyan.copy(alpha = 0.45f)
                ),
                modifier = Modifier.testTag("tag_filter_all_chip")
            )
        }

        if (tagCounts.isEmpty()) {
            item(key = "tag_filter_empty_hint") {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, ObsidianHairline)
                ) {
                    Text(
                        text = stringResource(R.string.wallet_tag_empty_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        } else {
            items(tagCounts, key = { (tag, _) -> "tag_chip_$tag" }) { (tag, count) ->
                val isSelected = selectedTag == tag
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectTag(if (isSelected) null else tag) },
                    label = {
                        Text(
                            text = "#$tag $count",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultCyan.copy(alpha = 0.18f),
                        selectedLabelColor = VaultCyan,
                        containerColor = colorScheme.surface,
                        labelColor = colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = ObsidianHairline,
                        selectedBorderColor = VaultCyan.copy(alpha = 0.50f)
                    ),
                    modifier = Modifier.testTag("tag_filter_chip_$tag")
                )
            }
        }
    }
}

/**
 * Compact Obsidian tag badges rendered inside card list rows for quick visual scanning.
 */
@Composable
fun CardTagPillsRow(
    tags: List<String>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    val visible = tags.take(3)
    val overflowCount = (tags.size - visible.size).coerceAtLeast(0)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEach { tag ->
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (overflowCount > 0) {
            Text(
                text = "+$overflowCount",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Reusable tag selector & custom tag input for scan creation and card detail editing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardTagEditor(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var customTagInput by remember { mutableStateOf("") }
    val normalizedCurrent = remember(tags) { normalizeCardTags(tags) }
    val customOnlyTags = remember(normalizedCurrent) {
        normalizedCurrent.filterNot { it in PRESET_CARD_TAGS }
    }
    val canAddMore = normalizedCurrent.size < MAX_TAGS_PER_CARD

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.card_tags_section_title),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = "${normalizedCurrent.size}/$MAX_TAGS_PER_CARD",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PRESET_CARD_TAGS.forEach { preset ->
                val selected = preset in normalizedCurrent
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = if (selected) {
                            normalizedCurrent - preset
                        } else if (canAddMore) {
                            normalizeCardTags(normalizedCurrent + preset)
                        } else {
                            normalizedCurrent
                        }
                        onTagsChange(updated)
                    },
                    label = {
                        Text(
                            text = "#$preset",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultCyan.copy(alpha = 0.18f),
                        selectedLabelColor = VaultCyan,
                        containerColor = colorScheme.surfaceContainer,
                        labelColor = colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = ObsidianHairline,
                        selectedBorderColor = VaultCyan.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.testTag("card_tag_preset_$preset")
                )
            }

            customOnlyTags.forEach { customTag ->
                InputChip(
                    selected = true,
                    onClick = { onTagsChange(normalizedCurrent - customTag) },
                    label = {
                        Text(
                            text = "#$customTag",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.card_tag_remove_cd, customTag),
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = CircleShape,
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = VaultCyan.copy(alpha = 0.18f),
                        selectedLabelColor = VaultCyan,
                        selectedTrailingIconColor = VaultCyan
                    ),
                    border = InputChipDefaults.inputChipBorder(
                        enabled = true,
                        selected = true,
                        borderColor = ObsidianHairline,
                        selectedBorderColor = VaultCyan.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.testTag("card_tag_custom_chip_$customTag")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customTagInput,
                onValueChange = { if (it.length <= MAX_TAG_LENGTH) customTagInput = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.card_tag_input_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                enabled = canAddMore,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ObsidianHairlineStrong,
                    unfocusedBorderColor = ObsidianHairline,
                    focusedContainerColor = colorScheme.surfaceContainer,
                    unfocusedContainerColor = colorScheme.surfaceContainer,
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_tag_custom_input")
            )

            OutlinedButton(
                onClick = {
                    val cleaned = normalizeCardTags(listOf(customTagInput)).firstOrNull()
                    if (!cleaned.isNullOrEmpty() && canAddMore) {
                        onTagsChange(normalizeCardTags(normalizedCurrent + cleaned))
                        customTagInput = ""
                    }
                },
                enabled = canAddMore && customTagInput.trim().trimStart('#').isNotEmpty(),
                border = BorderStroke(1.dp, ObsidianHairlineStrong),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("card_tag_add_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.card_tag_add_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
