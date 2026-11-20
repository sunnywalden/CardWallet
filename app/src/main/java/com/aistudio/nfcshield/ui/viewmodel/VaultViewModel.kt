package com.aistudio.nfcshield.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.nfcshield.crypto.VaultCryptoEngine
import com.aistudio.nfcshield.crypto.WrappedDekPayload
import com.aistudio.nfcshield.data.local.LocalSecurityPreferences
import com.aistudio.nfcshield.data.local.LocalVaultStore
import com.aistudio.nfcshield.data.local.PinLockoutState
import com.aistudio.nfcshield.data.model.BiometricSealMode
import javax.crypto.Cipher
import com.aistudio.nfcshield.data.model.ActiveHceCardConfig
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.DecryptedAccessCard
import com.aistudio.nfcshield.data.model.EncryptedCardDoc
import com.aistudio.nfcshield.data.model.HceTapEvent
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.data.model.UiState
import com.aistudio.nfcshield.data.model.VaultConfigDoc
import com.aistudio.nfcshield.data.remote.FirestoreVaultRepository
import com.aistudio.nfcshield.nfc.HceRuntimeBus
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultGateState {
    data object Loading : VaultGateState
    data object NeedsPinSetup : VaultGateState
    data class Locked(
        val config: VaultConfigDoc,
        val hasBiometricSealedKey: Boolean,
        val failedAttempts: Int = 0,
        val lockoutSecondsRemaining: Int = 0,
        val errorMessage: String? = null,
        val isBusy: Boolean = false
    ) : VaultGateState
    data object Unlocked : VaultGateState
    data class Error(val message: String) : VaultGateState
}

sealed interface BiometricUnlockChallenge {
    data class CryptoObjectPrompt(
        val cipher: Cipher,
        val sealedDekBase64: String,
        val deviceCredentialAllowed: Boolean
    ) : BiometricUnlockChallenge
    data object LegacyPrompt : BiometricUnlockChallenge
    data class RequiresPinRebind(val message: String) : BiometricUnlockChallenge
}

