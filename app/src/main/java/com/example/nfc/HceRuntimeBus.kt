package com.example.nfc

import com.example.data.model.ActiveHceCardConfig
import com.example.data.model.HceTapEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object HceRuntimeBus {
    private val _pendingScannedTagInfo = MutableStateFlow<ScannedNfcTagInfo?>(null)
    val pendingScannedTagInfo = _pendingScannedTagInfo.asStateFlow()

    private val _scannedTagLogs = MutableStateFlow<List<ScannedNfcTagInfo>>(emptyList())
    val scannedTagLogs = _scannedTagLogs.asStateFlow()

    private val _tapEvents = MutableSharedFlow<HceTapEvent>(extraBufferCapacity = 16)
    val tapEvents = _tapEvents.asSharedFlow()

    private val _recentEvents = MutableStateFlow<List<HceTapEvent>>(emptyList())
    val recentEvents = _recentEvents.asStateFlow()

    private val _scanTabActive = MutableStateFlow(false)
    val scanTabActive = _scanTabActive.asStateFlow()

    private val _readerModeActive = MutableStateFlow(false)
    val readerModeActive = _readerModeActive.asStateFlow()

    private val vaultUnlocked = AtomicBoolean(false)
    private val cachedActiveCard = AtomicReference<ActiveHceCardConfig?>(null)

    fun recordParsedTag(info: ScannedNfcTagInfo) {
        _scannedTagLogs.value = (listOf(info) + _scannedTagLogs.value).take(30)
        if (_scanTabActive.value) {
            _pendingScannedTagInfo.value = info
        }
    }

    fun consumeScannedTagInfo() {
        _pendingScannedTagInfo.value = null
    }

    fun clearDebugLogs() {
        _scannedTagLogs.value = emptyList()
        _recentEvents.value = emptyList()
    }

    fun setScanTabActive(active: Boolean) {
        _scanTabActive.value = active
    }

    fun setVaultUnlocked(unlocked: Boolean) {
        vaultUnlocked.set(unlocked)
    }

    fun isVaultUnlocked(): Boolean = vaultUnlocked.get()

    fun updateCachedActiveCard(config: ActiveHceCardConfig?) {
        cachedActiveCard.set(config)
    }

    fun getCachedActiveCard(): ActiveHceCardConfig? = cachedActiveCard.get()

    fun setReaderModeActive(active: Boolean) {
        _readerModeActive.value = active
    }

    fun emitTapEvent(event: HceTapEvent) {
        _tapEvents.tryEmit(event)
        _recentEvents.value = (listOf(event) + _recentEvents.value).take(20)
    }
}
