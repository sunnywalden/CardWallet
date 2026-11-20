package com.aistudio.nfcshield.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.data.model.HceTapEvent
import com.aistudio.nfcshield.data.model.UiState
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.ui.components.resolvedIcon
import com.aistudio.nfcshield.ui.components.toColor
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultAmberContainer
import com.aistudio.nfcshield.ui.theme.VaultBorder
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultCyanContainer
import com.aistudio.nfcshield.ui.theme.VaultObsidian
import com.aistudio.nfcshield.ui.theme.VaultRose
import com.aistudio.nfcshield.ui.theme.VaultSurface
import com.aistudio.nfcshield.ui.theme.VaultSurfaceElevated
import com.aistudio.nfcshield.ui.theme.VaultTextPrimary
import com.aistudio.nfcshield.ui.theme.VaultTextSecondary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeCardFilter {
    ALL,
    HCE_EMULATED,
    PHYSICAL_READ
}

@Composable
fun WalletHomeScreen(
    cardsState: UiState<List<DecryptedAccessCard>>,
    nfcStatus: NfcHardwareStatus,
    recentTapEvents: List<HceTapEvent>,
    onCardClick: (DecryptedAccessCard) -> Unit,
    onToggleHce: (DecryptedAccessCard) -> Unit,
    onNavigateToScan: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onLockVaultNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HomeCardFilter.ALL) }
    val revealedCardIds = remember { mutableSetOf<String>() }
    var initialLoadBatchPending by remember { mutableStateOf(true) }

    val cardCount = (cardsState as? UiState.Success)?.data?.size ?: 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VaultObsidian)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                // Clean Minimal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.wallet_header_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = VaultTextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = VaultCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (cardCount > 0) {
                                    "共 $cardCount 张门禁凭证 · AES-256-GCM 保护"
                                } else {
                                    "AES-256-GCM 云端同步已保护"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = VaultTextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cardCount > 0) {
                            IconButton(
                                onClick = {
                                    searchExpanded = !searchExpanded
                                    if (!searchExpanded) searchQuery = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("wallet_toggle_search_button")
                            ) {
                                Icon(
                                    imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.wallet_toggle_search),
                                    tint = if (searchExpanded) VaultCyan else VaultTextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onLockVaultNow,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lock_vault_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.wallet_lock_now),
                                tint = VaultCyan
                            )
                        }
                    }
                }

                val statePhase = when (cardsState) {
                    is UiState.Loading -> 0
                    is UiState.Error -> 1
                    is UiState.Success -> 2
                }

                Crossfade(
                    targetState = statePhase,
                    animationSpec = tween(durationMillis = 220),
                    label = "wallet_state_crossfade",
                    modifier = Modifier.weight(1f)
                ) { phase ->
                    when (phase) {
                        0 -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = VaultCyan)
                            }
                        }

                        1 -> {
                            val errMsg = (cardsState as? UiState.Error)?.message ?: "加载失败"
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errMsg,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VaultRose,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        else -> {
                            val allCards = (cardsState as? UiState.Success)?.data.orEmpty()
                            val activeHceCard = allCards.firstOrNull { it.activeHce }
                            val hceCount = remember(allCards) { allCards.count { it.hceSupported } }
                            val physicalCount = remember(allCards) { allCards.count { !it.hceSupported } }

                            LaunchedEffect(allCards.isNotEmpty()) {
                                if (allCards.isNotEmpty() && initialLoadBatchPending) {
                                    delay(300L)
                                    initialLoadBatchPending = false
                                }
                            }

                        val filteredCards = remember(allCards, searchQuery, selectedFilter) {
                            allCards.filter { card ->
                                val matchesFilter = when (selectedFilter) {
                                    HomeCardFilter.ALL -> true
                                    HomeCardFilter.HCE_EMULATED -> card.hceSupported
                                    HomeCardFilter.PHYSICAL_READ -> !card.hceSupported
                                }
                                val q = searchQuery.trim()
                                val matchesQuery = q.isEmpty() ||
                                    card.label.contains(q, ignoreCase = true) ||
                                    card.secrets.uidHex.contains(q, ignoreCase = true) ||
                                    card.secrets.aidHex.contains(q, ignoreCase = true) ||
                                    card.secrets.notes.contains(q, ignoreCase = true)
                                matchesFilter && matchesQuery
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // NFC Disabled Advisory Banner
                            if (nfcStatus.nfcSupported && !nfcStatus.nfcEnabled) {
                                item(key = "nfc_disabled_banner") {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = VaultAmberContainer,
                                        border = BorderStroke(1.dp, VaultAmber.copy(alpha = 0.5f))
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
                                                    imageVector = Icons.Filled.WarningAmber,
                                                    contentDescription = null,
                                                    tint = VaultAmber
                                                )
                                                Text(
                                                    text = stringResource(R.string.scan_nfc_disabled),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = VaultTextPrimary
                                                )
                                            }
                                            TextButton(onClick = onOpenNfcSettings) {
                                                Text("去开启", color = VaultAmber, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Compact Active HCE Status Strip
                            item(key = "hce_status_deck") {
                                ActiveHceSummaryStrip(
                                    activeCard = activeHceCard,
                                    nfcStatus = nfcStatus,
                                    latestTap = recentTapEvents.firstOrNull(),
                                    onCardClick = { activeHceCard?.let(onCardClick) },
                                    onDeactivate = { activeHceCard?.let(onToggleHce) }
                                )
                            }

                            // Collapsible Search + Clean Read/Emulate Filter Chips
                            if (allCards.isNotEmpty()) {
                                item(key = "search_bar") {
                                    AnimatedVisibility(
                                        visible = searchExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = {
                                                Text("搜索卡名、UID、AID 或备注…", color = VaultTextSecondary)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Search,
                                                    contentDescription = null,
                                                    tint = VaultTextSecondary
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = VaultCyan,
                                                unfocusedBorderColor = VaultBorder,
                                                focusedContainerColor = VaultSurface,
                                                unfocusedContainerColor = VaultSurface,
                                                focusedTextColor = VaultTextPrimary,
                                                unfocusedTextColor = VaultTextPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("wallet_search_input")
                                        )
                                    }
                                }

                                item(key = "filter_chips") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item {
                                            ModeFilterChip(
                                                selected = selectedFilter == HomeCardFilter.ALL,
                                                label = "${stringResource(R.string.wallet_filter_all)} (${allCards.size})",
                                                onClick = { selectedFilter = HomeCardFilter.ALL },
                                                tag = "filter_all_chip"
                                            )
                                        }
                                        item {
                                            ModeFilterChip(
                                                selected = selectedFilter == HomeCardFilter.HCE_EMULATED,
                                                label = "${stringResource(R.string.wallet_filter_hce)} ($hceCount)",
                                                onClick = { selectedFilter = HomeCardFilter.HCE_EMULATED },
                                                tag = "filter_hce_chip"
                                            )
                                        }
                                        item {
                                            ModeFilterChip(
                                                selected = selectedFilter == HomeCardFilter.PHYSICAL_READ,
                                                label = "${stringResource(R.string.wallet_filter_physical)} ($physicalCount)",
                                                onClick = { selectedFilter = HomeCardFilter.PHYSICAL_READ },
                                                tag = "filter_physical_chip"
                                            )
                                        }
                                    }
                                }
                            }

                            if (allCards.isEmpty()) {
                                item(key = "empty_wallet") {
                                    StaggeredCardEntrance(
                                        cardId = "empty_wallet_placeholder",
                                        index = 0,
                                        alreadyRevealedIds = revealedCardIds,
                                        isInitialBatch = true
                                    ) {
                                        EmptyWalletCard(onNavigateToScan = onNavigateToScan)
                                    }
                                }
                            } else if (filteredCards.isEmpty()) {
                                item(key = "empty_search") {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = VaultSurface,
                                        border = BorderStroke(1.dp, VaultBorder)
                                    ) {
                                        Text(
                                            text = "当前筛选条件下暂无门禁卡",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = VaultTextSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(28.dp)
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(filteredCards, key = { _, card -> card.id }) { index, card ->
                                    StaggeredCardEntrance(
                                        cardId = card.id,
                                        index = index,
                                        alreadyRevealedIds = revealedCardIds,
                                        isInitialBatch = initialLoadBatchPending,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(durationMillis = 220),
                                            fadeOutSpec = tween(durationMillis = 160),
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    ) {
                                        VaultCardListRow(
                                            card = card,
                                            onClick = { onCardClick(card) },
                                            onToggleHce = { onToggleHce(card) }
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

        // Primary '+' Button anchored at bottom-end to trigger Physical NFC Scan Flow
        FloatingActionButton(
            onClick = onNavigateToScan,
            containerColor = VaultCyan,
            contentColor = VaultObsidian,
            shape = RoundedCornerShape(18.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(58.dp)
                .testTag("home_add_nfc_fab")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.wallet_fab_add_desc),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun StaggeredCardEntrance(
    cardId: String,
    index: Int,
    alreadyRevealedIds: MutableSet<String>,
    isInitialBatch: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shouldAnimate = remember(cardId) { alreadyRevealedIds.add(cardId) }
    val alphaAnim = remember(cardId) { Animatable(if (shouldAnimate) 0f else 1f) }
    val translationYDp = remember(cardId) { Animatable(if (shouldAnimate) 22f else 0f) }

    LaunchedEffect(cardId) {
        if (shouldAnimate) {
            val staggerDelay = if (isInitialBatch) (index.coerceAtMost(6) * 45L) else 0L
            if (staggerDelay > 0L) delay(staggerDelay)
            coroutineScope {
                launch {
                    alphaAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    translationYDp.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = alphaAnim.value
            translationY = translationYDp.value.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
private fun ModeFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VaultCyanContainer,
            selectedLabelColor = VaultCyan,
            containerColor = VaultSurface,
            labelColor = VaultTextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = VaultBorder,
            selectedBorderColor = VaultCyan.copy(alpha = 0.6f)
        ),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun VaultCardListRow(
    card: DecryptedAccessCard,
    onClick: () -> Unit,
    onToggleHce: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = card.accentColor.toColor()
    val borderColor = if (card.activeHce) accent else VaultBorder
    val rowBg = Brush.horizontalGradient(
        colors = listOf(
            accent.copy(alpha = if (card.activeHce) 0.14f else 0.06f),
            VaultSurface
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("access_card_${card.id}"),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(if (card.activeHce) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .background(rowBg)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left vertical accent indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (card.activeHce) accent else accent.copy(alpha = 0.5f))
            )

            // Category / Custom Icon Tile
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.15f))
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

            // Card Primary Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = VaultTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            card.activeHce -> accent.copy(alpha = 0.18f)
                            card.hceSupported -> VaultSurfaceElevated
                            else -> VaultObsidian.copy(alpha = 0.65f)
                        }
                    ) {
                        Text(
                            text = when {
                                card.activeHce -> "HCE 模拟中"
                                card.hceSupported -> "HCE 可模拟"
                                else -> "已读取物理卡"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                card.activeHce -> accent
                                card.hceSupported -> VaultCyan
                                else -> VaultTextSecondary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val identifierText = when {
                    card.decryptionFailed -> "密钥不匹配"
                    card.maskedUid.isNotBlank() -> card.maskedUid
                    card.secrets.aidHex.isNotBlank() -> "AID ${card.secrets.aidHex.take(10)}"
                    else -> "虚拟 HCE 凭证"
                }

                Text(
                    text = "$identifierText · ${card.primaryTech.name}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = if (card.decryptionFailed) VaultRose else VaultTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Action Pill
            if (card.hceSupported) {
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onToggleHce)
                        .testTag("toggle_hce_${card.id}"),
                    shape = CircleShape,
                    color = if (card.activeHce) accent.copy(alpha = 0.22f) else VaultSurfaceElevated,
                    border = BorderStroke(1.dp, if (card.activeHce) accent else VaultBorder)
                ) {
                    Text(
                        text = if (card.activeHce) "停用" else "模拟",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (card.activeHce) accent else VaultTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = VaultObsidian.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, VaultBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = VaultTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "只读",
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveHceSummaryStrip(
    activeCard: DecryptedAccessCard?,
    nfcStatus: NfcHardwareStatus,
    latestTap: HceTapEvent?,
    onCardClick: () -> Unit,
    onDeactivate: () -> Unit
) {
    val isReady = activeCard != null && nfcStatus.nfcEnabled && nfcStatus.hceSupported
    val borderColor = if (activeCard != null) VaultCyan.copy(alpha = 0.7f) else VaultBorder

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = VaultSurfaceElevated,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    Surface(
                        shape = CircleShape,
                        color = if (activeCard != null) VaultCyan.copy(alpha = 0.16f) else VaultSurface
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Contactless,
                            contentDescription = null,
                            tint = if (activeCard != null) VaultCyan else VaultTextSecondary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (activeCard != null) "正在模拟 · ${activeCard.label}" else "HCE 模拟卡槽待命",
                            style = MaterialTheme.typography.titleMedium,
                            color = VaultTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                activeCard == null -> stringResource(R.string.wallet_active_hce_none)
                                !nfcStatus.hceSupported -> "当前设备无 HCE 硬件，仅支持云端凭证管理"
                                !nfcStatus.nfcEnabled -> "请先在系统设置开启 NFC 后再贴近读卡器"
                                else -> "AID ${activeCard.secrets.aidHex.ifBlank { "F04E464353484945" }} · 已刷 ${activeCard.emulationCount} 次"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isReady) VaultCyan else VaultTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (activeCard != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = onCardClick) {
                            Text("详情", color = VaultCyan)
                        }
                        TextButton(onClick = onDeactivate) {
                            Text("停用", color = VaultTextSecondary)
                        }
                    }
                }
            }

            if (latestTap != null) {
                Text(
                    text = "最近 APDU: ${latestTap.cardLabel} · SW=${latestTap.statusWordHex} (${if (latestTap.accepted) "通过" else "拒绝"})",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (latestTap.accepted) VaultCyan else VaultAmber
                )
            }
        }
    }
}

@Composable
private fun EmptyWalletCard(onNavigateToScan: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = VaultSurface,
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = VaultCyanContainer,
                border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = null,
                    tint = VaultCyan,
                    modifier = Modifier
                        .padding(18.dp)
                        .size(36.dp)
                )
            }
            Text(
                text = stringResource(R.string.wallet_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = VaultTextPrimary
            )
            Text(
                text = stringResource(R.string.wallet_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultTextSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onNavigateToScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultCyan,
                    contentColor = VaultObsidian
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_wallet_scan_button")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.wallet_add_card),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
