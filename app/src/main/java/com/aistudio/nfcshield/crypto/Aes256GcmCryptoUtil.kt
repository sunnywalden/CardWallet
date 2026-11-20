package com.aistudio.nfcshield.crypto

import android.util.Base64
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard AES-256-GCM authenticated encryption utility for zero-knowledge
 * client-side encryption of NFC card raw payloads before syncing to Firestore.
 *
 * Security parameters (NIST SP 800-38D):
 * - Algorithm: AES/GCM/NoPadding
 * - Key length: 256 bits (32 bytes)
 * - IV / Nonce length: 96 bits (12 bytes), freshly generated via CSPRNG per encryption
 * - Authentication tag: 128 bits (16 bytes)
 * - Optional Additional Authenticated Data (AAD) to bind ciphertexts to owner/context
 */
object Aes256GcmCryptoUtil {
    const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    const val AES_ALGORITHM = "AES"
    const val KEY_LENGTH_BITS = 256
    const val KEY_LENGTH_BYTES = 32
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_TAG_LENGTH_BYTES = 16

    const val CARD_AAD_PREFIX = "shield-nfc:card:v2:"
    const val CURRENT_CRYPTO_VERSION = 2
    /**
     * v1 (no-AAD) read support was removed on 2026-11-20 after the live audit confirmed
     * 100% of stored cards are v2. Unknown versions fail closed.
     */
    val KNOWN_CRYPTO_VERSIONS = setOf(CURRENT_CRYPTO_VERSION)
    private val secureRandom = SecureRandom()

    data class CipherPayload(
        val ciphertextBase64: String,
        val ivBase64: String,
        val aadApplied: Boolean
    )

    fun generateKey256(): ByteArray = ByteArray(KEY_LENGTH_BYTES).apply {
        secureRandom.nextBytes(this)
    }

    fun generateIv96(): ByteArray = ByteArray(GCM_IV_LENGTH_BYTES).apply {
        secureRandom.nextBytes(this)
    }

    /**
     * Encrypts arbitrary raw bytes (e.g. raw NFC UID/ATQA/APDU frames or serialized JSON)
     * with AES-256-GCM and optional Additional Authenticated Data (AAD).
     */
    fun encryptBytes(
        key256: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): CipherPayload {
        require(key256.size == KEY_LENGTH_BYTES) {
            "AES-256-GCM requires a 32-byte (256-bit) key, got ${key256.size} bytes"
        }
        val iv = generateIv96()
        val keySpec = SecretKeySpec(key256, AES_ALGORITHM)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        val ciphertext = cipher.doFinal(plaintext)
        return CipherPayload(
            ciphertextBase64 = encodeBase64(ciphertext),
            ivBase64 = encodeBase64(iv),
            aadApplied = associatedData != null && associatedData.isNotEmpty()
        )
    }

    /**
     * Decrypts and authenticates an AES-256-GCM ciphertext.
     * Throws [GeneralSecurityException] (e.g. AEADBadTagException) if key, IV, AAD, or ciphertext is tampered.
     */
    fun decryptBytes(
        key256: ByteArray,
        ciphertextBase64: String,
        ivBase64: String,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key256.size == KEY_LENGTH_BYTES) {
            "AES-256-GCM requires a 32-byte (256-bit) key, got ${key256.size} bytes"
        }
        val iv = decodeBase64(ivBase64)
        require(iv.size == GCM_IV_LENGTH_BYTES) {
            "AES-GCM requires a 12-byte (96-bit) IV, got ${iv.size} bytes"
        }
        val ciphertext = decodeBase64(ciphertextBase64)
        require(ciphertext.size >= GCM_TAG_LENGTH_BYTES) {
            "Ciphertext is truncated and missing the 128-bit GCM authentication tag"
        }

        val keySpec = SecretKeySpec(key256, AES_ALGORITHM)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        return cipher.doFinal(ciphertext)
    }

    fun encryptUtf8(
        key256: ByteArray,
        plaintext: String,
        associatedData: ByteArray? = null
    ): CipherPayload = encryptBytes(
        key256 = key256,
        plaintext = plaintext.toByteArray(Charsets.UTF_8),
        associatedData = associatedData
    )

    fun decryptUtf8(
        key256: ByteArray,
        ciphertextBase64: String,
        ivBase64: String,
        associatedData: ByteArray? = null
    ): String = String(
        decryptBytes(
            key256 = key256,
            ciphertextBase64 = ciphertextBase64,
            ivBase64 = ivBase64,
            associatedData = associatedData
        ),
        Charsets.UTF_8
    )

    /**
     * Encrypts structured NFC card raw credential data for Firestore zero-knowledge storage,
     * binding the ciphertext to [ownerUserId] via GCM AAD so records cannot be transplanted across users.
     */
    fun encryptNfcCardPayload(
        dek256: ByteArray,
        payload: CardSecretsPayload,
        ownerUserId: String? = null
    ): EncryptedPayloadResult {
        val aad = buildCardAad(ownerUserId)
        val result = encryptUtf8(
            key256 = dek256,
            plaintext = payload.toJsonString(),
            associatedData = aad
        )
        return EncryptedPayloadResult(
            encryptedPayloadBase64 = result.ciphertextBase64,
            payloadIvBase64 = result.ivBase64
        )
    }

    /**
     * Decrypts a Firestore NFC card ciphertext. Only v2 (owner-bound AAD) exists; a record
     * with any other version fails closed — no blind fallback and no unsalted-AAD path.
     */
    fun decryptNfcCardPayload(
        dek256: ByteArray,
        encryptedBase64: String,
        ivBase64: String,
        ownerUserId: String? = null,
        cryptoVersion: Int = CURRENT_CRYPTO_VERSION
    ): CardSecretsPayload {
        require(cryptoVersion in KNOWN_CRYPTO_VERSIONS) {
            "Unsupported crypto version: $cryptoVersion"
        }
        val json = decryptUtf8(
            key256 = dek256,
            ciphertextBase64 = encryptedBase64,
            ivBase64 = ivBase64,
            associatedData = buildCardAad(ownerUserId)
        )
        return CardSecretsPayload.fromJsonString(json)
    }

    fun buildCardAad(ownerUserId: String?): ByteArray? =
        ownerUserId?.takeIf { it.isNotBlank() }?.let {
            "$CARD_AAD_PREFIX$it".toByteArray(Charsets.UTF_8)
        }

    /**
     * Best-effort in-memory zeroization of sensitive plaintext or key buffers.
     */
    fun zeroize(vararg buffers: ByteArray?) {
        buffers.forEach { it?.fill(0) }
    }

    fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeBase64(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)
}
