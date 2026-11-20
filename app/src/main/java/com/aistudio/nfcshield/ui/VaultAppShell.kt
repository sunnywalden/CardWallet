package com.aistudio.nfcshield.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.nfcshield.FirebaseBootstrap
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.auth.GoogleAuthHelper
import com.aistudio.nfcshield.auth.authStateFlow
import com.aistudio.nfcshield.data.local.LocalVaultStore
import com.aistudio.nfcshield.data.model.UiState
import com.aistudio.nfcshield.data.remote.FirestoreVaultRepository
import com.aistudio.nfcshield.nfc.HceAidManager
import com.aistudio.nfcshield.nfc.HceQuickTileService
import com.aistudio.nfcshield.nfc.HceRuntimeBus
import com.aistudio.nfcshield.ui.components.NfcLiveStatusBanner
import com.aistudio.nfcshield.ui.components.openSystemNfcSettings
import com.aistudio.nfcshield.ui.components.rememberLiveNfcStatus
import com.aistudio.nfcshield.ui.screens.AuthScreen
import com.aistudio.nfcshield.ui.screens.CardDetailScreen
import com.aistudio.nfcshield.ui.screens.NfcDebugScreen
import com.aistudio.nfcshield.ui.screens.NfcScanScreen
import com.aistudio.nfcshield.ui.screens.SecuritySettingsScreen
import com.aistudio.nfcshield.ui.screens.VaultLockScreen
import com.aistudio.nfcshield.ui.screens.WalletHomeScreen
import com.aistudio.nfcshield.ui.theme.ObsidianHairline
import com.aistudio.nfcshield.ui.viewmodel.VaultGateState
import com.aistudio.nfcshield.ui.viewmodel.VaultViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

/**
 * Streamlined 3-tab Silicon Valley Information Architecture:
 * Wallet (卡包), Scan (扫描), Settings (设置).
 * NFC Diagnostics Console is nested as a dedicated sub-screen inside Settings.
 */
private enum class VaultTab(val tag: String) {
    WALLET("tab_wallet"),
    SCAN("tab_scan"),
    SECURITY("tab_security")
}

