package com.aistudio.nfcshield.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

enum class CardCategory(val displayName: String) {
    RESIDENCE("住宅小区"),
    OFFICE("办公门禁"),
    CAMPUS("校园一卡通"),
    PARKING("停车场卡"),
    TRANSIT("交通卡"),
    OTHER("其他凭证");

    companion object {
        fun fromRaw(raw: String?): CardCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class CardAccentColor(val displayName: String) {
    CYAN("电光青"),
    EMERALD("翡翠绿"),
    INDIGO("深邃蓝"),
    AMBER("琥珀金"),
    ROSE("曜石红");

    companion object {
        fun fromRaw(raw: String?): CardAccentColor =
            entries.firstOrNull { it.name == raw } ?: CYAN
    }
}

enum class CardIconOption(val displayName: String) {
    AUTO("跟随分类"),
    KEY("门禁钥匙"),
    HOME("住宅门锁"),
    OFFICE("办公大厦"),
    BADGE("身份工牌"),
    CAR("车库道闸"),
    TRANSIT("地铁交通"),
    CAMPUS("校园一卡通"),
    GYM("健身会所"),
    STORE("商铺门禁"),
    SHIELD("高密保险");

    companion object {
        fun fromRaw(raw: String?): CardIconOption =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

enum class NfcTechType(val displayName: String) {
    ISO_DEP("ISO-DEP (ISO 14443-4)"),
    NFC_A("NFC-A (ISO 14443-3A)"),
    NFC_B("NFC-B (ISO 14443-3B)"),
    NFC_F("NFC-F (FeliCa)"),
    NFC_V("NFC-V (ISO 15693)"),
    MIFARE_CLASSIC("MIFARE Classic"),
    MIFARE_ULTRALIGHT("MIFARE Ultralight"),
    NDEF("NDEF 标签"),
    OTHER("自定义凭证");

    companion object {
        fun fromRaw(raw: String?): NfcTechType =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

data class CardSecretsPayload(
    val uidHex: String = "",
    val atqaHex: String = "",
    val sakHex: String = "",
    val historicalBytesHex: String = "",
    val aidHex: String = "",
    val apduResponseHex: String = "",
    val ndefSummary: String = "",
    val techList: List<String> = emptyList(),
    val notes: String = "",
    val iconKey: String = ""
) {
    fun toJsonString(): String {
        val techArr = JSONArray()
        techList.forEach { techArr.put(it) }
        return JSONObject().apply {
            put("uidHex", uidHex)
            put("atqaHex", atqaHex)
            put("sakHex", sakHex)
            put("historicalBytesHex", historicalBytesHex)
            put("aidHex", aidHex)
            put("apduResponseHex", apduResponseHex)
            put("ndefSummary", ndefSummary)
            put("techList", techArr)
            put("notes", notes)
            if (iconKey.isNotEmpty()) {
                put("iconKey", iconKey)
            }
        }.toString()
    }

    companion object {
        fun fromJsonString(json: String): CardSecretsPayload {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("techList")
            val techs = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        add(arr.optString(i))
                    }
                }
            }
            return CardSecretsPayload(
                uidHex = obj.optString("uidHex", ""),
                atqaHex = obj.optString("atqaHex", ""),
                sakHex = obj.optString("sakHex", ""),
                historicalBytesHex = obj.optString("historicalBytesHex", ""),
                aidHex = obj.optString("aidHex", ""),
                apduResponseHex = obj.optString("apduResponseHex", ""),
                ndefSummary = obj.optString("ndefSummary", ""),
                techList = techs,
                notes = obj.optString("notes", ""),
                iconKey = obj.optString("iconKey", "")
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class VaultConfigDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val wrappedDekBase64: String = "",
    val wrappedDekIvBase64: String = "",
    val kdfSaltBase64: String = "",
    val kdfIterations: Long = 120000L,
    val pinVerifierBase64: String = "",
    val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
    val schemaVersion: Long = 2L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EncryptedCardDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val category: String = CardCategory.OTHER.name,
    val accentColor: String = CardAccentColor.CYAN.name,
    val primaryTech: String = NfcTechType.ISO_DEP.name,
    val maskedUid: String = "",
    val hceSupported: Boolean = false,
    val activeHce: Boolean = false,
    val encryptedPayloadBase64: String = "",
    val payloadIvBase64: String = "",
    val cryptoVersion: Long = 1L,
    val emulationCount: Long = 0L,
    val lastEmulatedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedAccessCard(
    val id: String,
    val label: String,
    val category: CardCategory,
    val accentColor: CardAccentColor,
    val primaryTech: NfcTechType,
    val maskedUid: String,
    val hceSupported: Boolean,
    val activeHce: Boolean,
    val emulationCount: Long,
    val lastEmulatedAt: Timestamp?,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?,
    val secrets: CardSecretsPayload,
    val decryptionFailed: Boolean = false
)

data class ActiveHceCardConfig(
    val cardId: String,
    val cardLabel: String,
    val aidHex: String,
    val apduResponseHex: String,
    val requireVaultUnlocked: Boolean
) {
    fun toJsonString(): String = JSONObject().apply {
        put("cardId", cardId)
        put("cardLabel", cardLabel)
        put("aidHex", aidHex)
        put("apduResponseHex", apduResponseHex)
        put("requireVaultUnlocked", requireVaultUnlocked)
    }.toString()

    companion object {
        fun fromJsonString(json: String): ActiveHceCardConfig? = runCatching {
            val obj = JSONObject(json)
            ActiveHceCardConfig(
                cardId = obj.optString("cardId", ""),
                cardLabel = obj.optString("cardLabel", ""),
                aidHex = obj.optString("aidHex", ""),
                apduResponseHex = obj.optString("apduResponseHex", ""),
                requireVaultUnlocked = obj.optBoolean("requireVaultUnlocked", false)
            )
        }.getOrNull()
    }
}

data class HceTapEvent(
    val timestampMillis: Long,
    val cardLabel: String,
    val aidHex: String,
    val commandHex: String,
    val statusWordHex: String,
    val accepted: Boolean
)

/**
 * How the DEK is sealed locally for quick unlock.
 *
 * - [RSA_CRYPTO_OBJECT]: RSA key requiring AUTH_BIOMETRIC_STRONG per use (strong biometrics).
 * - [RSA_DEVICE_CREDENTIAL]: RSA key requiring biometrics or device credential per use (API 30+).
 * - [AES_KEYSTORE]: legacy device-bound AES without user-auth binding. Read-only; upgraded on
 *   the next successful PIN unlock and never written for new seals.
 */
enum class BiometricSealMode {
    RSA_CRYPTO_OBJECT,
    RSA_DEVICE_CREDENTIAL,
    AES_KEYSTORE;

    val isAuthBound: Boolean
        get() = this == RSA_CRYPTO_OBJECT || this == RSA_DEVICE_CREDENTIAL
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
