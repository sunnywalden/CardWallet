package com.aistudio.nfcshield

import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.MAX_TAGS_PER_CARD
import com.aistudio.nfcshield.data.model.MAX_TAG_LENGTH
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.data.model.normalizeCardTags
import com.aistudio.nfcshield.data.remote.VALID_DOC_ID_REGEX
import com.aistudio.nfcshield.data.remote.buildRedactedCardDiagnostic
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
    fun normalizeCardTags_trimsStripsHashDeduplicatesAndCapsLengthAndCount() {
        val raw = listOf(
            "  #高频  ",
            "通勤",
            "高频",
            "###",
            "   ",
            "超长标签名称测试一二三四五六",
            "办公",
            "家门",
            "物业",
            "备用",
            "超出上限标签"
        )
        val normalized = normalizeCardTags(raw)

        assertEquals(MAX_TAGS_PER_CARD, normalized.size)
        assertEquals(
            listOf("高频", "通勤", "超长标签名称测试一二三四", "办公", "家门", "物业"),
            normalized
        )
        assertTrue(normalized.all { it.length <= MAX_TAG_LENGTH })
    }
}
