package com.example.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.example.data.local.LocalVaultStore
import com.example.data.model.ActiveHceCardConfig
import com.example.data.model.HceTapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AccessCardHceService : HostApduService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

        val activeCard = HceRuntimeBus.getCachedActiveCard()
        if (activeCard == null) {
            // Attempt async read if memory cache is cold
            serviceScope.launch {
                val loaded = localStore.readActiveHceCard()
                HceRuntimeBus.updateCachedActiveCard(loaded)
                val response = handleApduWithTelemetry(commandApdu, loaded, HceRuntimeBus.isVaultUnlocked())
                sendResponseApdu(response)
            }
            return null
        }

        return handleApduWithTelemetry(commandApdu, activeCard, HceRuntimeBus.isVaultUnlocked())
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

        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        val STATUS_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
        val STATUS_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())
        val STATUS_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        val STATUS_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00.toByte())

        private val BUILTIN_ACCESS_AIDS = setOf(
            NfcTagParser.DEFAULT_HCE_AID,
            "F0010203040506",
            "A0000002471001",
            "D2760000850101"
        )

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
                val configuredAid = NfcTagParser.normalizeHex(activeCard.aidHex)
                    .ifEmpty { NfcTagParser.DEFAULT_HCE_AID }

                val aidMatched = requestedAid == configuredAid ||
                    (configuredAid == NfcTagParser.DEFAULT_HCE_AID && requestedAid in BUILTIN_ACCESS_AIDS)

                return if (aidMatched) {
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

            // 3. GET DATA / PC/SC Get Token (00 CA / FF CA)
            if ((cla == 0x00 || cla == 0xFF) && ins == 0xCA) {
                return ApduEvaluationResult(
                    responseBytes = payloadBytes + STATUS_SUCCESS,
                    statusWordHex = "9000",
                    accepted = true,
                    cardLabel = activeCard.cardLabel,
                    aidHex = activeCard.aidHex,
                    commandHex = cmdHex
                )
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
