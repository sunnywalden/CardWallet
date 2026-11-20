package com.aistudio.nfcshield

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.aistudio.nfcshield.crypto.Aes256GcmCryptoUtil
import com.aistudio.nfcshield.crypto.DekUnwrapResult
import com.aistudio.nfcshield.crypto.VaultCryptoEngine
import com.aistudio.nfcshield.crypto.WrappedDekPayload
import com.aistudio.nfcshield.data.local.ActiveHceSealer
import com.aistudio.nfcshield.data.local.LocalVaultStore
import com.aistudio.nfcshield.data.model.ActiveHceCardConfig
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.nfc.AccessCardHceService
import com.aistudio.nfcshield.nfc.HceAids
import com.aistudio.nfcshield.nfc.NfcTagParser
import com.aistudio.nfcshield.ui.viewmodel.VaultViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Regression suite for the remediation plan (docs/remediation-design.md):
 * H1/H4 (KDF), H5 (crypto versioning), H6 (quick toggle), M1 (lockout policy),
 * M2 (counter rule), M5 (AID consistency), M9 (GET DATA semantics).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemediationRegressionTest {

    @Before
    fun installTestDeviceSealer() {
        TestDeviceKeySealer.reset()
        VaultCryptoEngine.deviceKeySealer = TestDeviceKeySealer
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun dek(): ByteArray = ByteArray(32) { (it + 1).toByte() }

    private fun deriveKek(pin: String, salt: ByteArray, iterations: Int, algorithm: String): ByteArray =
        javax.crypto.SecretKeyFactory.getInstance(algorithm)
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256))
            .encoded

    private fun encryptDek(dek: ByteArray, kek: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(dek)
    }

    private fun verifierV2(kek: ByteArray, salt: ByteArray, iterations: Int): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update("shield-nfc-pin-verifier-v2".toByteArray(Charsets.UTF_8))
            update(salt)
            update(java.nio.ByteBuffer.allocate(4).putInt(iterations).array())
            update(VaultCryptoEngine.KDF_SHA256.toByteArray(Charsets.UTF_8))
            update(kek)
            digest()
        }

    // --- H1: new vaults are v3/SHA-256, device-bound, and need no upgrade ---

    @Test
    fun `wrap produces device-bound v3 payload with sha256 and upgrade is not needed`() {
        val wrapped = VaultCryptoEngine.wrapDekWithPin(dek(), "246810".toCharArray())
        assertEquals(VaultCryptoEngine.KDF_SHA256, wrapped.kdfAlgorithm)
        assertEquals(VaultCryptoEngine.CURRENT_SCHEMA_VERSION, wrapped.schemaVersion)
        assertEquals(VaultCryptoEngine.DEFAULT_KDF_ITERATIONS, wrapped.kdfIterations)
        assertEquals(VaultCryptoEngine.DEVICE_BINDING_ANDROID_KEYSTORE, wrapped.deviceBinding)
        assertEquals(VaultCryptoEngine.DEVICE_BOUND_VERIFIER_MARKER, wrapped.pinVerifierBase64)
        assertFalse(VaultCryptoEngine.needsVaultUpgrade(wrapped))
    }

    @Test
    fun `v3 unlock fails closed when the device key is missing or different`() {
        val wrapped = VaultCryptoEngine.wrapDekWithPin(dek(), "246810".toCharArray())

        // Fresh install / different device: the cloud doc alone is useless.
        TestDeviceKeySealer.reset()
        assertEquals(
            DekUnwrapResult.DeviceBindingUnavailable,
            VaultCryptoEngine.unwrapDekResult("246810".toCharArray(), wrapped)
        )

        // Key present but rotated (e.g. Keystore reset): blob no longer authenticates.
        TestDeviceKeySealer.reset()
        VaultCryptoEngine.deviceKeySealer.seal(byteArrayOf(1))
        assertEquals(
            DekUnwrapResult.DeviceBindingCorrupt,
            VaultCryptoEngine.unwrapDekResult("246810".toCharArray(), wrapped)
        )
    }

    // --- Eviction: v1 vault records are no longer readable ---

    @Test
    fun `v1 vault payloads are rejected after eviction`() {
        val d = dek()
        val salt = ByteArray(16) { (it * 3).toByte() }
        val iv = ByteArray(12) { 7 }
        val kek = deriveKek("246810", salt, 100_000, VaultCryptoEngine.KDF_SHA256)
        val wrapped = WrappedDekPayload(
            wrappedDekBase64 = b64(encryptDek(d, kek, iv)),
            wrappedDekIvBase64 = b64(iv),
            kdfSaltBase64 = b64(salt),
            kdfIterations = 100_000,
            pinVerifierBase64 = b64(verifierV2(kek, salt, 100_000)),
            kdfAlgorithm = VaultCryptoEngine.KDF_SHA256,
            schemaVersion = 1
        )
        assertEquals(
            DekUnwrapResult.UnsupportedFormat,
            VaultCryptoEngine.unwrapDekResult("246810".toCharArray(), wrapped)
        )
        assertNull(VaultCryptoEngine.unwrapDekWithPin("246810".toCharArray(), wrapped))
    }

    // --- v2 migration source remains readable and requests a device-binding upgrade ---

    @Test
    fun `v2 payload unlocks, roundtrips and requests device binding upgrade`() {
        val d = dek()
        val salt = ByteArray(16) { (it * 5).toByte() }
        val iv = ByteArray(12) { 9 }
        val kek = deriveKek("246810", salt, 120_000, VaultCryptoEngine.KDF_SHA256)
        val wrapped = WrappedDekPayload(
            wrappedDekBase64 = b64(encryptDek(d, kek, iv)),
            wrappedDekIvBase64 = b64(iv),
            kdfSaltBase64 = b64(salt),
            kdfIterations = 120_000,
            pinVerifierBase64 = b64(verifierV2(kek, salt, 120_000)),
            kdfAlgorithm = VaultCryptoEngine.KDF_SHA256,
            schemaVersion = 2
        )
        assertArrayEquals(d, VaultCryptoEngine.unwrapDekWithPin("246810".toCharArray(), wrapped))
        assertNull(VaultCryptoEngine.unwrapDekWithPin("111111".toCharArray(), wrapped))
        assertTrue(VaultCryptoEngine.needsVaultUpgrade(wrapped))
    }

    // --- H4: parameter tampering is rejected (verifier binds salt/iterations/algorithm) ---

    @Test
    fun `payload with downgraded iterations or unsupported algorithm is rejected`() {
        val wrapped = VaultCryptoEngine.wrapDekWithPin(dek(), "246810".toCharArray(), bindToDevice = false)

        assertNull(
            VaultCryptoEngine.unwrapDekWithPin(
                "246810".toCharArray(),
                wrapped.copy(kdfIterations = 10_000)
            )
        )
        assertNull(
            VaultCryptoEngine.unwrapDekWithPin(
                "246810".toCharArray(),
                wrapped.copy(kdfAlgorithm = "PBKDF2WithHmacSHA1")
            )
        )
    }

    // --- H5: crypto version selects AAD explicitly, no blind fallback, v1 evicted ---

    @Test
    fun `v2 ciphertext cannot be decrypted without v2 declaration and unknown versions are rejected`() {
        val d = dek()
        val secrets = CardSecretsPayload(uidHex = "04:AA:BB:CC", notes = "gate")
        val enc = VaultCryptoEngine.encryptCardSecrets(d, secrets, ownerUserId = "uid_alice")

        // v1 was evicted: the version is rejected outright instead of retrying without AAD.
        assertTrue(
            runCatching {
                VaultCryptoEngine.decryptCardSecrets(
                    dek = d,
                    encryptedBase64 = enc.encryptedPayloadBase64,
                    ivBase64 = enc.payloadIvBase64,
                    ownerUserId = "uid_alice",
                    cryptoVersion = 1
                )
            }.isFailure
        )

        // Unknown versions fail closed with a clear error instead of a fallback attempt.
        assertTrue(
            runCatching {
                VaultCryptoEngine.decryptCardSecrets(
                    dek = d,
                    encryptedBase64 = enc.encryptedPayloadBase64,
                    ivBase64 = enc.payloadIvBase64,
                    ownerUserId = "uid_alice",
                    cryptoVersion = 3
                )
            }.isFailure
        )
    }

    @Test
    fun `v1 ciphertext is rejected after eviction`() {
        val d = dek()
        val secrets = CardSecretsPayload(uidHex = "04:11:22:33", aidHex = "F04E464353484945")
        val legacy = Aes256GcmCryptoUtil.encryptNfcCardPayload(d, secrets, ownerUserId = null)
        assertTrue(
            runCatching {
                Aes256GcmCryptoUtil.decryptNfcCardPayload(
                    dek256 = d,
                    encryptedBase64 = legacy.encryptedPayloadBase64,
                    ivBase64 = legacy.payloadIvBase64,
                    ownerUserId = "uid_alice",
                    cryptoVersion = 1
                )
            }.isFailure
        )
    }

    // --- H6: state synchronization never resurrects a paused HCE slot ---

    /** Passthrough sealer: Robolectric has no AndroidKeyStore backend. */
    private object PlainTestSealer : ActiveHceSealer {
        override fun seal(plaintext: ByteArray): Pair<String, String> =
            Base64.encodeToString(plaintext, Base64.NO_WRAP) to
                Base64.encodeToString(byteArrayOf(0), Base64.NO_WRAP)
        override fun unseal(cipherBase64: String, ivBase64: String): ByteArray? =
            Base64.decode(cipherBase64, Base64.NO_WRAP)
    }

    @Test
    fun `sealing active card never flips the quick toggle and pause survives resync`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = LocalVaultStore(app, PlainTestSealer)
        store.clearSensitiveLocalSession()

        val cfg = ActiveHceCardConfig(
            cardId = "card-1",
            cardLabel = "东门",
            aidHex = "F04E464353484945",
            apduResponseHex = "4B4559",
            requireVaultUnlocked = false
        )

        // Fail-closed default: nothing enabled until the user opts in.
        assertFalse(store.isQuickHceEnabled())

        store.sealActiveHceCard(cfg)
        assertFalse("sealing must not enable quick HCE", store.isQuickHceEnabled())
        assertNull(store.readActiveHceCard())

        store.setQuickHceEnabled(true)
        assertEquals(cfg, store.readActiveHceCard())

        // User pauses via the tile; a snapshot-driven resync re-seals the same config.
        store.setQuickHceEnabled(false)
        store.sealActiveHceCard(cfg)
        assertFalse("resync must not resume a paused slot", store.isQuickHceEnabled())
        assertNull(store.readActiveHceCard())

        // Explicit activation path still works.
        store.saveActiveHceCard(cfg)
        assertTrue(store.isQuickHceEnabled())
        assertEquals(cfg, store.readActiveHceCard())

        // Clearing removes the slot and the toggle.
        store.saveActiveHceCard(null)
        assertFalse(store.isQuickHceEnabled())
        assertNull(store.readConfiguredHceCard())

        store.clearSensitiveLocalSession()
    }

    @Test
    fun `sign out clears security preferences for the next account`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = LocalVaultStore(app)
        store.setBiometricEnabled(false)
        store.setAutoLockMinutes(-1)
        store.setRequireVaultUnlockedForHce(true)

        store.clearSensitiveLocalSession()

        val prefs = store.preferencesFlow.first()
        assertTrue(prefs.biometricEnabled)
        assertEquals(5, prefs.autoLockMinutes)
        assertFalse(prefs.requireVaultUnlockedForHce)
    }

    // --- M1: lockout escalation policy ---

    @Test
    fun `lockout escalates every five failures and caps at thirty minutes`() {
        assertEquals(0, VaultViewModel.lockoutSecondsFor(0))
        assertEquals(0, VaultViewModel.lockoutSecondsFor(4))
        assertEquals(30, VaultViewModel.lockoutSecondsFor(5))
        assertEquals(0, VaultViewModel.lockoutSecondsFor(6))
        assertEquals(300, VaultViewModel.lockoutSecondsFor(10))
        assertEquals(1800, VaultViewModel.lockoutSecondsFor(15))
        assertEquals(1800, VaultViewModel.lockoutSecondsFor(40))
    }

    // --- M5: AID source of truth matches the static manifest list ---

    @Test
    fun `hce aids builtin set matches static apduservice xml`() {
        val xml = File("src/main/res/xml/apduservice.xml").readText()
        val ids = Regex("aid-filter android:name=\"([0-9A-Fa-f]+)\"")
            .findAll(xml)
            .map { it.groupValues[1].uppercase() }
            .toSet()
        assertEquals(HceAids.BUILTIN, ids)
    }

    @Test
    fun `dynamic aid registration keeps builtin aids`() {
        val registered = HceAids.registeredAidsFor("A1B2C3D4E5F6")
        assertTrue(registered.containsAll(HceAids.BUILTIN))
        assertTrue(registered.contains("A1B2C3D4E5F6"))
        assertEquals(registered.size, registered.distinct().size)
    }

    // --- M9: GET DATA only answers the tag-less form ---

    @Test
    fun `get data with non zero tag returns 6A88 instead of leaking payload`() {
        val card = ActiveHceCardConfig(
            cardId = "c",
            cardLabel = "门禁",
            aidHex = "F04E464353484945",
            apduResponseHex = "4B4559",
            requireVaultUnlocked = false
        )
        val tagged = AccessCardHceService.evaluateCommandApdu(
            NfcTagParser.hexStringToBytes("00CA9F7F00"),
            card,
            vaultUnlocked = true
        )
        assertFalse(tagged.accepted)
        assertEquals("6A88", tagged.statusWordHex)

        val untagged = AccessCardHceService.evaluateCommandApdu(
            NfcTagParser.hexStringToBytes("00CA0000"),
            card,
            vaultUnlocked = true
        )
        assertTrue(untagged.accepted)
        assertEquals("9000", untagged.statusWordHex)
    }
}