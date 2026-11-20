package com.aistudio.nfcshield.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.aistudio.nfcshield.ui.components.AccessCardVisual
import com.aistudio.nfcshield.ui.components.DeleteCardConfirmDialog
import com.aistudio.nfcshield.ui.components.SwipeToDeleteCardContainer
import com.aistudio.nfcshield.ui.components.resolvedIcon
import com.aistudio.nfcshield.ui.components.toColor
import com.aistudio.nfcshield.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.nfcshield.ui.theme.ObsidianHairline
import com.aistudio.nfcshield.ui.theme.ObsidianHairlineStrong
import com.aistudio.nfcshield.ui.theme.VaultAmber
import com.aistudio.nfcshield.ui.theme.VaultCyan
import com.aistudio.nfcshield.ui.theme.VaultRose
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
    onDeleteCard: (DecryptedAccessCard) -> Unit = {},
    onNavigateToScan: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onLockVaultNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HomeCardFilter.ALL) }
    var pendingDeleteCard by remember { mutableStateOf<DecryptedAccessCard?>(null) }
    val revealedCardIds = remember { mutableSetOf<String>() }
    var initialLoadBatchPending by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val hasScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }
    val headerDividerAlpha by animateFloatAsState(
        targetValue = if (hasScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "wallet_header_divider_alpha"
    )

    val cardCount = (cardsState as? UiState.Success)?.data?.size ?: 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
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
                // Restrained Obsidian Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wallet_header_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = if (cardCount > 0) {
                                "$cardCount 张凭证 · AES-256-GCM 零知识加密"
                            } else {
                                "AES-256-GCM 零知识端到端保护"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (cardCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = if (searchExpanded) colorScheme.surfaceContainer else colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (searchExpanded) ObsidianHairlineStrong else ObsidianHairline
                                )
                            ) {
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
                                        tint = if (searchExpanded) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, ObsidianHairline)
                        ) {
                            IconButton(
                                onClick = onLockVaultNow,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("lock_vault_now_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = stringResource(R.string.wallet_lock_now),
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // Subtle hairline divider that fades in smoothly as the LazyColumn scrolls
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ObsidianHairline,
                    modifier = Modifier.alpha(headerDividerAlpha)
                )

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
                                CircularProgressIndicator(
                                    color = colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
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
                            // Focal Hero Card: active HCE card first, otherwise first card in wallet
                            val focalCard = remember(allCards) {
                                allCards.firstOrNull { it.activeHce } ?: allCards.firstOrNull()
                            }
                            val hceCount = remember(allCards) { allCards.count { it.hceSupported } }
                            val physicalCount = remember(allCards) { allCards.count { !it.hceSupported } }

                            LaunchedEffect(allCards.isNotEmpty()) {
                                if (allCards.isNotEmpty() && initialLoadBatchPending) {
                                    delay(280L)
                                    initialLoadBatchPending = false
                                }
                            }

                            val isFilteringOrSearching = searchQuery.isNotBlank() || selectedFilter != HomeCardFilter.ALL
                            val listCards = remember(allCards, focalCard, searchQuery, selectedFilter) {
                                val basePool = if (isFilteringOrSearching) {
                                    allCards
                                } else {
                                    allCards.filterNot { it.id == focalCard?.id }
                                }
                                basePool.filter { card ->
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
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("wallet_lazy_column"),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 8.dp,
                                    bottom = 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Top Hero Focal Card with translucent glow & smooth scroll parallax transition
                                if (focalCard != null) {
                                    item(key = "wallet_hero_focal_stage") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    // Read LazyListState strictly in draw phase for 60/120fps scroll fluidity
                                                    val scrollOffsetPx = if (listState.firstVisibleItemIndex == 0) {
                                                        listState.firstVisibleItemScrollOffset.toFloat()
                                                    } else {
                                                        700f
                                                    }
                                                    val progress = (scrollOffsetPx / 520f).coerceIn(0f, 1f)
                                                    val scale = 1f - (0.055f * progress)
                                                    scaleX = scale
                                                    scaleY = scale
                                                    alpha = 1f - (0.38f * progress)
                                                    translationY = scrollOffsetPx * 0.24f
                                                }
                                                .testTag("wallet_hero_focal_card")
                                        ) {
                                            HeroFocalCardStage(
                                                focalCard = focalCard,
                                                nfcStatus = nfcStatus,
                                                latestTap = recentTapEvents.firstOrNull(),
                                                onCardClick = { onCardClick(focalCard) },
                                                onToggleHce = { onToggleHce(focalCard) },
                                                onOpenNfcSettings = onOpenNfcSettings
                                            )
                                        }
                                    }
                                }

                                // 2. Collapsible Search + Crisp Section Header & Filter Pills
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
                                                    Text(
                                                        text = "搜索卡名、UID、AID 或备注…",
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Filled.Search,
                                                        contentDescription = null,
                                                        tint = colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(14.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = ObsidianHairlineStrong,
                                                    unfocusedBorderColor = ObsidianHairline,
                                                    focusedContainerColor = colorScheme.surface,
                                                    unfocusedContainerColor = colorScheme.surface,
                                                    focusedTextColor = colorScheme.onSurface,
                                                    unfocusedTextColor = colorScheme.onSurface
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("wallet_search_input")
                                            )
                                        }
                                    }

                                    item(key = "section_header_and_filters") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                                .testTag("wallet_card_list_section"),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isFilteringOrSearching) "筛选结果" else "更多门禁卡",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = colorScheme.onBackground
                                                )
                                                Text(
                                                    text = if (isFilteringOrSearching) {
                                                        "${listCards.size} / ${allCards.size}"
                                                    } else {
                                                        "${listCards.size} 张"
                                                    },
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                item {
                                                    ModeFilterChip(
                                                        selected = selectedFilter == HomeCardFilter.ALL,
                                                        label = "${stringResource(R.string.wallet_filter_all)} ${allCards.size}",
                                                        onClick = { selectedFilter = HomeCardFilter.ALL },
                                                        tag = "filter_all_chip"
                                                    )
                                                }
                                                item {
                                                    ModeFilterChip(
                                                        selected = selectedFilter == HomeCardFilter.HCE_EMULATED,
                                                        label = "${stringResource(R.string.wallet_filter_hce)} $hceCount",
                                                        onClick = { selectedFilter = HomeCardFilter.HCE_EMULATED },
                                                        tag = "filter_hce_chip"
                                                    )
                                                }
                                                item {
                                                    ModeFilterChip(
                                                        selected = selectedFilter == HomeCardFilter.PHYSICAL_READ,
                                                        label = "${stringResource(R.string.wallet_filter_physical)} $physicalCount",
                                                        onClick = { selectedFilter = HomeCardFilter.PHYSICAL_READ },
                                                        tag = "filter_physical_chip"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Below-Hero Clean Card List (or Empty States)
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
                                } else if (listCards.isEmpty()) {
                                    item(key = "empty_list_hint") {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = colorScheme.surface,
                                            border = BorderStroke(1.dp, ObsidianHairline)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(
                                                        enabled = !isFilteringOrSearching,
                                                        onClick = onNavigateToScan
                                                    )
                                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isFilteringOrSearching) {
                                                        "当前筛选条件下暂无门禁卡"
                                                    } else {
                                                        "主卡已就绪，点击右下角 + 可继续添加门禁卡"
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (!isFilteringOrSearching) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Add,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    itemsIndexed(listCards, key = { _, card -> card.id }) { index, card ->
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
                                            SwipeToDeleteCardContainer(
                                                cardId = card.id,
                                                onRequestDelete = { pendingDeleteCard = card }
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
        }

        // Crisp Monochrome Obsidian Primary '+' FAB (#E0E0E0 on #0A0A0A)
        FloatingActionButton(
            onClick = onNavigateToScan,
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(56.dp)
                .testTag("home_add_nfc_fab")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.wallet_fab_add_desc),
                modifier = Modifier.size(26.dp)
            )
        }

        pendingDeleteCard?.let { targetCard ->
            DeleteCardConfirmDialog(
                card = targetCard,
                onDismiss = { pendingDeleteCard = null },
                onConfirmDelete = {
                    pendingDeleteCard = null
                    onDeleteCard(targetCard)
                }
            )
        }
    }
}

@Composable
private fun HeroFocalCardStage(
    focalCard: DecryptedAccessCard,
    nfcStatus: NfcHardwareStatus,
    latestTap: HceTapEvent?,
    onCardClick: () -> Unit,
    onToggleHce: () -> Unit,
    onOpenNfcSettings: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Subtle focal label row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (focalCard.activeHce) VaultCyan else colorScheme.onSurfaceVariant)
                )
                Text(
                    text = if (focalCard.activeHce) "焦点主卡 · HCE 模拟就绪" else "焦点主卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = focalCard.primaryTech.name,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        // Smooth transition whenever the active focal card changes
        AnimatedContent(
            targetState = focalCard,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260)))
                    .togetherWith(fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "hero_focal_card_transition"
        ) { card ->
            AccessCardVisual(
                card = card,
                onClick = onCardClick,
                onToggleHce = onToggleHce,
                heroGlow = true
            )
        }

        // Whisper-clean telemetry micro-bar beneath the hero focal card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surface,
            border = BorderStroke(1.dp, ObsidianHairline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val isReady = focalCard.activeHce && nfcStatus.nfcEnabled && nfcStatus.hceSupported
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isReady -> VaultCyan
                                    !nfcStatus.nfcEnabled && nfcStatus.nfcSupported -> VaultAmber
                                    else -> colorScheme.onSurfaceVariant
                                }
                            )
                    )
                    Text(
                        text = when {
                            latestTap != null && focalCard.activeHce ->
                                "APDU SW=${latestTap.statusWordHex} · ${if (latestTap.accepted) "刷卡通过" else "已拒绝"}"
                            !focalCard.activeHce && focalCard.hceSupported ->
                                "轻点主卡「启用模拟」即可激活 HCE 感应刷卡"
                            !focalCard.hceSupported ->
                                "此卡片为只读物理凭证 · 轻点查看加密详情"
                            !nfcStatus.hceSupported ->
                                "设备无 HCE 硬件 · 云端加密管理模式"
                            !nfcStatus.nfcEnabled ->
                                "请先开启系统 NFC 后贴近读卡器"
                            else ->
                                "贴近读卡器即可刷卡 · AID ${focalCard.secrets.aidHex.take(8)}"
                        },
                        fontFamily = if (latestTap != null && focalCard.activeHce) JetBrainsMonoFontFamily else null,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (latestTap != null && !latestTap.accepted) VaultAmber else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!nfcStatus.nfcEnabled && nfcStatus.nfcSupported) {
                    TextButton(onClick = onOpenNfcSettings) {
                        Text("开启 NFC", color = VaultAmber, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    TextButton(onClick = onCardClick) {
                        Text("详情", color = colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
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
    val translationYDp = remember(cardId) { Animatable(if (shouldAnimate) 18f else 0f) }

    LaunchedEffect(cardId) {
        if (shouldAnimate) {
            val staggerDelay = if (isInitialBatch) (index.coerceAtMost(6) * 40L) else 0L
            if (staggerDelay > 0L) delay(staggerDelay)
            coroutineScope {
                launch {
                    alphaAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
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
    val colorScheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.primary,
            selectedLabelColor = colorScheme.onPrimary,
            containerColor = colorScheme.surface,
            labelColor = colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ObsidianHairline,
            selectedBorderColor = Color.Transparent
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
    val colorScheme = MaterialTheme.colorScheme
    val accent = card.accentColor.toColor()
    val borderColor = if (card.activeHce) accent.copy(alpha = 0.45f) else ObsidianHairline

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("access_card_${card.id}"),
        shape = RoundedCornerShape(18.dp),
        color = if (card.activeHce) colorScheme.surfaceContainer else colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = card.resolvedIcon(),
                    contentDescription = card.category.displayName,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (card.activeHce) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(VaultCyan)
                        )
                    }
                }

                val identifierText = when {
                    card.decryptionFailed -> "密钥不匹配"
                    card.maskedUid.isNotBlank() -> card.maskedUid
                    card.secrets.aidHex.isNotBlank() -> "AID ${card.secrets.aidHex.take(10)}"
                    else -> "HCE 虚拟凭证"
                }

                Text(
                    text = "$identifierText · ${card.primaryTech.name}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = if (card.decryptionFailed) VaultRose else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (card.hceSupported) {
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onToggleHce)
                        .testTag("toggle_hce_${card.id}"),
                    shape = CircleShape,
                    color = if (card.activeHce) VaultCyan.copy(alpha = 0.16f) else colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (card.activeHce) VaultCyan.copy(alpha = 0.45f) else ObsidianHairlineStrong
                    )
                ) {
                    Text(
                        text = if (card.activeHce) "模拟中" else "模拟",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (card.activeHce) VaultCyan else colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, ObsidianHairline)
                ) {
                    Text(
                        text = "只读",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWalletCard(onNavigateToScan: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, ObsidianHairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainer)
                    .border(1.dp, ObsidianHairlineStrong, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = stringResource(R.string.wallet_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.wallet_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onNavigateToScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_wallet_scan_button")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.wallet_add_card),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
