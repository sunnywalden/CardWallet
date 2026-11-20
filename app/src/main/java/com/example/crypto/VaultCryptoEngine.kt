package com.example.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.model.CardSecretsPayload
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

data class WrappedDekPayload(
    val wrappedDekBase64: String,
    val wrappedDekIvBase64: String,
    val kdfSaltBase64: String,
    val kdfIterations: Int,
    val pinVerifierBase64: String,
    val schemaVersion: Int = 1
)

data class EncryptedPayloadResult(
    val encryptedPayloadBase64: String,
    val payloadIvBase64: String
)

object VaultCryptoEngine {
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val DEK_LENGTH_BYTES = 32
    private const val SALT_LENGTH_BYTES = 16
    const val DEFAULT_KDF_ITERATIONS = 120_000
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "shield_nfc_hardware_vault_key_v1"
    private const val BIOMETRIC_RSA_KEYSTORE_ALIAS = "shield_nfc_biometric_rsa_key_v2"
    private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    private val secureRandom = SecureRandom()

    sealed interface BiometricCipherInitResult {
        data class Ready(val cipher: Cipher) : BiometricCipherInitResult
        data object KeyInvalidated : BiometricCipherInitResult
        data object MissingKey : BiometricCipherInitResult
        data class Error(val message: String) : BiometricCipherInitResult
    }

    fun generateDek(): ByteArray = Aes256GcmCryptoUtil.generateKey256()

    fun wrapDekWithPin(dek: ByteArray, pin: CharArray): WrappedDekPayload {
        require(dek.size == DEK_LENGTH_BYTES) { "Invalid DEK size" }
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val kek = deriveKek(pin, salt, DEFAULT_KDF_ITERATIONS)
        val encrypted = Aes256GcmCryptoUtil.encryptBytes(
            key256 = kek.encoded,
            plaintext = dek
        )
        val verifier = computePinVerifier(kek, salt)

        return WrappedDekPayload(
            wrappedDekBase64 = encrypted.ciphertextBase64,
            wrappedDekIvBase64 = encrypted.ivBase64,
            kdfSaltBase64 = encodeBase64(salt),
            kdfIterations = DEFAULT_KDF_ITERATIONS,
            pinVerifierBase64 = verifier,
            schemaVersion = 1
        )
    }

    fun unwrapDekWithPin(pin: CharArray, wrapped: WrappedDekPayload): ByteArray? {
        return try {
            val salt = decodeBase64(wrapped.kdfSaltBase64)
            val expectedVerifier = decodeBase64(wrapped.pinVerifierBase64)

            val kek = deriveKek(pin, salt, wrapped.kdfIterations)
            val actualVerifier = decodeBase64(computePinVerifier(kek, salt))
            if (!MessageDigest.isEqual(expectedVerifier, actualVerifier)) {
                return null
            }

            Aes256GcmCryptoUtil.decryptBytes(
                key256 = kek.encoded,
                ciphertextBase64 = wrapped.wrappedDekBase64,
                ivBase64 = wrapped.wrappedDekIvBase64
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encryptCardSecrets(
        dek: ByteArray,
        payload: CardSecretsPayload,
        ownerUserId: String? = null
    ): EncryptedPayloadResult = Aes256GcmCryptoUtil.encryptNfcCardPayload(
        dek256 = dek,
        payload = payload,
        ownerUserId = ownerUserId
    )

    fun decryptCardSecrets(
        dek: ByteArray,
        encryptedBase64: String,
        ivBase64: String,
        ownerUserId: String? = null
    ): CardSecretsPayload = Aes256GcmCryptoUtil.decryptNfcCardPayload(
        dek256 = dek,
        encryptedBase64 = encryptedBase64,
        ivBase64 = ivBase64,
        ownerUserId = ownerUserId
    )

    fun sealWithHardwareKeystore(plaintext: ByteArray): Pair<String, String> {
        val secretKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(plaintext)
        return encodeBase64(ciphertext) to encodeBase64(cipher.iv)
    }

    fun unsealWithHardwareKeystore(ciphertextBase64: String, ivBase64: String): ByteArray? {
        return try {
            val secretKey = getOrCreateKeystoreKey()
            val iv = decodeBase64(ivBase64)
            val ciphertext = decodeBase64(ciphertextBase64)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Seals the 256-bit DEK using the public key of an AndroidKeyStore RSA-2048 keypair whose
     * private key strictly requires biometric authentication (setUserAuthenticationRequired = true).
     */
    fun sealWithBiometricKeystorePublicKey(dek: ByteArray): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(BIOMETRIC_RSA_KEYSTORE_ALIAS)) {
            generateBiometricKeystoreKeyPair()
        }
        val cert = keyStore.getCertificate(BIOMETRIC_RSA_KEYSTORE_ALIAS)
            ?: generateBiometricKeystoreKeyPair().let {
                keyStore.load(null)
                keyStore.getCertificate(BIOMETRIC_RSA_KEYSTORE_ALIAS)
            }
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(cert.publicKey.encoded))
        val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec())
        val wrapped = cipher.doFinal(dek)
        return encodeBase64(wrapped)
    }

    /**
     * Initializes a DECRYPT_MODE Cipher bound to the biometric-protected Keystore PrivateKey.
     * The returned Cipher must be passed inside BiometricPrompt.CryptoObject before calling doFinal().
     */
    fun prepareBiometricDecryptCipher(): BiometricCipherInitResult {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val privateKey = keyStore.getKey(BIOMETRIC_RSA_KEYSTORE_ALIAS, null) as? PrivateKey
                ?: return BiometricCipherInitResult.MissingKey
            val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec())
            BiometricCipherInitResult.Ready(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteBiometricKeystoreKey()
            BiometricCipherInitResult.KeyInvalidated
        } catch (e: Exception) {
            BiometricCipherInitResult.Error(e.localizedMessage ?: "无法初始化生物识别硬件密钥")
        }
    }

    fun decryptWithUnlockedBiometricCipher(unlockedCipher: Cipher, sealedDekBase64: String): ByteArray? {
        return try {
            val wrapped = decodeBase64(sealedDekBase64)
            unlockedCipher.doFinal(wrapped)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteBiometricKeystoreKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(BIOMETRIC_RSA_KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_RSA_KEYSTORE_ALIAS)
            }
        }
    }

    private fun generateBiometricKeystoreKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_RSA_KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        kpg.initialize(builder.build())
        return kpg.generateKeyPair()
    }

    private fun oaepSpec(): OAEPParameterSpec =
        OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun deriveKek(pin: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val factory = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (_: NoSuchAlgorithmException) {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun computePinVerifier(kek: SecretKey, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("shield-nfc-pin-verifier-v1".toByteArray(Charsets.UTF_8))
        digest.update(salt)
        digest.update(kek.encoded)
        return encodeBase64(digest.digest())
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)
}
