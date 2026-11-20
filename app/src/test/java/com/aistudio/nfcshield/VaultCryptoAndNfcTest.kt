package com.aistudio.nfcshield

import androidx.test.core.app.ApplicationProvider
import com.aistudio.nfcshield.crypto.Aes256GcmCryptoUtil
import com.aistudio.nfcshield.crypto.VaultCryptoEngine
import com.aistudio.nfcshield.data.model.ActiveHceCardConfig
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.nfc.AccessCardHceService
import com.aistudio.nfcshield.nfc.HceAidManager
import com.aistudio.nfcshield.nfc.HceRuntimeBus
import com.aistudio.nfcshield.nfc.NfcHardwareStatus
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.aistudio.nfcshield.nfc.NfcTechDebugEntry
import com.aistudio.nfcshield.nfc.ScannedNfcTagInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultCryptoAndNfcTest {

    @Test
    fun `pbkdf2 wrap and unwrap dek roundtrip succeeds with correct pin and fails with wrong pin`() {
        val dek = VaultCryptoEngine.generateDek()
        val wrapped = VaultCryptoEngine.wrapDekWithPin(dek, "246810".toCharArray())

        val unwrapped = VaultCryptoEngine.unwrapDekWithPin("246810".toCharArray(), wrapped)
        assertNotNull(unwrapped)
        assertArrayEquals(dek, unwrapped)

        val wrong = VaultCryptoEngine.unwrapDekWithPin("111111".toCharArray(), wrapped)
        assertNull(wrong)
    }

    @Test
    fun `aes gcm encrypt and decrypt card secrets preserves all fields`() {
        val dek = VaultCryptoEngine.generateDek()
        val secrets = CardSecretsPayload(
            uidHex = "04:A1:B2:C3",
            atqaHex = "0004",
            sakHex = "20",
            historicalBytesHex = "80318065B0",
            aidHex = "F04E464353484945",
            apduResponseHex = "4B45593031",
            ndefSummary = "Gate A",
            techList = listOf("IsoDep", "NfcA"),
            notes = "公司北门",
            iconKey = "SHIELD"
        )

        val encrypted = VaultCryptoEngine.encryptCardSecrets(dek, secrets)
        val decrypted = VaultCryptoEngine.decryptCardSecrets(
            dek = dek,
            encryptedBase64 = encrypted.encryptedPayloadBase64,
            ivBase64 = encrypted.payloadIvBase64
        )

        assertEquals(secrets, decrypted)
        assertEquals("SHIELD", decrypted.iconKey)
    }

    @Test
    fun `nfc tag parser aid and masked uid helpers behave correctly`() {
        assertTrue(NfcTagParser.isValidAidHex("F04E464353484945"))
        assertTrue(NfcTagParser.isValidAidHex("A0000002471001"))
        assertFalse(NfcTagParser.isValidAidHex("1234"))
        assertFalse(NfcTagParser.isValidAidHex("F04E464"))

        assertEquals("•••• B2C3", NfcTagParser.buildMaskedUidFromHexString("04:A1:B2:C3"))
        assertArrayEquals(
            byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00),
            NfcTagParser.hexStringToBytes("00 A4 04 00")
        )
    }

    @Test
    fun `nfc debug report and HceRuntimeBus record tag tech stacks accurately`() {
        HceRuntimeBus.clearDebugLogs()
        val entries = listOf(
            NfcTechDebugEntry(
                shortName = "IsoDep",
                fullClassName = "android.nfc.tech.IsoDep",
                details = listOf("MaxTransceive" to "261 bytes", "SELECT NDEF SW" to "6A82")
            ),
            NfcTechDebugEntry(
                shortName = "NfcA",
                fullClassName = "android.nfc.tech.NfcA",
                details = listOf("ATQA" to "0004", "SAK" to "0x20")
            )
        )
        val hint = NfcTagParser.buildDiagnosticHint(
            primaryTech = NfcTechType.ISO_DEP,
            readableTechs = listOf("IsoDep", "NfcA"),
            techEntries = entries
        )
        assertTrue(hint.contains("IsoDep"))

        val tagInfo = ScannedNfcTagInfo(
            uidHex = "04:11:22:33",
            maskedUid = "•••• 2233",
            primaryTech = NfcTechType.ISO_DEP,
            techList = listOf("IsoDep", "NfcA"),
            atqaHex = "0004",
            sakHex = "20",
            historicalBytesHex = "8031",
            aidHex = "F04E464353484945",
            ndefSummary = "",
            mifareSummary = "",
            hceSupported = true,
            uidLengthBytes = 4,
            rawTechList = listOf("android.nfc.tech.IsoDep", "android.nfc.tech.NfcA"),
            techEntries = entries,
            diagnosticSummary = hint,
            captureSource = "ReaderCallback"
        )
        HceRuntimeBus.recordParsedTag(tagInfo)
        assertEquals(1, HceRuntimeBus.scannedTagLogs.value.size)

        val status = NfcHardwareStatus(
            nfcSupported = true,
            nfcEnabled = true,
            hceSupported = true,
            registeredAids = listOf("F04E464353484945"),
            hceFSupported = false,
            hceSelectionMode = "优先默认服务",
            hceDefaultService = true,
            deviceSummary = "Samsung SM-S9260 · Android 14 (API 34)",
            nfcPermissionDeclared = true,
            nfcPermissionGranted = true,
            hceBindServiceDeclared = true,
            adapterStateLabel = "已开启 (STATE_ON)"
        )
        val report = NfcTagParser.formatDebugReport(status, HceRuntimeBus.scannedTagLogs.value)
        assertTrue(report.contains("android.nfc.tech.IsoDep"))
        assertTrue(report.contains("04:11:22:33"))
        assertTrue(report.contains("HCE 卡模拟支持: true"))
        assertTrue(report.contains("Manifest NFC 权限声明: true"))
        assertTrue(report.contains("NFC 运行时授权"))

        HceRuntimeBus.clearDebugLogs()
        assertTrue(HceRuntimeBus.scannedTagLogs.value.isEmpty())
    }

    @Test
    fun `hardware status detects manifest nfc permission and hce bind service`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val initialStatus = HceAidManager.getHardwareStatus(app)
        assertTrue(initialStatus.nfcPermissionDeclared)
        assertFalse(initialStatus.nfcPermissionGranted)
        assertTrue(initialStatus.hceBindServiceDeclared)

        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.NFC)
        val grantedStatus = HceAidManager.getHardwareStatus(app)
        assertTrue(grantedStatus.nfcPermissionDeclared)
        assertTrue(grantedStatus.nfcPermissionGranted)
        assertTrue(grantedStatus.hceBindServiceDeclared)
    }

    @Test
    fun `aes256 gcm utility enforces 256bit keys, unique ivs, tamper detection, and owner aad binding`() {
        val dek = Aes256GcmCryptoUtil.generateKey256()
        assertEquals(32, dek.size)

        val rawNfcDump = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0x90.toByte(), 0x00)
        val enc1 = Aes256GcmCryptoUtil.encryptBytes(dek, rawNfcDump)
        val enc2 = Aes256GcmCryptoUtil.encryptBytes(dek, rawNfcDump)

        // Fresh 96-bit IV per encryption guarantees semantic security
        assertFalse(enc1.ivBase64 == enc2.ivBase64)
        assertFalse(enc1.ciphertextBase64 == enc2.ciphertextBase64)
        assertArrayEquals(rawNfcDump, Aes256GcmCryptoUtil.decryptBytes(dek, enc1.ciphertextBase64, enc1.ivBase64))

        // Invalid key size rejected
        var invalidKeyRejected = false
        try {
            Aes256GcmCryptoUtil.encryptBytes(ByteArray(16), rawNfcDump)
        } catch (_: IllegalArgumentException) {
            invalidKeyRejected = true
        }
        assertTrue(invalidKeyRejected)

        // Tampered ciphertext rejected by 128-bit GCM auth tag
        val rawCipher = Aes256GcmCryptoUtil.decodeBase64(enc1.ciphertextBase64)
        rawCipher[0] = (rawCipher[0].toInt() xor 0xFF).toByte()
        val tamperedBase64 = Aes256GcmCryptoUtil.encodeBase64(rawCipher)
        var tamperDetected = false
        try {
            Aes256GcmCryptoUtil.decryptBytes(dek, tamperedBase64, enc1.ivBase64)
        } catch (_: java.security.GeneralSecurityException) {
            tamperDetected = true
        }
        assertTrue(tamperDetected)

        // AAD binds Firestore NFC card payload strictly to ownerUserId
        val secrets = CardSecretsPayload(
            uidHex = "04:AA:BB:CC",
            aidHex = "F04E464353484945",
            techList = listOf("IsoDep", "NfcA"),
            notes = "Zero-knowledge payload"
        )
        val ownerBound = Aes256GcmCryptoUtil.encryptNfcCardPayload(dek, secrets, ownerUserId = "uid_alice")
        assertEquals(
            secrets,
            Aes256GcmCryptoUtil.decryptNfcCardPayload(
                dek256 = dek,
                encryptedBase64 = ownerBound.encryptedPayloadBase64,
                ivBase64 = ownerBound.payloadIvBase64,
                ownerUserId = "uid_alice"
            )
        )

        // Transplanting ciphertext to another user must fail GCM tag verification
        var crossUserRejected = false
        try {
            Aes256GcmCryptoUtil.decryptNfcCardPayload(
                dek256 = dek,
                encryptedBase64 = ownerBound.encryptedPayloadBase64,
                ivBase64 = ownerBound.payloadIvBase64,
                ownerUserId = "uid_mallory"
            )
        } catch (_: java.security.GeneralSecurityException) {
            crossUserRejected = true
        }
        assertTrue(crossUserRejected)

        // Zeroization clears sensitive key bytes
        Aes256GcmCryptoUtil.zeroize(dek)
        assertTrue(dek.all { it == 0.toByte() })
    }

    @Test
    fun `hce service evaluates SELECT AID, READ BINARY, GET DATA, and security status words`() {
        val activeCard = ActiveHceCardConfig(
            cardId = "card-1",
            cardLabel = "东门门禁",
            aidHex = "F04E464353484945",
            apduResponseHex = "4B45593031",
            requireVaultUnlocked = true
        )

        // SELECT AID 00 A4 04 00 08 F04E464353484945
        val selectCmd = NfcTagParser.hexStringToBytes("00A4040008F04E464353484945")

        // Locked vault returns 6985
        val lockedRes = AccessCardHceService.evaluateCommandApdu(selectCmd, activeCard, vaultUnlocked = false)
        assertFalse(lockedRes.accepted)
        assertEquals("6985", lockedRes.statusWordHex)

        // Unlocked vault matches AID and returns payload + 9000
        val okRes = AccessCardHceService.evaluateCommandApdu(selectCmd, activeCard, vaultUnlocked = true)
        assertTrue(okRes.accepted)
        assertEquals("9000", okRes.statusWordHex)
        assertEquals("4B455930319000", NfcTagParser.bytesToRawHex(okRes.responseBytes))

        // Unknown AID returns 6A82
        val wrongAidCmd = NfcTagParser.hexStringToBytes("00A4040006112233445566")
        val wrongAidRes = AccessCardHceService.evaluateCommandApdu(wrongAidCmd, activeCard, vaultUnlocked = true)
        assertFalse(wrongAidRes.accepted)
        assertEquals("6A82", wrongAidRes.statusWordHex)

        // READ BINARY with offset=1 and Le=2 returns 4559 + 9000
        val readBinaryCmd = NfcTagParser.hexStringToBytes("00B0000102")
        val readRes = AccessCardHceService.evaluateCommandApdu(readBinaryCmd, activeCard, vaultUnlocked = true)
        assertTrue(readRes.accepted)
        assertEquals("45599000", NfcTagParser.bytesToRawHex(readRes.responseBytes))

        // GET DATA (00 CA 00 00) returns payload + 9000
        val getDataCmd = NfcTagParser.hexStringToBytes("00CA0000")
        val getDataRes = AccessCardHceService.evaluateCommandApdu(getDataCmd, activeCard, vaultUnlocked = true)
        assertTrue(getDataRes.accepted)
        assertEquals("4B455930319000", NfcTagParser.bytesToRawHex(getDataRes.responseBytes))

        // Unsupported INS returns 6D00
        val badInsCmd = NfcTagParser.hexStringToBytes("00FF0000")
        val badInsRes = AccessCardHceService.evaluateCommandApdu(badInsCmd, activeCard, vaultUnlocked = true)
        assertFalse(badInsRes.accepted)
        assertEquals("6D00", badInsRes.statusWordHex)
    }

    @Test
    fun `biometric crypto cipher unseals dek with rsa oaep and rejects invalid ciphertext`() {
        val dek = VaultCryptoEngine.generateDek()
        val kpg = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = kpg.generateKeyPair()
        val oaepSpec = javax.crypto.spec.OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            java.security.spec.MGF1ParameterSpec.SHA1,
            javax.crypto.spec.PSource.PSpecified.DEFAULT
        )

        val encCipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        encCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keyPair.public, oaepSpec)
        val sealedBase64 = Aes256GcmCryptoUtil.encodeBase64(encCipher.doFinal(dek))

        val decCipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        decCipher.init(javax.crypto.Cipher.DECRYPT_MODE, keyPair.private, oaepSpec)
        val unsealed = VaultCryptoEngine.decryptWithUnlockedBiometricCipher(decCipher, sealedBase64)

        assertNotNull(unsealed)
        assertArrayEquals(dek, unsealed)

        // Tampered sealed payload fails safely with null rather than throwing
        val anotherDecrypt = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        anotherDecrypt.init(javax.crypto.Cipher.DECRYPT_MODE, keyPair.private, oaepSpec)
        assertNull(VaultCryptoEngine.decryptWithUnlockedBiometricCipher(anotherDecrypt, "AAAA"))
    }

    @Test
    fun `quick settings tile evaluator maps hardware, nfc toggle, card slot, and lock states accurately`() {
        val baseStatus = NfcHardwareStatus(
            nfcSupported = true,
            nfcEnabled = true,
            hceSupported = true,
            registeredAids = listOf("F04E464353484945")
        )
        val activeCard = ActiveHceCardConfig(
            cardId = "card-qs",
            cardLabel = "公司工卡",
            aidHex = "F04E464353484945",
            apduResponseHex = "4B4559",
            requireVaultUnlocked = false
        )

        // 1. No HCE hardware -> STATE_UNAVAILABLE
        val noHce = com.aistudio.nfcshield.nfc.HceQuickTileService.evaluatePresentation(
            status = baseStatus.copy(hceSupported = false),
            configuredCard = activeCard,
            quickEnabled = true,
            vaultUnlocked = true
        )
        assertEquals(android.service.quicksettings.Tile.STATE_UNAVAILABLE, noHce.tileState)
        assertEquals(
            com.aistudio.nfcshield.nfc.QuickTileAction.UNAVAILABLE,
            com.aistudio.nfcshield.nfc.HceQuickTileService.determineClickAction(
                status = baseStatus.copy(hceSupported = false),
                configuredCard = activeCard,
                quickEnabled = true,
                vaultUnlocked = true
            )
        )

        // 2. NFC off -> STATE_INACTIVE + opens NFC settings
        val nfcOff = com.aistudio.nfcshield.nfc.HceQuickTileService.evaluatePresentation(
            status = baseStatus.copy(nfcEnabled = false),
            configuredCard = activeCard,
            quickEnabled = true,
            vaultUnlocked = true
        )
        assertEquals(android.service.quicksettings.Tile.STATE_INACTIVE, nfcOff.tileState)
        assertEquals(
            com.aistudio.nfcshield.nfc.QuickTileAction.OPEN_NFC_SETTINGS,
            com.aistudio.nfcshield.nfc.HceQuickTileService.determineClickAction(
                status = baseStatus.copy(nfcEnabled = false),
                configuredCard = activeCard,
                quickEnabled = true,
                vaultUnlocked = true
            )
        )

        // 3. Configured card & quick HCE enabled -> STATE_ACTIVE + TOGGLE_OFF on tap
        val running = com.aistudio.nfcshield.nfc.HceQuickTileService.evaluatePresentation(
            status = baseStatus,
            configuredCard = activeCard,
            quickEnabled = true,
            vaultUnlocked = false
        )
        assertEquals(android.service.quicksettings.Tile.STATE_ACTIVE, running.tileState)
        assertEquals("公司工卡", running.label)
        assertEquals(
            com.aistudio.nfcshield.nfc.QuickTileAction.TOGGLE_OFF,
            com.aistudio.nfcshield.nfc.HceQuickTileService.determineClickAction(
                status = baseStatus,
                configuredCard = activeCard,
                quickEnabled = true,
                vaultUnlocked = false
            )
        )

        // 4. Configured card & quick HCE paused -> STATE_INACTIVE + TOGGLE_ON on tap
        val paused = com.aistudio.nfcshield.nfc.HceQuickTileService.evaluatePresentation(
            status = baseStatus,
            configuredCard = activeCard,
            quickEnabled = false,
            vaultUnlocked = true
        )
        assertEquals(android.service.quicksettings.Tile.STATE_INACTIVE, paused.tileState)
        assertEquals(
            com.aistudio.nfcshield.nfc.QuickTileAction.TOGGLE_ON,
            com.aistudio.nfcshield.nfc.HceQuickTileService.determineClickAction(
                status = baseStatus,
                configuredCard = activeCard,
                quickEnabled = false,
                vaultUnlocked = true
            )
        )

        // 5. Require vault unlocked while locked -> OPEN_APP_TO_UNLOCK_OR_SELECT
        assertEquals(
            com.aistudio.nfcshield.nfc.QuickTileAction.OPEN_APP_TO_UNLOCK_OR_SELECT,
            com.aistudio.nfcshield.nfc.HceQuickTileService.determineClickAction(
                status = baseStatus,
                configuredCard = activeCard.copy(requireVaultUnlocked = true),
                quickEnabled = false,
                vaultUnlocked = false
            )
        )

        // 6. Manifest declares HceQuickTileService with BIND_QUICK_SETTINGS_TILE
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val serviceInfo = app.packageManager.getServiceInfo(
            android.content.ComponentName(app, com.aistudio.nfcshield.nfc.HceQuickTileService::class.java),
            android.content.pm.PackageManager.GET_META_DATA
        )
        assertEquals(android.Manifest.permission.BIND_QUICK_SETTINGS_TILE, serviceInfo.permission)
        assertTrue(serviceInfo.exported)
    }

    @Test
    fun `auto lock background policy triggers immediately at 0 min, respects delay, and skips manual mode`() {
        // Immediate lock (autoLockMinutes == 0): locks even at 0ms elapsed
        assertTrue(com.aistudio.nfcshield.ui.viewmodel.VaultViewModel.shouldLockAfterBackground(0, 0L))
        assertTrue(com.aistudio.nfcshield.ui.viewmodel.VaultViewModel.shouldLockAfterBackground(0, 500L))

        // Delayed lock (5 minutes)
        assertFalse(com.aistudio.nfcshield.ui.viewmodel.VaultViewModel.shouldLockAfterBackground(5, 299_999L))
        assertTrue(com.aistudio.nfcshield.ui.viewmodel.VaultViewModel.shouldLockAfterBackground(5, 300_000L))

        // Manual-only lock (-1)
        assertFalse(com.aistudio.nfcshield.ui.viewmodel.VaultViewModel.shouldLockAfterBackground(-1, 3_600_000L))
    }
}