@Composable
fun VaultAppRoot() {
    val context = LocalContext.current
    val firebaseReady = remember(context) {
        FirebaseBootstrap.ensureInitialized(context) || FirebaseApp.getApps(context).isNotEmpty()
    }
    if (!firebaseReady) {
        MissingFirebaseConfigScreen()
        return
    }
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedVaultHost(
                uid = user.uid,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedVaultHost(
    uid: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VaultViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VaultViewModel(
                    repository = FirestoreVaultRepository(db),
                    localStore = LocalVaultStore(app),
                    currentUserId = uid
                )
            }
        }
    )

    val gateState by viewModel.vaultGateState.collectAsStateWithLifecycle()
    val cardsState by viewModel.cardsState.collectAsStateWithLifecycle()
    val securityPrefs by viewModel.securityPrefs.collectAsStateWithLifecycle()
    val recentHceEvents by viewModel.recentHceEvents.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val biometricSealMode by viewModel.biometricSealMode.collectAsStateWithLifecycle()

    val liveNfcStatus by rememberLiveNfcStatus()
    var manualNfcStatusOverride by remember { mutableStateOf<com.aistudio.nfcshield.nfc.NfcHardwareStatus?>(null) }
    val nfcStatus = manualNfcStatusOverride ?: liveNfcStatus
    LaunchedEffect(liveNfcStatus) {
        manualNfcStatusOverride = null
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (viewModel.onAppResumed()) {
                        HceQuickTileService.requestTileSync(context)
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (viewModel.onAppBackgrounded()) {
                        HceQuickTileService.requestTileSync(context)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val performSignOut: () -> Unit = {
        GoogleAuthHelper.signOut(
            credentialManager = credentialManager,
            scope = scope,
            onPreSignOutCleanup = { viewModel.prepareForSignOut() }
        )
    }

    val syncHceAid: (String?) -> Unit = { aidHex ->
        HceAidManager.syncDynamicAid(context, aidHex)
        manualNfcStatusOverride = HceAidManager.getHardwareStatus(context)
        HceQuickTileService.requestTileSync(context)
    }

    val openNfcSettings: () -> Unit = {
        if (!openSystemNfcSettings(context)) {
            scope.launch { snackbarHostState.showSnackbar("无法打开系统设置，请手动前往设置开启 NFC") }
        }
    }

    val openAppPermissionSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        val opened = runCatching { context.startActivity(intent) }.isSuccess || runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }.isSuccess
        if (!opened) {
            scope.launch { snackbarHostState.showSnackbar("无法打开应用详情设置") }
        }
    }

    if (gateState !is VaultGateState.Unlocked) {
        VaultLockScreen(
            gateState = gateState,
            biometricPrefEnabled = securityPrefs.biometricEnabled,
            userEmail = userEmail,
            onSetupPin = { pin, enableBio, onErr ->
                viewModel.setupVaultPin(pin, enableBio) { res ->
                    if (res.isFailure) {
                        onErr(res.exceptionOrNull()?.localizedMessage ?: "初始化失败")
                    }
                }
            },
            onSubmitUnlockPin = { pin ->
                val locked = gateState as? VaultGateState.Locked
                if (locked != null) {
                    viewModel.unlockWithPin(pin, locked.config)
                }
            },
            onPrepareBiometricChallenge = { onReady ->
                viewModel.prepareBiometricUnlock(onReady)
            },
            onBiometricCryptoSuccess = { cipher, sealedDekBase64, onFallbackMsg ->
                viewModel.unlockWithBiometricCryptoCipher(cipher, sealedDekBase64, onFallbackMsg)
            },
            onBiometricSuccess = { onFallbackMsg ->
                viewModel.unlockWithBiometric(onFallbackMsg)
            },
            onClearError = { viewModel.clearPinError() },
            onSignOut = performSignOut
        )
        return
    }

    var activeTab by remember { mutableStateOf(VaultTab.WALLET) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    var showNfcDebugScreen by remember { mutableStateOf(false) }
    var debugReaderModeEnabled by remember { mutableStateOf(true) }
    var physicalScanRequestNonce by remember { mutableIntStateOf(0) }
    val scannedTagLogs by HceRuntimeBus.scannedTagLogs.collectAsStateWithLifecycle()

    val selectedCard = remember(cardsState, selectedCardId) {
        val id = selectedCardId ?: return@remember null
        (cardsState as? UiState.Success)?.data?.firstOrNull { it.id == id }
    }

    val isInSubScreen = selectedCard != null || showNfcDebugScreen

    DisposableEffect(activeTab, selectedCard, showNfcDebugScreen, debugReaderModeEnabled) {
        val inScan = activeTab == VaultTab.SCAN && !isInSubScreen
        val inDebugReader = showNfcDebugScreen && debugReaderModeEnabled && selectedCard == null
        HceRuntimeBus.setScanTabActive(inScan)
        HceRuntimeBus.setReaderModeActive(inScan || inDebugReader)
        onDispose {
            HceRuntimeBus.setScanTabActive(false)
            HceRuntimeBus.setReaderModeActive(false)
        }
    }

    if (showNfcDebugScreen && selectedCard == null) {
        BackHandler { showNfcDebugScreen = false }
    } else if (activeTab != VaultTab.WALLET && selectedCard == null) {
        BackHandler { activeTab = VaultTab.WALLET }
    }

    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = colorScheme.background,
            contentColor = colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && !isInSubScreen) {
                    Column(
                        modifier = Modifier
                            .background(colorScheme.surface)
                            .navigationBarsPadding()
                    ) {
                        HorizontalDivider(thickness = 1.dp, color = ObsidianHairline)
                        NavigationBar(
                            containerColor = colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            tonalElevation = 0.dp
                        ) {
                            VaultTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                val (label, filledIcon, outlinedIcon) = tabMetadata(tab)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) filledIcon else outlinedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colorScheme.primary,
                                        selectedTextColor = colorScheme.primary,
                                        indicatorColor = colorScheme.primaryContainer,
                                        unselectedIconColor = colorScheme.onSurfaceVariant,
                                        unselectedTextColor = colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isInSubScreen) {
                    NavigationRail(
                        containerColor = colorScheme.surface,
                        contentColor = colorScheme.onSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        VaultTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            val (label, filledIcon, outlinedIcon) = tabMetadata(tab)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) filledIcon else outlinedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colorScheme.primary,
                                    selectedTextColor = colorScheme.primary,
                                    indicatorColor = colorScheme.primaryContainer,
                                    unselectedIconColor = colorScheme.onSurfaceVariant,
                                    unselectedTextColor = colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                    VerticalDivider(thickness = 1.dp, color = ObsidianHairline)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    // Always-available, whisper-clean top NFC live status monitor across primary tabs
                    if (!isInSubScreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            NfcLiveStatusBanner(
                                nfcStatus = nfcStatus,
                                onOpenNfcSettings = openNfcSettings,
                                modifier = Modifier.widthIn(max = 720.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        when {
                            selectedCard != null -> {
                                CardDetailScreen(
                                    card = selectedCard,
                                    onBack = { selectedCardId = null },
                                    onToggleHce = { card -> viewModel.toggleActiveHce(card, syncHceAid) },
                                    onUpdateCard = { existing, label, cat, accent, secrets, onDone ->
                                        viewModel.updateCard(
                                            existing = existing,
                                            label = label,
                                            category = cat,
                                            accentColor = accent,
                                            hceSupported = existing.hceSupported,
                                            secrets = secrets,
                                            onSyncAid = syncHceAid,
                                            onResult = onDone
                                        )
                                    },
                                    onDeleteCard = { card ->
                                        viewModel.deleteCard(card, syncHceAid) {
                                            selectedCardId = null
                                        }
                                    },
                                    onShowMessage = { msg ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                )
                            }

                            showNfcDebugScreen -> {
                                NfcDebugScreen(
                                    nfcStatus = nfcStatus,
                                    readerModeEnabled = debugReaderModeEnabled,
                                    tagLogs = scannedTagLogs,
                                    hceEvents = recentHceEvents,
                                    onBack = { showNfcDebugScreen = false },
                                    onToggleReaderMode = { debugReaderModeEnabled = it },
                                    onRefreshStatus = {
                                        manualNfcStatusOverride = HceAidManager.getHardwareStatus(context)
                                    },
                                    onOpenNfcSettings = openNfcSettings,
                                    onOpenAppPermissionSettings = openAppPermissionSettings,
                                    onClearLogs = { HceRuntimeBus.clearDebugLogs() },
                                    onShowMessage = { msg ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                )
                            }

                            else -> {
                                when (activeTab) {
                                    VaultTab.WALLET -> WalletHomeScreen(
                                        cardsState = cardsState,
                                        nfcStatus = nfcStatus,
                                        recentTapEvents = recentHceEvents,
                                        onCardClick = { card -> selectedCardId = card.id },
                                        onToggleHce = { card -> viewModel.toggleActiveHce(card, syncHceAid) },
                                        onNavigateToScan = {
                                            physicalScanRequestNonce++
                                            activeTab = VaultTab.SCAN
                                        },
                                        onOpenNfcSettings = openNfcSettings,
                                        onLockVaultNow = { viewModel.lockVaultNow() }
                                    )

                                    VaultTab.SCAN -> NfcScanScreen(
                                        nfcStatus = nfcStatus,
                                        physicalScanRequestNonce = physicalScanRequestNonce,
                                        onOpenNfcSettings = openNfcSettings,
                                        onSaveCard = { label, cat, accent, tech, hceSupported, activateHce, secrets, onDone ->
                                            viewModel.saveNewCard(
                                                label = label,
                                                category = cat,
                                                accentColor = accent,
                                                primaryTech = tech,
                                                hceSupported = hceSupported,
                                                activateHceNow = activateHce,
                                                secrets = secrets,
                                                onSyncAid = syncHceAid,
                                                onResult = onDone
                                            )
                                        },
                                        onNavigateBackToWallet = { activeTab = VaultTab.WALLET }
                                    )

                                    VaultTab.SECURITY -> SecuritySettingsScreen(
                                        userEmail = userEmail,
                                        prefs = securityPrefs,
                                        biometricSealMode = biometricSealMode,
                                        nfcStatus = nfcStatus,
                                        recentEvents = recentHceEvents,
                                        onToggleBiometric = { viewModel.updateBiometricPreference(it) },
                                        onChangeAutoLockMinutes = { viewModel.updateAutoLockMinutes(it) },
                                        onToggleRequireUnlockForHce = { viewModel.updateRequireUnlockForHce(it) },
                                        onChangePin = { oldPin, newPin, onDone ->
                                            viewModel.changeVaultPin(oldPin, newPin, onDone)
                                        },
                                        onLockVaultNow = {
                                            viewModel.lockVaultNow()
                                            HceQuickTileService.requestTileSync(context)
                                        },
                                        onSignOut = performSignOut,
                                        onOpenNfcDebug = { showNfcDebugScreen = true },
                                        onAddQuickTile = {
                                            HceQuickTileService.requestAddTileToSystem(context) { msg ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        }
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
private fun tabMetadata(tab: VaultTab) = when (tab) {
    VaultTab.WALLET -> Triple(
        stringResource(R.string.nav_wallet),
        Icons.Filled.CreditCard,
        Icons.Outlined.CreditCard
    )
    VaultTab.SCAN -> Triple(
        stringResource(R.string.nav_scan),
        Icons.Filled.Nfc,
        Icons.Outlined.Nfc
    )
    VaultTab.SECURITY -> Triple(
        stringResource(R.string.nav_security),
        Icons.Filled.Tune,
        Icons.Outlined.Tune
    )
}

@Composable
private fun MissingFirebaseConfigScreen() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "缺少 Firebase 配置",
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onBackground
            )
            Text(
                text = "当前安装包未注入 google-services.json，无法连接账号与云端保险库。请联系构建方提供完整配置后重新打包。",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
