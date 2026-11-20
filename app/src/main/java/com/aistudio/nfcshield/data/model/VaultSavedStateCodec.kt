package com.aistudio.nfcshield.data.model

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compact, size-bounded JSON codec for persisting encrypted card documents and wrapped vault
 * configuration inside [androidx.lifecycle.SavedStateHandle].
 *
 * Security invariant: only ciphertext fields (identical to Firestore storage) and non-sensitive
 * metadata are serialized. Plaintext DEK, PIN, and decrypted [CardSecretsPayload] are NEVER
 * written to SavedStateHandle.
 */
object VaultSavedStateCodec {
    private const val MAX_CARDS_JSON_BYTES = 128 * 1024

    fun encodeEncryptedCards(cards: List<EncryptedCardDoc>): String? {
        if (cards.isEmpty()) return "[]"
        val arr = JSONArray()
        var approxBytes = 2
        for (card in cards) {
            val obj = JSONObject().apply {
                put("id", card.id)
                put("userId", card.userId)
                put("label", card.label)
                put("category", card.category)
                put("accentColor", card.accentColor)
                put("primaryTech", card.primaryTech)
                put("maskedUid", card.maskedUid)
                put("hceSupported", card.hceSupported)
                put("activeHce", card.activeHce)
                put("encryptedPayloadBase64", card.encryptedPayloadBase64)
                put("payloadIvBase64", card.payloadIvBase64)
                put("cryptoVersion", card.cryptoVersion)
                put("emulationCount", card.emulationCount)
                putTimestamp("lastEmulatedAt", card.lastEmulatedAt)
                putTimestamp("createdAt", card.createdAt)
                putTimestamp("updatedAt", card.updatedAt)
            }
            val itemSize = obj.toString().toByteArray(Charsets.UTF_8).size + 1
            if (approxBytes + itemSize > MAX_CARDS_JSON_BYTES) {
                break
            }
            arr.put(obj)
            approxBytes += itemSize
        }
        return arr.toString()
    }

    fun decodeEncryptedCards(rawJson: String?): List<EncryptedCardDoc>? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    if (id.isBlank()) continue
                    add(
                        EncryptedCardDoc(
                            id = id,
                            userId = obj.optString("userId", ""),
                            label = obj.optString("label", ""),
                            category = obj.optString("category", CardCategory.OTHER.name),
                            accentColor = obj.optString("accentColor", CardAccentColor.CYAN.name),
                            primaryTech = obj.optString("primaryTech", NfcTechType.ISO_DEP.name),
                            maskedUid = obj.optString("maskedUid", ""),
                            hceSupported = obj.optBoolean("hceSupported", false),
                            activeHce = obj.optBoolean("activeHce", false),
                            encryptedPayloadBase64 = obj.optString("encryptedPayloadBase64", ""),
                            payloadIvBase64 = obj.optString("payloadIvBase64", ""),
                            cryptoVersion = obj.optLong("cryptoVersion", 1L),
                            emulationCount = obj.optLong("emulationCount", 0L),
                            lastEmulatedAt = obj.optTimestamp("lastEmulatedAt"),
                            createdAt = obj.optTimestamp("createdAt"),
                            updatedAt = obj.optTimestamp("updatedAt")
                        )
                    )
                }
            }
        }.getOrNull()
    }

    fun encodeVaultConfig(config: VaultConfigDoc): String = JSONObject().apply {
        put("id", config.id)
        put("userId", config.userId)
        put("wrappedDekBase64", config.wrappedDekBase64)
        put("wrappedDekIvBase64", config.wrappedDekIvBase64)
        put("kdfSaltBase64", config.kdfSaltBase64)
        put("kdfIterations", config.kdfIterations)
        put("pinVerifierBase64", config.pinVerifierBase64)
        put("kdfAlgorithm", config.kdfAlgorithm)
        put("schemaVersion", config.schemaVersion)
        putTimestamp("createdAt", config.createdAt)
        putTimestamp("updatedAt", config.updatedAt)
    }.toString()

    fun decodeVaultConfig(rawJson: String?): VaultConfigDoc? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(rawJson)
            val userId = obj.optString("userId", "")
            val wrappedDek = obj.optString("wrappedDekBase64", "")
            if (userId.isBlank() || wrappedDek.isBlank()) return null
            VaultConfigDoc(
                id = obj.optString("id", "config"),
                userId = userId,
                wrappedDekBase64 = wrappedDek,
                wrappedDekIvBase64 = obj.optString("wrappedDekIvBase64", ""),
                kdfSaltBase64 = obj.optString("kdfSaltBase64", ""),
                kdfIterations = obj.optLong("kdfIterations", 120000L),
                pinVerifierBase64 = obj.optString("pinVerifierBase64", ""),
                kdfAlgorithm = obj.optString("kdfAlgorithm", "PBKDF2WithHmacSHA256"),
                schemaVersion = obj.optLong("schemaVersion", 2L),
                createdAt = obj.optTimestamp("createdAt"),
                updatedAt = obj.optTimestamp("updatedAt")
            )
        }.getOrNull()
    }

    private fun JSONObject.putTimestamp(key: String, timestamp: Timestamp?) {
        if (timestamp != null) {
            put("${key}Sec", timestamp.seconds)
            put("${key}Nano", timestamp.nanoseconds)
        }
    }

    private fun JSONObject.optTimestamp(key: String): Timestamp? {
        if (!has("${key}Sec")) return null
        val sec = optLong("${key}Sec", Long.MIN_VALUE)
        if (sec == Long.MIN_VALUE) return null
        val nano = optInt("${key}Nano", 0)
        return runCatching { Timestamp(sec, nano) }.getOrNull()
    }
}
