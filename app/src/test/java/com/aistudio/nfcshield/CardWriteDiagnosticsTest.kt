package com.aistudio.nfcshield

import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.data.remote.VALID_DOC_ID_REGEX
import com.aistudio.nfcshield.data.remote.buildCardSaveLogMessage
import com.aistudio.nfcshield.data.remote.buildRedactedCardDiagnostic
import com.aistudio.nfcshield.data.remote.hashUidPrefix
import com.aistudio.nfcshield.data.remote.redactUidInPath
import com.aistudio.nfcshield.data.remote.validateCardWriteFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardWriteDiagnosticsTest {

    private val validCipher = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/=="
    private val validIv = "MTIzNDU2Nzg5MDEy"

    @Test
    fun validateCardWriteFields_acceptsValidTransitCardPayload() {
        val violations = validateCardWriteFields(
            userId = "firebase-user-12345",
            label = "长安通",
            category = CardCategory.TRANSIT,
            accentColor = CardAccentColor.ROSE,
            primaryTech = NfcTechType.ISO_DEP,
            maskedUid = "D1:**:**:BD",
            encryptedPayloadBase64 = validCipher,
            payloadIvBase64 = validIv,
            cryptoVersion = 2,
            isCreate = true
        )

        assertTrue("Expected no violations, got: $violations", violations.isEmpty())
    }

    @Test
    fun validateCardWriteFields_rejectsMissingAndOutOfBoundsRequiredFields() {
        val violations = validateCardWriteFields(
            userId = "   ",
            label = "",
            category = CardCategory.TRANSIT,
            accentColor = CardAccentColor.ROSE,
            primaryTech = NfcTechType.ISO_DEP,
            maskedUid = "A".repeat(25),
            encryptedPayloadBase64 = "short",
            payloadIvBase64 = "iv",
            cryptoVersion = 1,
            isCreate = true
        )

        assertEquals(6, violations.size)
        assertTrue(violations.any { it.contains("userId") })
        assertTrue(violations.any { it.contains("label") })
        assertTrue(violations.any { it.contains("maskedUid") })
        assertTrue(violations.any { it.contains("encryptedPayloadBase64") })
        assertTrue(violations.any { it.contains("payloadIvBase64") })
        assertTrue(violations.any { it.contains("cryptoVersion") })
    }

    @Test
    fun validateCardWriteFields_allowsCryptoVersion1OnUpdateOnly() {
        val updateV1 = validateCardWriteFields(
            userId = "user-1",
            label = "小区门禁",
            category = CardCategory.RESIDENCE,
            accentColor = CardAccentColor.CYAN,
            primaryTech = NfcTechType.NFC_A,
            maskedUid = "04:**:**:AA",
            encryptedPayloadBase64 = validCipher,
            payloadIvBase64 = validIv,
            cryptoVersion = 1,
            isCreate = false
        )
        assertTrue(updateV1.isEmpty())

        val updateV3 = validateCardWriteFields(
            userId = "user-1",
            label = "小区门禁",
            category = CardCategory.RESIDENCE,
            accentColor = CardAccentColor.CYAN,
            primaryTech = NfcTechType.NFC_A,
            maskedUid = "04:**:**:AA",
            encryptedPayloadBase64 = validCipher,
            payloadIvBase64 = validIv,
            cryptoVersion = 3,
            isCreate = false
        )
        assertEquals(1, updateV3.size)
        assertTrue(updateV3.first().contains("cryptoVersion"))
    }

    @Test
    fun validDocIdRegex_matchesFirestoreRuleConstraint() {
        assertTrue(VALID_DOC_ID_REGEX.matches("abcDEF_012-xyz"))
        assertFalse(VALID_DOC_ID_REGEX.matches(""))
        assertFalse(VALID_DOC_ID_REGEX.matches("bad/id"))
        assertFalse(VALID_DOC_ID_REGEX.matches("a".repeat(129)))
    }

    @Test
    fun buildRedactedCardDiagnostic_redactsRawUidAndIncludesSchemaMetadata() {
        val rawUid = "sensitive-firebase-uid-9999"
        val summary = buildRedactedCardDiagnostic(
            op = "createCardAndActivate",
            expectedUserId = rawUid,
            authUid = rawUid,
            cardId = "(auto-batch)",
            labelLen = 3,
            category = CardCategory.TRANSIT,
            accentColor = CardAccentColor.ROSE,
            primaryTech = NfcTechType.ISO_DEP,
            maskedUidLen = 11,
            hceSupported = true,
            activeHce = true,
            encryptedPayloadLen = 188,
            payloadIvLen = 16,
            cryptoVersion = 2,
            deactivateCount = 1,
            emulationCount = 0L
        )

        assertFalse("Raw Firebase UID must never appear in logs", summary.contains(rawUid))
        assertTrue(summary.contains("op=createCardAndActivate"))
        assertTrue(summary.contains("signedIn=true"))
        assertTrue(summary.contains("uidMatch=true"))
        assertTrue(summary.contains("category=TRANSIT"))
        assertTrue(summary.contains("accentColor=ROSE"))
        assertTrue(summary.contains("primaryTech=ISO_DEP"))
        assertTrue(summary.contains("cryptoVersion=2"))
        assertTrue(summary.contains("emulationCount=0"))
        assertTrue(summary.contains("deactivateCount=1"))
    }

    @Test
    fun redactUidInPath_replacesRawUidWithHashAndKeepsStructure() {
        val rawUid = "sensitive-firebase-uid-9999"
        val redacted = redactUidInPath("users/$rawUid/cards/card123", rawUid, rawUid)

        assertFalse("Raw Firebase UID must never appear in paths", redacted.contains(rawUid))
        assertTrue(redacted.contains(hashUidPrefix(rawUid)))
        assertTrue(redacted.endsWith("/cards/card123"))
    }

    @Test
    fun redactUidInPath_fallsBackToAuthUidAndLeavesUidFreePathsAlone() {
        val rawUid = "sensitive-firebase-uid-9999"

        assertEquals(
            "users/${hashUidPrefix(rawUid)}/cards",
            redactUidInPath("users/$rawUid/cards", expectedUserId = "", authUid = rawUid)
        )
        // Nothing to redact: the path carries no identifier (e.g. early validation failures).
        assertEquals(
            "users/unknown/cards",
            redactUidInPath("users/unknown/cards", expectedUserId = "  ", authUid = null)
        )
    }

    @Test
    fun cardSaveLogMessage_neverLogsRawUidAndOnlyIncludesMessageInDebug() {
        val rawUid = "sensitive-firebase-uid-9999"
        val redactedPath = redactUidInPath("users/$rawUid/cards", rawUid, rawUid)

        val release = buildCardSaveLogMessage(
            codeName = "PERMISSION_DENIED",
            path = redactedPath,
            summary = "op=createCard, cardId=(auto)",
            exceptionMessage = "raw failure mentioning users/$rawUid",
            isDebug = false
        )
        assertFalse("Raw UID must not leak through the path", release.contains(rawUid))
        assertFalse("release builds must not log raw exception messages", release.contains("raw failure"))
        assertTrue(release.contains("code=PERMISSION_DENIED"))
        assertTrue(release.contains(hashUidPrefix(rawUid)))

        val debug = buildCardSaveLogMessage(
            codeName = "PERMISSION_DENIED",
            path = redactedPath,
            summary = "op=createCard, cardId=(auto)",
            exceptionMessage = "boom",
            isDebug = true
        )
        assertTrue(debug.contains("message=boom"))
        assertFalse(debug.contains(rawUid))
    }
}
