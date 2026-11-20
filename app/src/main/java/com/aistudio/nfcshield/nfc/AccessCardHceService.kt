package com.aistudio.nfcshield.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.aistudio.nfcshield.data.local.LocalVaultStore
import com.aistudio.nfcshield.data.model.ActiveHceCardConfig
import com.aistudio.nfcshield.data.model.HceTapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class AccessCardHceService : HostApduService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coldLoadMutex = Mutex()
    private lateinit var localStore: LocalVaultStore

    override fun onCreate() {
        super.onCreate()
        localStore = LocalVaultStore(applicationContext)
        serviceScope.launch {
            val stored = localStore.readActiveHceCard()
            if (stored != null && HceRuntimeBus.getCachedActiveCard() == null) {
                HceRuntimeBus.updateCachedActiveCard(stored)
            }
        }
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_WRONG_LENGTH
        }

        val activeCard = HceRuntimeBus.getCachedActiveCard() ?: loadActiveCardBlocking()
        return handleApduWithTelemetry(commandApdu, activeCard, HceRuntimeBus.isVaultUnlocked())
    }

    /**
     * Single-flight cold-cache load. The first APDU after process (re)start blocks this
     * binder thread until the sealed config is read, so responses stay ordered and are
     * never sent asynchronously after the reader has moved on. A short timeout bounds the
     * worst case; the reader retries and by then the cache is warm.
     */
    private fun loadActiveCardBlocking(): ActiveHceCardConfig? = runBlocking {
        coldLoadMutex.withLock {
            HceRuntimeBus.getCachedActiveCard() ?: withTimeoutOrNull(COLD_LOAD_TIMEOUT_MS) {
                val loaded = localStore.readActiveHceCard()
                HceRuntimeBus.updateCachedActiveCard(loaded)
                loaded
            }
        }
    }

    private fun handleApduWithTelemetry(
        commandApdu: ByteArray,
        activeCard: ActiveHceCardConfig?,
        vaultUnlocked: Boolean
    ): ByteArray {
        val result = evaluateCommandApdu(commandApdu, activeCard, vaultUnlocked)
        emitEvent(
            label = result.cardLabel,
            aidHex = result.aidHex,
            cmdHex = result.commandHex,
            swHex = result.statusWordHex,
            accepted = result.accepted
        )
        return result.responseBytes
    }

    private fun emitEvent(
        label: String,
        aidHex: String,
        cmdHex: String,
        swHex: String,
        accepted: Boolean
    ) {
        HceRuntimeBus.emitTapEvent(
            HceTapEvent(
                timestampMillis = System.currentTimeMillis(),
                cardLabel = label,
                aidHex = aidHex,
                commandHex = cmdHex,
                statusWordHex = swHex,
                accepted = accepted
            )
        )
    }

    override fun onDeactivated(reason: Int) {
        val reasonLabel = when (reason) {
            DEACTIVATION_LINK_LOSS -> "LINK_LOSS"
            DEACTIVATION_DESELECTED -> "DESELECTED"
            else -> "UNKNOWN($reason)"
        }
        Log.d(TAG, "HCE session deactivated: $reasonLabel")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    data class ApduEvaluationResult(
        val responseBytes: ByteArray,
        val statusWordHex: String,
        val accepted: Boolean,
        val cardLabel: String,
        val aidHex: String,
        val commandHex: String
    )

    companion object {
        private const val TAG = "AccessCardHceService"
        private const val COLD_LOAD_TIMEOUT_MS = 400L

        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        val STATUS_REFERENCED_DATA_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x88.toByte())
        val STATUS_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
        val STATUS_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())
        val STATUS_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        val STATUS_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00.toByte())

        fun evaluateCommandApdu(
            commandApdu: ByteArray,
            activeCard: ActiveHceCardConfig?,
            vaultUnlocked: Boolean
        ): ApduEvaluationResult {
            val cmdHex = NfcTagParser.bytesToRawHex(commandApdu)
            if (commandApdu.size < 4) {
                return ApduEvaluationResult(
                    responseBytes = STATUS_WRONG_LENGTH,
                    statusWordHex = "6700",
                    accepted = false,
                    cardLabel = activeCard?.cardLabel ?: "未激活卡片",
                    aidHex = activeCard?.aidHex.orEmpty(),
                    commandHex = cmdHex
                )
            }

            if (activeCard == null) {
                return ApduEvaluationResult(
                    responseBytes = STATUS_FILE_NOT_FOUND,
                    statusWordHex = "6A82",
                    accepted = false,
                    cardLabel = "未激活卡片",
                    aidHex = "",
                    commandHex = cmdHex
                )
            }

            if (activeCard.requireVaultUnlocked && !vaultUnlocked) {
                return ApduEvaluationResult(
                    responseBytes = STATUS_CONDITIONS_NOT_SATISFIED,
                    statusWordHex = "6985",
                    accepted = false,
                    cardLabel = activeCard.cardLabel,
                    aidHex = activeCard.aidHex,
                    commandHex = cmdHex
                )
            }

            val cla = commandApdu[0].toInt() and 0xFF
            val ins = commandApdu[1].toInt() and 0xFF
            val p1 = commandApdu[2].toInt() and 0xFF
            val p2 = commandApdu[3].toInt() and 0xFF
            val payloadBytes = NfcTagParser.hexStringToBytes(activeCard.apduResponseHex)

            // 1. ISO 7816-4 SELECT by DF Name (00 A4 04 P2 <Lc> <AID> [Le])
            if (cla == 0x00 && ins == 0xA4 && p1 == 0x04) {
                if (commandApdu.size < 5) {
                    return ApduEvaluationResult(
                        responseBytes = STATUS_WRONG_LENGTH,
                        statusWordHex = "6700",
                        accepted = false,
                        cardLabel = activeCard.cardLabel,
                        aidHex = activeCard.aidHex,
                        commandHex = cmdHex
                    )
                }
                val lc = commandApdu[4].toInt() and 0xFF
                if (lc == 0 || commandApdu.size < 5 + lc) {
                    return ApduEvaluationResult(
                        responseBytes = STATUS_WRONG_LENGTH,
                        statusWordHex = "6700",
                        accepted = false,
                        cardLabel = activeCard.cardLabel,
                        aidHex = activeCard.aidHex,
                        commandHex = cmdHex
                    )
                }
                val requestedAid = NfcTagParser.bytesToRawHex(commandApdu.copyOfRange(5, 5 + lc))

                return if (HceAids.isAcceptedAid(requestedAid, activeCard.aidHex)) {
                    ApduEvaluationResult(
                        responseBytes = payloadBytes + STATUS_SUCCESS,
                        statusWordHex = "9000",
                        accepted = true,
                        cardLabel = activeCard.cardLabel,
                        aidHex = requestedAid,
                        commandHex = cmdHex
                    )
                } else {
                    ApduEvaluationResult(
                        responseBytes = STATUS_FILE_NOT_FOUND,
                        statusWordHex = "6A82",
                        accepted = false,
                        cardLabel = activeCard.cardLabel,
                        aidHex = requestedAid,
                        commandHex = cmdHex
                    )
                }
            }

            // 2. ISO 7816-4 READ BINARY (00 B0 <P1> <P2> [Le])
            if (cla == 0x00 && ins == 0xB0) {
                val offset = ((p1 and 0x7F) shl 8) or p2
                if (offset > payloadBytes.size) {
                    return ApduEvaluationResult(
                        responseBytes = STATUS_WRONG_P1P2,
                        statusWordHex = "6B00",
                        accepted = false,
                        cardLabel = activeCard.cardLabel,
                        aidHex = activeCard.aidHex,
                        commandHex = cmdHex
                    )
                }
                val slice = payloadBytes.copyOfRange(offset, payloadBytes.size)
                val le = if (commandApdu.size >= 5) commandApdu[4].toInt() and 0xFF else 0
                val responseBody = if (le in 1..slice.size) slice.copyOfRange(0, le) else slice
                return ApduEvaluationResult(
                    responseBytes = responseBody + STATUS_SUCCESS,
                    statusWordHex = "9000",
                    accepted = true,
                    cardLabel = activeCard.cardLabel,
                    aidHex = activeCard.aidHex,
                    commandHex = cmdHex
                )
            }

            // 3. GET DATA / PC/SC Get Token (00 CA / FF CA).
            // Only the standard tag-less form (P1=P2=00) is answered; other tags are not
            // emulated, so return 6A88 instead of dumping the payload for any tag.
            if ((cla == 0x00 || cla == 0xFF) && ins == 0xCA) {
                return if (p1 == 0x00 && p2 == 0x00) {
                    ApduEvaluationResult(
                        responseBytes = payloadBytes + STATUS_SUCCESS,
                        statusWordHex = "9000",
                        accepted = true,
                        cardLabel = activeCard.cardLabel,
                        aidHex = activeCard.aidHex,
                        commandHex = cmdHex
                    )
                } else {
                    ApduEvaluationResult(
                        responseBytes = STATUS_REFERENCED_DATA_NOT_FOUND,
                        statusWordHex = "6A88",
                        accepted = false,
                        cardLabel = activeCard.cardLabel,
                        aidHex = activeCard.aidHex,
                        commandHex = cmdHex
                    )
                }
            }

            return ApduEvaluationResult(
                responseBytes = STATUS_INS_NOT_SUPPORTED,
                statusWordHex = "6D00",
                accepted = false,
                cardLabel = activeCard.cardLabel,
                aidHex = activeCard.aidHex,
                commandHex = cmdHex
            )
        }
    }
}