class VaultViewModel(
    private val repository: FirestoreVaultRepository,
    private val localStore: LocalVaultStore,
    val currentUserId: String
) : ViewModel() {

    private val _dekState = MutableStateFlow<ByteArray?>(null)
    private val _failedAttempts = MutableStateFlow(0)
    private val _lockoutSeconds = MutableStateFlow(0)
    private val _pinError = MutableStateFlow<String?>(null)
    private val _isUnlockBusy = MutableStateFlow(false)
    private val _hasBiometricSealedKey = MutableStateFlow(false)
    private val _sealMode = MutableStateFlow<BiometricSealMode?>(null)
    val biometricSealMode: StateFlow<BiometricSealMode?> = _sealMode.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var lockoutJob: Job? = null
    private var backgroundTimestampElapsedMillis: Long? = null

    val securityPrefs: StateFlow<LocalSecurityPreferences> = localStore.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LocalSecurityPreferences()
        )

    private val vaultConfigStream: StateFlow<UiState<VaultConfigDoc?>> =
        repository.observeVaultConfig(currentUserId)
            .map<VaultConfigDoc?, UiState<VaultConfigDoc?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing vault config", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "无法连接云端保险库配置"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val encryptedCardsStream: StateFlow<UiState<List<EncryptedCardDoc>>> =
        repository.observeEncryptedCards(currentUserId)
            .map<List<EncryptedCardDoc>, UiState<List<EncryptedCardDoc>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing cards", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "加载云端加密门禁卡失败"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val vaultGateState: StateFlow<VaultGateState> = combine(
        vaultConfigStream,
        _dekState,
        _hasBiometricSealedKey,
        _failedAttempts,
        _lockoutSeconds,
        _pinError,
        _isUnlockBusy
    ) { values ->
        val configState = values[0] as UiState<VaultConfigDoc?>
        val dek = values[1] as ByteArray?
        val hasBioKey = values[2] as Boolean
        val attempts = values[3] as Int
        val lockout = values[4] as Int
        val err = values[5] as String?
        val busy = values[6] as Boolean

        when (configState) {
            is UiState.Loading -> VaultGateState.Loading
            is UiState.Error -> VaultGateState.Error(configState.message)
            is UiState.Success -> {
                val cfg = configState.data
                if (cfg == null) {
                    VaultGateState.NeedsPinSetup
                } else if (dek != null) {
                    VaultGateState.Unlocked
                } else {
                    VaultGateState.Locked(
                        config = cfg,
                        hasBiometricSealedKey = hasBioKey,
                        failedAttempts = attempts,
                        lockoutSecondsRemaining = lockout,
                        errorMessage = err,
                        isBusy = busy
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = VaultGateState.Loading
    )

    /** Pure derivation: decrypts card documents when the vault is unlocked. */
    val cardsState: StateFlow<UiState<List<DecryptedAccessCard>>> = combine(
        encryptedCardsStream,
        _dekState
    ) { encState, dek ->
        when (encState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(encState.message)
            is UiState.Success -> {
                if (dek == null) {
                    UiState.Loading
                } else {
                    val decrypted = withContext(Dispatchers.Default) {
                        encState.data.map { doc -> decryptDoc(doc, dek) }
                    }
                    UiState.Success(decrypted)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val recentHceEvents: StateFlow<List<HceTapEvent>> = HceRuntimeBus.recentEvents

    init {
        refreshBiometricCacheFlag()
        viewModelScope.launch {
            refreshLockoutFromStore()
        }
        // Side effects live outside the state derivation (M6): the cached HCE slot is
        // synchronized from emitted card state, never from inside combine {}.
        viewModelScope.launch {
            cardsState.collect { state ->
                if (state is UiState.Success) {
                    syncActiveHceFromCards(state.data)
                }
            }
        }
        viewModelScope.launch {
            HceRuntimeBus.tapEvents.collect { event ->
                if (event.accepted && _dekState.value != null) {
                    val currentCards = (cardsState.value as? UiState.Success)?.data.orEmpty()
                    val active = currentCards.firstOrNull { it.activeHce }
                    if (active != null) {
                        repository.recordHceEmulation(
                            userId = currentUserId,
                            cardId = active.id
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        _dekState.value?.fill(0)
        _dekState.value = null
        HceRuntimeBus.setVaultUnlocked(false)
        super.onCleared()
    }

    private fun refreshBiometricCacheFlag() {
        viewModelScope.launch {
            val record = localStore.readSealedDekRecord(currentUserId)
            _hasBiometricSealedKey.value = record != null
            _sealMode.value = record?.mode
        }
    }

    private suspend fun refreshLockoutFromStore() {
        val state = localStore.readPinLockoutState()
        _failedAttempts.value = state.failedAttempts
        val remainingMs = state.lockoutUntilElapsedMillis - SystemClock.elapsedRealtime()
        if (remainingMs > 0) {
            startLockoutTimer(((remainingMs + 999) / 1000).toInt().coerceAtLeast(1))
        }
    }

    private fun decryptDoc(doc: EncryptedCardDoc, dek: ByteArray): DecryptedAccessCard {
        val secretsResult = runCatching {
            VaultCryptoEngine.decryptCardSecrets(
                dek = dek,
                encryptedBase64 = doc.encryptedPayloadBase64,
                ivBase64 = doc.payloadIvBase64,
                ownerUserId = currentUserId,
                cryptoVersion = doc.cryptoVersion.toInt()
            )
        }
        val unsupportedVersion = doc.cryptoVersion.toInt() !in setOf(1, 2)
        return DecryptedAccessCard(
            id = doc.id,
            label = doc.label,
            category = CardCategory.fromRaw(doc.category),
            accentColor = CardAccentColor.fromRaw(doc.accentColor),
            primaryTech = NfcTechType.fromRaw(doc.primaryTech),
            maskedUid = doc.maskedUid,
            hceSupported = doc.hceSupported,
            activeHce = doc.activeHce,
            emulationCount = doc.emulationCount,
            lastEmulatedAt = doc.lastEmulatedAt,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt,
            secrets = secretsResult.getOrElse {
                CardSecretsPayload(
                    notes = if (unsupportedVersion) {
                        "数据版本不支持，请升级应用"
                    } else {
                        "解密失败：密钥不匹配或数据已损坏"
                    }
                )
            },
            decryptionFailed = secretsResult.isFailure
        )
    }

    /**
     * Keeps the sealed active-HCE configuration aligned with cloud state.
     *
     * Never flips the user's quick-toggle: a paused slot stays paused across snapshots and
     * app resumes. Only the explicit activation paths may enable quick HCE.
     */
    private suspend fun syncActiveHceFromCards(cards: List<DecryptedAccessCard>) {
        val active = cards.firstOrNull { it.activeHce && !it.decryptionFailed }
        if (active == null) {
            val hadConfigured = localStore.readConfiguredHceCard() != null
            if (hadConfigured || HceRuntimeBus.getCachedActiveCard() != null) {
                localStore.saveActiveHceCard(null)
                HceRuntimeBus.updateCachedActiveCard(null)
            }
            return
        }
        val prefs = securityPrefs.value
        val cfg = ActiveHceCardConfig(
            cardId = active.id,
            cardLabel = active.label,
            aidHex = active.secrets.aidHex.ifBlank { NfcTagParser.DEFAULT_HCE_AID },
            apduResponseHex = active.secrets.apduResponseHex,
            requireVaultUnlocked = prefs.requireVaultUnlockedForHce
        )
        val quickEnabled = localStore.isQuickHceEnabled()
        val cached = HceRuntimeBus.getCachedActiveCard()
        if (cached != cfg) {
            localStore.sealActiveHceCard(cfg)
            HceRuntimeBus.updateCachedActiveCard(if (quickEnabled) cfg else null)
        } else if (quickEnabled && cached == null) {
            HceRuntimeBus.updateCachedActiveCard(cfg)
        }
    }

    fun setupVaultPin(pin: String, enableBiometric: Boolean, onResult: (Result<Unit>) -> Unit) {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            onResult(Result.failure(IllegalArgumentException("请输入 6 位数字 PIN 码")))
            return
        }
        viewModelScope.launch {
            _isUnlockBusy.value = true
            try {
                val (dek, wrapped) = withContext(Dispatchers.Default) {
                    val freshDek = VaultCryptoEngine.generateDek()
                    val wrappedPayload = VaultCryptoEngine.wrapDekWithPin(freshDek, pin.toCharArray())
                    freshDek to wrappedPayload
                }
                val saveRes = repository.saveVaultConfig(currentUserId, wrapped, isUpdate = false)
                if (saveRes.isSuccess) {
                    localStore.setBiometricEnabled(enableBiometric)
                    if (enableBiometric) {
                        val mode = trySealForBiometric(dek)
                        if (mode == null) {
                            _statusMessage.value = "该设备不支持安全的生物识别密钥，已仅启用 PIN 解锁"
                        }
                    }
                    _dekState.value = dek
                    HceRuntimeBus.setVaultUnlocked(true)
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(saveRes.exceptionOrNull() ?: Exception("云端初始化失败")))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _isUnlockBusy.value = false
            }
        }
    }

    fun unlockWithPin(pin: String, config: VaultConfigDoc) {
        if (_lockoutSeconds.value > 0 || _isUnlockBusy.value) return
        if (pin.length != 6) return

        viewModelScope.launch {
            _isUnlockBusy.value = true
            _pinError.value = null
            try {
                val persisted = localStore.readPinLockoutState()
                val remainingMs = persisted.lockoutUntilElapsedMillis - SystemClock.elapsedRealtime()
                if (remainingMs > 0) {
                    startLockoutTimer(((remainingMs + 999) / 1000).toInt().coerceAtLeast(1))
                    return@launch
                }

                val wrapped = WrappedDekPayload(
                    wrappedDekBase64 = config.wrappedDekBase64,
                    wrappedDekIvBase64 = config.wrappedDekIvBase64,
                    kdfSaltBase64 = config.kdfSaltBase64,
                    kdfIterations = config.kdfIterations.toInt(),
                    pinVerifierBase64 = config.pinVerifierBase64,
                    kdfAlgorithm = config.kdfAlgorithm,
                    schemaVersion = config.schemaVersion.toInt()
                )
                val dek = withContext(Dispatchers.Default) {
                    VaultCryptoEngine.unwrapDekWithPin(pin.toCharArray(), wrapped)
                }
                if (dek != null) {
                    _failedAttempts.value = 0
                    _pinError.value = null
                    localStore.clearPinLockoutState()
                    _dekState.value = dek
                    HceRuntimeBus.setVaultUnlocked(true)
                    trySealForBiometric(dek)
                    upgradeVaultConfigIfNeeded(wrapped, dek, pin)
                    migrateLegacyCards(dek)
                } else {
                    onPinAttemptFailed()
                }
            } finally {
                _isUnlockBusy.value = false
            }
        }
    }

    /**
     * Transparent KDF migration: legacy v1 configs (SHA-1 or v1 verifier) are re-wrapped
     * with SHA-256 + verifier v2 after a successful unlock. Failure is non-fatal.
     */
    private suspend fun upgradeVaultConfigIfNeeded(
        wrapped: WrappedDekPayload,
        dek: ByteArray,
        pin: String
    ) {
        if (!VaultCryptoEngine.needsKdfUpgrade(wrapped)) return
        runCatching {
            val upgraded = withContext(Dispatchers.Default) {
                VaultCryptoEngine.wrapDekWithPin(dek, pin.toCharArray())
            }
            repository.saveVaultConfig(currentUserId, upgraded, isUpdate = true)
        }.onFailure {
            Log.w(TAG, "vault KDF upgrade deferred", it)
        }
    }

    /**
     * Re-encrypts legacy (no-AAD) card payloads with the owner-bound AAD once the DEK is
     * available. Best-effort: failures are retried on the next unlock.
     */
    private fun migrateLegacyCards(dek: ByteArray) {
        viewModelScope.launch {
            val docs = (encryptedCardsStream.value as? UiState.Success)?.data.orEmpty()
            docs.filter { it.cryptoVersion < 2 }.forEach { doc ->
                runCatching {
                    val secrets = VaultCryptoEngine.decryptCardSecrets(
                        dek = dek,
                        encryptedBase64 = doc.encryptedPayloadBase64,
                        ivBase64 = doc.payloadIvBase64,
                        ownerUserId = currentUserId,
                        cryptoVersion = 1
                    )
                    val enc = VaultCryptoEngine.encryptCardSecrets(
                        dek = dek,
                        payload = secrets,
                        ownerUserId = currentUserId
                    )
                    repository.updateCard(
                        userId = currentUserId,
                        cardId = doc.id,
                        label = doc.label,
                        category = CardCategory.fromRaw(doc.category),
                        accentColor = CardAccentColor.fromRaw(doc.accentColor),
                        primaryTech = NfcTechType.fromRaw(doc.primaryTech),
                        maskedUid = doc.maskedUid,
                        hceSupported = doc.hceSupported,
                        activeHce = doc.activeHce,
                        encryptedPayloadBase64 = enc.encryptedPayloadBase64,
                        payloadIvBase64 = enc.payloadIvBase64,
                        cryptoVersion = 2
                    ).getOrThrow()
                }.onFailure {
                    Log.w(TAG, "legacy card migration deferred for ${doc.id}", it)
                }
            }
        }
    }

    private suspend fun onPinAttemptFailed() {
        val nextFails = _failedAttempts.value + 1
        _failedAttempts.value = nextFails
        val lockoutSeconds = lockoutSecondsFor(nextFails)
        if (lockoutSeconds > 0) {
            startLockoutTimer(lockoutSeconds)
            _pinError.value = "尝试次数过多，请等待 ${lockoutSeconds} 秒后再试"
        } else {
            val remaining = 5 - (nextFails % 5)
            _pinError.value = "PIN 码错误，还可尝试 $remaining 次"
        }
        localStore.persistPinLockoutState(
            PinLockoutState(
                failedAttempts = nextFails,
                lockoutUntilElapsedMillis = SystemClock.elapsedRealtime() + lockoutSeconds * 1000L
            )
        )
    }

    fun prepareBiometricUnlock(onResult: (BiometricUnlockChallenge) -> Unit) {
        viewModelScope.launch {
            val record = localStore.readSealedDekRecord(currentUserId)
            if (record == null) {
                _hasBiometricSealedKey.value = false
                _sealMode.value = null
                onResult(BiometricUnlockChallenge.RequiresPinRebind("本地硬件密钥尚未缓存，请先输入一次备用 PIN 码"))
                return@launch
            }
            if (record.mode.isAuthBound) {
                val initRes = withContext(Dispatchers.Default) {
                    VaultCryptoEngine.prepareBiometricDecryptCipher(record.mode)
                }
                when (initRes) {
                    is VaultCryptoEngine.BiometricCipherInitResult.Ready -> {
                        onResult(
                            BiometricUnlockChallenge.CryptoObjectPrompt(
                                cipher = initRes.cipher,
                                sealedDekBase64 = record.cipherBase64,
                                deviceCredentialAllowed = record.mode == BiometricSealMode.RSA_DEVICE_CREDENTIAL
                            )
                        )
                    }
                    is VaultCryptoEngine.BiometricCipherInitResult.KeyInvalidated -> {
                        localStore.clearBiometricSealedDek()
                        _hasBiometricSealedKey.value = false
                        _sealMode.value = null
                        onResult(
                            BiometricUnlockChallenge.RequiresPinRebind(
                                "检测到设备生物特征变更，Keystore 硬件私钥已自动失效，请用备用 PIN 码解锁以重新绑定"
                            )
                        )
                    }
                    is VaultCryptoEngine.BiometricCipherInitResult.MissingKey -> {
                        localStore.clearBiometricSealedDek()
                        _hasBiometricSealedKey.value = false
                        _sealMode.value = null
                        onResult(BiometricUnlockChallenge.RequiresPinRebind("硬件密钥已重置，请用备用 PIN 码解锁一次"))
                    }
                    is VaultCryptoEngine.BiometricCipherInitResult.Error -> {
                        onResult(BiometricUnlockChallenge.RequiresPinRebind(initRes.message))
                    }
                }
            } else {
                onResult(BiometricUnlockChallenge.LegacyPrompt)
            }
        }
    }

    fun unlockWithBiometricCryptoCipher(
        unlockedCipher: Cipher,
        sealedDekBase64: String,
        onFallbackToPin: (String?) -> Unit
    ) {
        viewModelScope.launch {
            _isUnlockBusy.value = true
            try {
                val dek = withContext(Dispatchers.Default) {
                    VaultCryptoEngine.decryptWithUnlockedBiometricCipher(unlockedCipher, sealedDekBase64)
                }
                if (dek != null && dek.size == 32) {
                    _failedAttempts.value = 0
                    _pinError.value = null
                    localStore.clearPinLockoutState()
                    _dekState.value = dek
                    HceRuntimeBus.setVaultUnlocked(true)
                    migrateLegacyCards(dek)
                } else {
                    localStore.clearBiometricSealedDek()
                    _hasBiometricSealedKey.value = false
                    _sealMode.value = null
                    onFallbackToPin("Keystore 私钥解封失败，请使用备用 PIN 码解锁")
                }
            } finally {
                _isUnlockBusy.value = false
            }
        }
    }

    /**
     * Legacy non-auth-bound AES unlock. Only reachable for pre-upgrade records; on success
     * the DEK is re-sealed with an auth-bound key on the next PIN unlock.
     */
    fun unlockWithBiometric(onFallbackToPin: (String?) -> Unit) {
        viewModelScope.launch {
            _isUnlockBusy.value = true
            try {
                val dek = localStore.unsealDekForUser(currentUserId)
                if (dek != null) {
                    _failedAttempts.value = 0
                    _pinError.value = null
                    _dekState.value = dek
                    HceRuntimeBus.setVaultUnlocked(true)
                    _statusMessage.value = "旧版设备绑定缓存已解锁，请稍后输入一次 PIN 以升级为认证绑定密钥"
                    migrateLegacyCards(dek)
                } else {
                    _hasBiometricSealedKey.value = false
                    _sealMode.value = null
                    onFallbackToPin("本地硬件密钥尚未缓存，请先输入一次备用 PIN 码")
                }
            } finally {
                _isUnlockBusy.value = false
            }
        }
    }

    private fun startLockoutTimer(seconds: Int) {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            val total = seconds.coerceAtLeast(1)
            var remaining = total
            while (remaining > 0) {
                _lockoutSeconds.value = remaining
                delay(1000L)
                remaining--
            }
            _lockoutSeconds.value = 0
            _pinError.value = null
        }
    }

    fun clearPinError() {
        _pinError.value = null
    }

    fun lockVaultNow() {
        _dekState.value?.fill(0)
        _dekState.value = null
        HceRuntimeBus.setVaultUnlocked(false)
        refreshBiometricCacheFlag()
    }

    fun onAppBackgrounded(): Boolean {
        if (_dekState.value != null) {
            backgroundTimestampElapsedMillis = SystemClock.elapsedRealtime()
            if (securityPrefs.value.autoLockMinutes == 0) {
                lockVaultNow()
                return true
            }
        }
        return false
    }

    fun onAppResumed(): Boolean {
        val bgTime = backgroundTimestampElapsedMillis ?: return false
        backgroundTimestampElapsedMillis = null
        val limitMin = securityPrefs.value.autoLockMinutes
        if (_dekState.value != null) {
            val elapsedMillis = (SystemClock.elapsedRealtime() - bgTime).coerceAtLeast(0L)
            if (shouldLockAfterBackground(limitMin, elapsedMillis)) {
                lockVaultNow()
                return true
            }
        }
        return false
    }

    fun saveNewCard(
        label: String,
        category: CardCategory,
        accentColor: CardAccentColor,
        primaryTech: NfcTechType,
        hceSupported: Boolean,
        activateHceNow: Boolean,
        secrets: CardSecretsPayload,
        onSyncAid: (String?) -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        val dek = _dekState.value
        if (dek == null) {
            onResult(Result.failure(IllegalStateException("保险库未解锁")))
            return
        }
        validateSecrets(secrets)?.let { message ->
            onResult(Result.failure(IllegalArgumentException(message)))
            return
        }
        viewModelScope.launch {
            val enc = withContext(Dispatchers.Default) {
                VaultCryptoEngine.encryptCardSecrets(dek, secrets, ownerUserId = currentUserId)
            }
            validateEncryptedSize(enc.encryptedPayloadBase64)?.let { message ->
                onResult(Result.failure(IllegalArgumentException(message)))
                return@launch
            }
            val masked = NfcTagParser.buildMaskedUidFromHexString(secrets.uidHex)
            val existingActiveIds = (cardsState.value as? UiState.Success)?.data
                ?.filter { it.activeHce }
                ?.map { it.id }
                .orEmpty()

            val shouldActivate = hceSupported && activateHceNow
            val createRes = if (shouldActivate) {
                repository.createCardAndActivate(
                    userId = currentUserId,
                    label = label,
                    category = category,
                    accentColor = accentColor,
                    primaryTech = primaryTech,
                    maskedUid = masked,
                    hceSupported = true,
                    encryptedPayloadBase64 = enc.encryptedPayloadBase64,
                    payloadIvBase64 = enc.payloadIvBase64,
                    deactivateCardIds = existingActiveIds
                ).onSuccess {
                    localStore.setQuickHceEnabled(true)
                    onSyncAid(secrets.aidHex)
                }
            } else {
                repository.createCard(
                    userId = currentUserId,
                    label = label,
                    category = category,
                    accentColor = accentColor,
                    primaryTech = primaryTech,
                    maskedUid = masked,
                    hceSupported = hceSupported,
                    activeHce = false,
                    encryptedPayloadBase64 = enc.encryptedPayloadBase64,
                    payloadIvBase64 = enc.payloadIvBase64
                )
            }
            onResult(createRes)
        }
    }

    fun updateCard(
        existing: DecryptedAccessCard,
        label: String,
        category: CardCategory,
        accentColor: CardAccentColor,
        hceSupported: Boolean,
        secrets: CardSecretsPayload,
        onSyncAid: (String?) -> Unit,
        onResult: (Result<Unit>) -> Unit
    ) {
        val dek = _dekState.value
        if (dek == null) {
            onResult(Result.failure(IllegalStateException("保险库未解锁")))
            return
        }
        validateSecrets(secrets)?.let { message ->
            onResult(Result.failure(IllegalArgumentException(message)))
            return
        }
        viewModelScope.launch {
            val enc = withContext(Dispatchers.Default) {
                VaultCryptoEngine.encryptCardSecrets(dek, secrets, ownerUserId = currentUserId)
            }
            validateEncryptedSize(enc.encryptedPayloadBase64)?.let { message ->
                onResult(Result.failure(IllegalArgumentException(message)))
                return@launch
            }
            val masked = NfcTagParser.buildMaskedUidFromHexString(secrets.uidHex)
            val keepActive = existing.activeHce && hceSupported
            val res = repository.updateCard(
                userId = currentUserId,
                cardId = existing.id,
                label = label,
                category = category,
                accentColor = accentColor,
                primaryTech = existing.primaryTech,
                maskedUid = masked,
                hceSupported = hceSupported,
                activeHce = keepActive,
                encryptedPayloadBase64 = enc.encryptedPayloadBase64,
                payloadIvBase64 = enc.payloadIvBase64,
                cryptoVersion = 2
            )
            if (res.isSuccess && keepActive) {
                onSyncAid(secrets.aidHex)
            }
            onResult(res)
        }
    }

    fun toggleActiveHce(card: DecryptedAccessCard, onSyncAid: (String?) -> Unit) {
        if (!card.hceSupported) {
            _statusMessage.value = "该卡片属于固定 UID 物理标签（${card.primaryTech.displayName}），受 Android 硬件限制暂不支持 HCE 模拟"
            return
        }
        viewModelScope.launch {
            val currentCards = (cardsState.value as? UiState.Success)?.data.orEmpty()
            val activeIds = currentCards.filter { it.activeHce }.map { it.id }
            val nextTargetId = if (card.activeHce) null else card.id
            val res = repository.setActiveHceCard(currentUserId, nextTargetId, activeIds)
            if (res.isSuccess) {
                if (nextTargetId != null) {
                    localStore.setQuickHceEnabled(true)
                    onSyncAid(card.secrets.aidHex)
                    _statusMessage.value = "已激活「${card.label}」用于 HCE 模拟刷卡"
                } else {
                    localStore.saveActiveHceCard(null)
                    HceRuntimeBus.updateCachedActiveCard(null)
                    onSyncAid(null)
                    _statusMessage.value = "已停用 HCE 模拟卡槽"
                }
            } else {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "切换模拟状态失败"
            }
        }
    }

    fun deleteCard(card: DecryptedAccessCard, onSyncAid: (String?) -> Unit, onComplete: () -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteCard(currentUserId, card.id)
            if (res.isSuccess) {
                if (card.activeHce) {
                    localStore.saveActiveHceCard(null)
                    HceRuntimeBus.updateCachedActiveCard(null)
                    onSyncAid(null)
                }
                _statusMessage.value = "已安全删除「${card.label}」"
                onComplete()
            } else {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "删除卡片失败"
            }
        }
    }

    fun updateBiometricPreference(enabled: Boolean) {
        viewModelScope.launch {
            localStore.setBiometricEnabled(enabled)
            val dek = _dekState.value
            if (enabled && dek != null) {
                val mode = trySealForBiometric(dek)
                if (mode == null) {
                    _statusMessage.value = "该设备不支持安全的生物识别密钥，已保持仅 PIN 解锁"
                    return@launch
                }
            } else if (!enabled) {
                VaultCryptoEngine.deleteBiometricKeystoreKey()
                _hasBiometricSealedKey.value = false
                _sealMode.value = null
            }
            _statusMessage.value = if (enabled) "已开启生物识别快速解锁" else "已关闭生物识别解锁"
        }
    }

    fun updateAutoLockMinutes(minutes: Int) {
        viewModelScope.launch {
            localStore.setAutoLockMinutes(minutes)
            _statusMessage.value = when {
                minutes == 0 -> "已开启自动锁屏：切至后台立即锁定并触发重新生物识别"
                minutes > 0 -> "已设置为后台 ${minutes} 分钟后自动锁定"
                else -> "已切换为仅手动锁定"
            }
        }
    }

    fun updateRequireUnlockForHce(required: Boolean) {
        viewModelScope.launch {
            localStore.setRequireVaultUnlockedForHce(required)
            val currentCards = (cardsState.value as? UiState.Success)?.data.orEmpty()
            val active = currentCards.firstOrNull { it.activeHce && !it.decryptionFailed }
            if (active != null) {
                val updatedCfg = ActiveHceCardConfig(
                    cardId = active.id,
                    cardLabel = active.label,
                    aidHex = active.secrets.aidHex.ifBlank { NfcTagParser.DEFAULT_HCE_AID },
                    apduResponseHex = active.secrets.apduResponseHex,
                    requireVaultUnlocked = required
                )
                localStore.sealActiveHceCard(updatedCfg)
                if (localStore.isQuickHceEnabled()) {
                    HceRuntimeBus.updateCachedActiveCard(updatedCfg)
                }
            }
        }
    }

    fun changeVaultPin(currentPin: String, newPin: String, onResult: (Result<Unit>) -> Unit) {
        val gate = vaultConfigStream.value as? UiState.Success
        val cfg = gate?.data
        if (cfg == null) {
            onResult(Result.failure(IllegalStateException("未找到保险库配置")))
            return
        }
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            onResult(Result.failure(IllegalArgumentException("新 PIN 码必须为 6 位数字")))
            return
        }
        viewModelScope.launch {
            try {
                val wrappedOld = WrappedDekPayload(
                    wrappedDekBase64 = cfg.wrappedDekBase64,
                    wrappedDekIvBase64 = cfg.wrappedDekIvBase64,
                    kdfSaltBase64 = cfg.kdfSaltBase64,
                    kdfIterations = cfg.kdfIterations.toInt(),
                    pinVerifierBase64 = cfg.pinVerifierBase64,
                    kdfAlgorithm = cfg.kdfAlgorithm,
                    schemaVersion = cfg.schemaVersion.toInt()
                )
                val dek = withContext(Dispatchers.Default) {
                    VaultCryptoEngine.unwrapDekWithPin(currentPin.toCharArray(), wrappedOld)
                }
                if (dek == null) {
                    onResult(Result.failure(IllegalArgumentException("原 PIN 码验证失败")))
                    return@launch
                }
                val newWrapped = withContext(Dispatchers.Default) {
                    VaultCryptoEngine.wrapDekWithPin(dek, newPin.toCharArray())
                }
                val res = repository.saveVaultConfig(currentUserId, newWrapped, isUpdate = true)
                if (res.isSuccess) {
                    val sealMode = trySealForBiometric(dek)
                    _statusMessage.value = if (securityPrefs.value.biometricEnabled && sealMode == null) {
                        "PIN 码已更新并同步；生物识别缓存将在下次解锁时重建"
                    } else {
                        "PIN 码已更新，密钥封装已同步至云端"
                    }
                }
                onResult(res)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    suspend fun prepareForSignOut() {
        _dekState.value?.fill(0)
        _dekState.value = null
        HceRuntimeBus.setVaultUnlocked(false)
        HceRuntimeBus.updateCachedActiveCard(null)
        VaultCryptoEngine.deleteBiometricKeystoreKey()
        localStore.clearSensitiveLocalSession()
    }

    private suspend fun trySealForBiometric(dek: ByteArray): BiometricSealMode? {
        if (!securityPrefs.value.biometricEnabled) return null
        val mode = withContext(Dispatchers.Default) {
            runCatching { localStore.sealDekForBiometric(currentUserId, dek) }
                .onFailure { Log.w(TAG, "biometric seal failed; PIN unlock unaffected", it) }
                .getOrNull()
        }
        _hasBiometricSealedKey.value = mode != null
        _sealMode.value = mode
        return mode
    }

    private fun validateSecrets(secrets: CardSecretsPayload): String? {
        if (secrets.notes.length > MAX_NOTES_LENGTH) {
            return "备注过长（最多 $MAX_NOTES_LENGTH 字），请精简后重试"
        }
        return null
    }

    private fun validateEncryptedSize(cipherBase64: String): String? =
        if (cipherBase64.length > MAX_ENCRYPTED_PAYLOAD_CHARS) {
            "卡片数据过大，无法同步到云端，请精简备注或去除冗余数据"
        } else {
            null
        }

    companion object {
        private const val TAG = "VaultViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val MAX_NOTES_LENGTH = 1500
        private const val MAX_ENCRYPTED_PAYLOAD_CHARS = 8192

        fun lockoutSecondsFor(failedAttempts: Int): Int = when {
            failedAttempts <= 0 || failedAttempts % 5 != 0 -> 0
            failedAttempts <= 5 -> 30
            failedAttempts <= 10 -> 300
            else -> 1800
        }

        fun shouldLockAfterBackground(autoLockMinutes: Int, elapsedMillis: Long): Boolean {
            if (autoLockMinutes < 0) return false
            if (autoLockMinutes == 0) return true
            return elapsedMillis >= autoLockMinutes * 60_000L
        }
    }
}