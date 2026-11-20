package com.aistudio.nfcshield.data.remote

import android.util.Log
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.NfcTechType
import com.google.firebase.firestore.FirebaseFirestoreException
import java.security.MessageDigest

internal const val CARD_SAVE_LOG_TAG = "NfcCardSave"
internal val VALID_DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

/**
 * Validates every required non-null field and boundary enforced by `firestore.rules`
 * (`isValidAccessCard`, `allow create`, `allow update`) before sending a write to Firestore.
 *
 * Returns an empty list when all constraints are satisfied, or descriptive violation codes
 * for immediate pre-flight failure and diagnostic logging.
 */
internal fun validateCardWriteFields(
    userId: String,
    label: String,
    category: CardCategory,
    accentColor: CardAccentColor,
    primaryTech: NfcTechType,
    maskedUid: String,
    encryptedPayloadBase64: String,
    payloadIvBase64: String,
    cryptoVersion: Int,
    isCreate: Boolean
): List<String> = buildList {
    if (userId.isBlank()) {
        add("userId 缺失或为空")
    }
    if (label.isBlank() || label.length > 40) {
        add("label 长度无效 (${label.length}, 要求 1..40)")
    }
    if (category.name.isBlank()) {
        add("category 缺失")
    }
    if (accentColor.name.isBlank()) {
        add("accentColor 缺失")
    }
    if (primaryTech.name.isBlank()) {
        add("primaryTech 缺失")
    }
    if (maskedUid.length > 24) {
        add("maskedUid 超长 (${maskedUid.length}, 上限 24)")
    }
    if (encryptedPayloadBase64.length !in 16..8192) {
        add("encryptedPayloadBase64 长度越界 (${encryptedPayloadBase64.length}, 要求 16..8192)")
    }
    if (payloadIvBase64.length !in 8..64) {
        add("payloadIvBase64 长度越界 (${payloadIvBase64.length}, 要求 8..64)")
    }
    if (isCreate) {
        if (cryptoVersion != 2) {
            add("新建卡片 cryptoVersion 必须为 2 (实际=$cryptoVersion)")
        }
    } else {
        if (cryptoVersion !in 1..2) {
            add("更新卡片 cryptoVersion 越界 (实际=$cryptoVersion, 要求 1..2)")
        }
    }
}

/**
 * Produces a PII-redacted diagnostic summary of an NFC card write payload.
 * Never logs raw email, raw Firebase UID, raw NFC UID/AID/APDU bytes, or plaintext notes.
 */
internal fun buildRedactedCardDiagnostic(
    op: String,
    expectedUserId: String,
    authUid: String?,
    cardId: String,
    labelLen: Int,
    category: CardCategory,
    accentColor: CardAccentColor,
    primaryTech: NfcTechType,
    maskedUidLen: Int,
    hceSupported: Boolean,
    activeHce: Boolean,
    encryptedPayloadLen: Int,
    payloadIvLen: Int,
    cryptoVersion: Int,
    deactivateCount: Int,
    emulationCount: Long? = null
): String {
    val authHash = authUid?.let(::hashUidPrefix) ?: "null"
    val expectedHash = expectedUserId.takeIf { it.isNotBlank() }?.let(::hashUidPrefix) ?: "blank"
    val uidMatch = authUid != null && authUid == expectedUserId
    val emulationText = emulationCount?.toString() ?: "unchanged"
    return "op=$op, cardId=$cardId, signedIn=${authUid != null}, uidMatch=$uidMatch, " +
        "authUidHash=$authHash, expectedUidHash=$expectedHash, " +
        "labelLen=$labelLen, category=${category.name}, accentColor=${accentColor.name}, " +
        "primaryTech=${primaryTech.name}, maskedUidLen=$maskedUidLen, " +
        "hceSupported=$hceSupported, activeHce=$activeHce, " +
        "encryptedPayloadLen=$encryptedPayloadLen, payloadIvLen=$payloadIvLen, " +
        "cryptoVersion=$cryptoVersion, emulationCount=$emulationText, deactivateCount=$deactivateCount"
}

internal fun logCardSaveException(
    op: String,
    path: String,
    expectedUserId: String,
    authUid: String?,
    cardId: String,
    labelLen: Int,
    category: CardCategory,
    accentColor: CardAccentColor,
    primaryTech: NfcTechType,
    maskedUidLen: Int,
    hceSupported: Boolean,
    activeHce: Boolean,
    encryptedPayloadLen: Int,
    payloadIvLen: Int,
    cryptoVersion: Int,
    deactivateCount: Int,
    exception: Throwable,
    emulationCount: Long? = null
) {
    val rootFirestore = generateSequence(exception) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    val codeName = rootFirestore?.code?.name ?: exception.javaClass.simpleName
    val summary = buildRedactedCardDiagnostic(
        op = op,
        expectedUserId = expectedUserId,
        authUid = authUid,
        cardId = cardId,
        labelLen = labelLen,
        category = category,
        accentColor = accentColor,
        primaryTech = primaryTech,
        maskedUidLen = maskedUidLen,
        hceSupported = hceSupported,
        activeHce = activeHce,
        encryptedPayloadLen = encryptedPayloadLen,
        payloadIvLen = payloadIvLen,
        cryptoVersion = cryptoVersion,
        deactivateCount = deactivateCount,
        emulationCount = emulationCount
    )
    Log.e(
        CARD_SAVE_LOG_TAG,
        "NFC card save failed [code=$codeName, path=$path]: $summary | message=${exception.message}",
        exception
    )
}

internal fun hashUidPrefix(uid: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(12)
