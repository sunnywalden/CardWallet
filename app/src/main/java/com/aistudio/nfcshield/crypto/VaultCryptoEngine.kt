package com.aistudio.nfcshield.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aistudio.nfcshield.data.model.CardSecretsPayload
import com.aistudio.nfcshield.data.model.BiometricSealMode
import java.nio.ByteBuffer
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
import org.json.JSONObject

data class WrappedDekPayload(
    val wrappedDekBase64: String,
    val wrappedDekIvBase64: String,
    val kdfSaltBase64: String,
    val kdfIterations: Int,
    val pinVerifierBase64: String,
    val kdfAlgorithm: String = VaultCryptoEngine.KDF_SHA256,
    val schemaVersion: Int = 2,
    /** Non-null when the PIN-wrapped DEK (and verifier) are sealed under a device key. */
    val deviceBinding: String? = null
)

data class EncryptedPayloadResult(
    val encryptedPayloadBase64: String,
    val payloadIvBase64: String
)

/** Result of a device-key seal: GCM ciphertext (with tag) and the random IV used. */
data class DeviceSealedPayload(
    val cipherBase64: String,
    val ivBase64: String
)

/**
 * Seals payloads with a non-exportable device key (AndroidKeyStore in production).
 * Injected so unit tests can exercise the device-binding flow without a TEE.
 */
interface DeviceKeySealer {
    /** Whether a device key already exists. Must not create one. */
    fun hasKey(): Boolean

    /** Seals with the existing device key, creating it on first use. Null when unavailable. */
    fun seal(plaintext: ByteArray): DeviceSealedPayload?

    /** Unseals with the device key. Null when the key is missing or authentication fails. */
    fun unseal(cipherBase64: String, ivBase64: String): ByteArray?
}

class DeviceKeyUnavailableException(message: String) : IllegalStateException(message)

/** Sealed result so the UI can distinguish wrong PIN from a missing device key. */
sealed interface DekUnwrapResult {
    data class Success(val dek: ByteArray) : DekUnwrapResult
    data object WrongPin : DekUnwrapResult
    /** The vault is device-bound but this device no longer holds the wrapping key. */
    data object DeviceBindingUnavailable : DekUnwrapResult
    /** The device key exists but does not authenticate the cloud blob. */
    data object DeviceBindingCorrupt : DekUnwrapResult
    /** Legacy or future schema / binding combination: fail closed. */
    data object UnsupportedFormat : DekUnwrapResult
}

object VaultCryptoEngine {
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val DEK_LENGTH_BYTES = 32
    private const val SALT_LENGTH_BYTES = 16

    const val KDF_SHA256 = "PBKDF2WithHmacSHA256"
    const val DEFAULT_KDF_ITERATIONS = 120_000
    const val MIN_V2_KDF_ITERATIONS = 120_000

    /**
     * v2 = PIN-wrapped DEK readable on any device (legacy, still readable for upgrade).
     * v3 = the PIN-wrapped DEK and verifier are additionally sealed under a device key,
     *      so the cloud document alone cannot be used for offline PIN brute force.
     */
    const val CURRENT_SCHEMA_VERSION = 3
    const val DEVICE_BINDING_ANDROID_KEYSTORE = "ANDROID_KEYSTORE"
    /** Placeholder verifier stored in cleartext for v3; the real verifier lives inside the device seal. */
    const val DEVICE_BOUND_VERIFIER_MARKER = "device-bound:v3"

    private const val VERIFIER_V2_DOMAIN = "shield-nfc-pin-verifier-v2"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "shield_nfc_hardware_vault_key_v1"
    private const val DEVICE_WRAP_KEYSTORE_ALIAS = "shield_nfc_device_wrap_key_v1"
    private const val BIOMETRIC_RSA_KEYSTORE_ALIAS = "shield_nfc_biometric_rsa_key_v2"
    private const val DEVICE_CREDENTIAL_RSA_KEYSTORE_ALIAS = "shield_nfc_biometric_rsa_key_v3"
    private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    private const val INNER_WRAPPED_DEK = "w"
    private const val INNER_WRAPPED_DEK_IV = "i"
    private const val INNER_PIN_VERIFIER = "v"

    private data class InnerPayload(
        val wrappedDekBase64: String,
        val wrappedDekIvBase64: String,
        val pinVerifierBase64: String
    )

    private fun parseInnerPayload(json: String): InnerPayload? = runCatching {
        val obj = JSONObject(json)
        InnerPayload(
            wrappedDekBase64 = obj.getString(INNER_WRAPPED_DEK),
            wrappedDekIvBase64 = obj.getString(INNER_WRAPPED_DEK_IV),
            pinVerifierBase64 = obj.getString(INNER_PIN_VERIFIER)
        )
    }.getOrNull()

    private val secureRandom = SecureRandom()

    /** Overridable for unit tests (Robolectric has no TEE-backed AndroidKeyStore). */
    @Volatile
    var deviceKeySealer: DeviceKeySealer = AndroidKeystoreDeviceKeySealer

    /** Non-exportable AES-256-GCM key inside AndroidKeyStore, used only by schema v3. */
    object AndroidKeystoreDeviceKeySealer : DeviceKeySealer {
        override fun hasKey(): Boolean = runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.containsAlias(DEVICE_WRAP_KEYSTORE_ALIAS)
        }.getOrDefault(false)

        override fun seal(plaintext: ByteArray): DeviceSealedPayload? = runCatching {
            val key = getOrCreateDeviceWrapKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            DeviceSealedPayload(
                cipherBase64 = encodeBase64(cipher.doFinal(plaintext)),
                ivBase64 = encodeBase64(cipher.iv)
            )
        }.getOrNull()

        override fun unseal(cipherBase64: String, ivBase64: String): ByteArray? = runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val key = keyStore.getKey(DEVICE_WRAP_KEYSTORE_ALIAS, null) as? SecretKey
                ?: return null
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, decodeBase64(ivBase64))
            )
            cipher.doFinal(decodeBase64(cipherBase64))
        }.getOrNull()

        private fun getOrCreateDeviceWrapKey(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(DEVICE_WRAP_KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    DEVICE_WRAP_KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return keyGenerator.generateKey()
        }
    }

    sealed interface BiometricCipherInitResult {
        data class Ready(val cipher: Cipher) : BiometricCipherInitResult
        data object KeyInvalidated : BiometricCipherInitResult
        data object MissingKey : BiometricCipherInitResult
        data class Error(val message: String) : BiometricCipherInitResult
    }

    /** True when this OS image provides PBKDF2-HMAC-SHA256 (Android 8.0+). */
    fun isStrongKdfSupported(): Boolean = try {
        SecretKeyFactory.getInstance(KDF_SHA256)
        true
    } catch (_: NoSuchAlgorithmException) {
        false
    }

    fun generateDek(): ByteArray = Aes256GcmCryptoUtil.generateKey256()

    /**
     * Wraps the DEK with a PBKDF2-derived KEK. Always uses PBKDF2-HMAC-SHA256 and
     * verifier v2 (binds salt + iterations + algorithm). Fails closed on OS images
     * without SHA-256 support instead of silently downgrading to SHA-1.
     *
     * With [bindToDevice] the inner PIN-wrapped payload is additionally sealed under a
     * non-exportable device key (schema v3): the cloud document alone can no longer be
     * used to test PIN guesses offline. The trade-off is that the vault can only be
     * unlocked on a device that still holds that key — there is no pure-PIN cross-device
     * recovery. Sealing failure throws [DeviceKeyUnavailableException] (fail closed).
     */
    fun wrapDekWithPin(
        dek: ByteArray,
        pin: CharArray,
        bindToDevice: Boolean = true
    ): WrappedDekPayload {
        require(dek.size == DEK_LENGTH_BYTES) { "Invalid DEK size" }
        check(isStrongKdfSupported()) {
            "此设备的系统版本不支持安全密钥派生 (PBKDF2-HMAC-SHA256)，无法创建或更新保险库。请升级 Android 系统。"
        }
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val kek = deriveKek(pin, salt, DEFAULT_KDF_ITERATIONS, KDF_SHA256)
        val encrypted = Aes256GcmCryptoUtil.encryptBytes(
            key256 = kek.encoded,
            plaintext = dek
        )
        val verifier = computePinVerifierV2(kek, salt, DEFAULT_KDF_ITERATIONS, KDF_SHA256)

        if (!bindToDevice) {
            return WrappedDekPayload(
                wrappedDekBase64 = encrypted.ciphertextBase64,
                wrappedDekIvBase64 = encrypted.ivBase64,
                kdfSaltBase64 = encodeBase64(salt),
                kdfIterations = DEFAULT_KDF_ITERATIONS,
                pinVerifierBase64 = verifier,
                kdfAlgorithm = KDF_SHA256,
                schemaVersion = 2
            )
        }

        val innerJson = JSONObject().apply {
            put(INNER_WRAPPED_DEK, encrypted.ciphertextBase64)
            put(INNER_WRAPPED_DEK_IV, encrypted.ivBase64)
            put(INNER_PIN_VERIFIER, verifier)
        }.toString()
        val sealed = deviceKeySealer.seal(innerJson.toByteArray(Charsets.UTF_8))
            ?: throw DeviceKeyUnavailableException(
                "此设备不支持硬件密钥绑定，无法创建或升级安全保险库。"
            )
        return WrappedDekPayload(
            wrappedDekBase64 = sealed.cipherBase64,
            wrappedDekIvBase64 = sealed.ivBase64,
            kdfSaltBase64 = encodeBase64(salt),
            kdfIterations = DEFAULT_KDF_ITERATIONS,
            pinVerifierBase64 = DEVICE_BOUND_VERIFIER_MARKER,
            kdfAlgorithm = KDF_SHA256,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            deviceBinding = DEVICE_BINDING_ANDROID_KEYSTORE
        )
    }

    /**
     * Unwraps the DEK, distinguishing a wrong PIN from a missing/invalid device key.
     *
     * - v2 records: readable for migration (SHA-256, >= 120k iterations, verifier v2).
     * - v3 records: the device-sealed inner payload must be unsealed first; any failure
     *   there means the cloud document alone is insufficient (fail closed).
     * - v1 (or unknown) records: rejected; the eviction milestone removed the read path.
     */
    fun unwrapDekResult(pin: CharArray, wrapped: WrappedDekPayload): DekUnwrapResult {
        return try {
            val inner = when {
                wrapped.schemaVersion >= CURRENT_SCHEMA_VERSION -> {
                    if (wrapped.schemaVersion != CURRENT_SCHEMA_VERSION ||
                        wrapped.deviceBinding != DEVICE_BINDING_ANDROID_KEYSTORE
                    ) {
                        return DekUnwrapResult.UnsupportedFormat
                    }
                    if (!deviceKeySealer.hasKey()) {
                        return DekUnwrapResult.DeviceBindingUnavailable
                    }
                    val innerBytes = deviceKeySealer.unseal(
                        wrapped.wrappedDekBase64,
                        wrapped.wrappedDekIvBase64
                    ) ?: return DekUnwrapResult.DeviceBindingCorrupt
                    parseInnerPayload(String(innerBytes, Charsets.UTF_8))
                        ?: return DekUnwrapResult.DeviceBindingCorrupt
                }
                wrapped.schemaVersion == 2 -> {
                    if (!wrapped.deviceBinding.isNullOrBlank()) {
                        return DekUnwrapResult.UnsupportedFormat
                    }
                    InnerPayload(
                        wrappedDekBase64 = wrapped.wrappedDekBase64,
                        wrappedDekIvBase64 = wrapped.wrappedDekIvBase64,
                        pinVerifierBase64 = wrapped.pinVerifierBase64
                    )
                }
                else -> return DekUnwrapResult.UnsupportedFormat
            }

            require(wrapped.kdfAlgorithm == KDF_SHA256) { "Unsupported KDF algorithm" }
            require(wrapped.kdfIterations >= MIN_V2_KDF_ITERATIONS) {
                "vault KDF iterations below security floor"
            }

            val salt = decodeBase64(wrapped.kdfSaltBase64)
            val kek = deriveKek(pin, salt, wrapped.kdfIterations, KDF_SHA256)
            val expectedVerifier = decodeBase64(inner.pinVerifierBase64)
            val actualVerifier = decodeBase64(
                computePinVerifierV2(kek, salt, wrapped.kdfIterations, KDF_SHA256)
            )
            if (!MessageDigest.isEqual(expectedVerifier, actualVerifier)) {
                return DekUnwrapResult.WrongPin
            }

            val dek = Aes256GcmCryptoUtil.decryptBytes(
                key256 = kek.encoded,
                ciphertextBase64 = inner.wrappedDekBase64,
                ivBase64 = inner.wrappedDekIvBase64
            )
            if (dek.size != DEK_LENGTH_BYTES) return DekUnwrapResult.DeviceBindingCorrupt
            DekUnwrapResult.Success(dek)
        } catch (_: IllegalArgumentException) {
            DekUnwrapResult.UnsupportedFormat
        } catch (_: Exception) {
            DekUnwrapResult.DeviceBindingCorrupt
        }
    }

    /** Convenience wrapper: only the DEK on success, null for every failure mode. */
    fun unwrapDekWithPin(pin: CharArray, wrapped: WrappedDekPayload): ByteArray? =
        (unwrapDekResult(pin, wrapped) as? DekUnwrapResult.Success)?.dek

    /** True when an existing config should be transparently upgraded to device binding. */
    fun needsVaultUpgrade(wrapped: WrappedDekPayload): Boolean =
        wrapped.schemaVersion < CURRENT_SCHEMA_VERSION ||
            wrapped.deviceBinding != DEVICE_BINDING_ANDROID_KEYSTORE ||
            wrapped.kdfAlgorithm != KDF_SHA256 ||
            wrapped.kdfIterations < MIN_V2_KDF_ITERATIONS

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
        ownerUserId: String? = null,
        cryptoVersion: Int = Aes256GcmCryptoUtil.CURRENT_CRYPTO_VERSION
    ): CardSecretsPayload = Aes256GcmCryptoUtil.decryptNfcCardPayload(
        dek256 = dek,
        encryptedBase64 = encryptedBase64,
        ivBase64 = ivBase64,
        ownerUserId = ownerUserId,
        cryptoVersion = cryptoVersion
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
     * Seals the 256-bit DEK with the public key of an AndroidKeyStore RSA-2048 keypair whose
     * private key strictly requires user authentication. Public-key encryption itself never
     * needs authentication, which lets us seal during PIN setup while keeping decryption
     * bound to the biometric prompt (CryptoObject).
     */
    fun sealWithBiometricKeystorePublicKey(
        dek: ByteArray,
        mode: BiometricSealMode = BiometricSealMode.RSA_CRYPTO_OBJECT
    ): String {
        val alias = when (mode) {
            BiometricSealMode.RSA_DEVICE_CREDENTIAL -> DEVICE_CREDENTIAL_RSA_KEYSTORE_ALIAS
            else -> BIOMETRIC_RSA_KEYSTORE_ALIAS
        }
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            generateBiometricKeystoreKeyPair(alias, mode)
            keyStore.load(null)
        }
        val cert = keyStore.getCertificate(alias)
            ?: generateBiometricKeystoreKeyPair(alias, mode).let {
                keyStore.load(null)
                keyStore.getCertificate(alias)
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
    fun prepareBiometricDecryptCipher(
        mode: BiometricSealMode = BiometricSealMode.RSA_CRYPTO_OBJECT
    ): BiometricCipherInitResult {
        val alias = when (mode) {
            BiometricSealMode.RSA_DEVICE_CREDENTIAL -> DEVICE_CREDENTIAL_RSA_KEYSTORE_ALIAS
            else -> BIOMETRIC_RSA_KEYSTORE_ALIAS
        }
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val privateKey = keyStore.getKey(alias, null) as? PrivateKey
                ?: return BiometricCipherInitResult.MissingKey
            val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec())
            BiometricCipherInitResult.Ready(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteBiometricKeystoreKey(mode)
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

    fun deleteBiometricKeystoreKey(mode: BiometricSealMode? = null) {
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val aliases = when (mode) {
                BiometricSealMode.RSA_DEVICE_CREDENTIAL -> listOf(DEVICE_CREDENTIAL_RSA_KEYSTORE_ALIAS)
                BiometricSealMode.RSA_CRYPTO_OBJECT -> listOf(BIOMETRIC_RSA_KEYSTORE_ALIAS)
                else -> listOf(BIOMETRIC_RSA_KEYSTORE_ALIAS, DEVICE_CREDENTIAL_RSA_KEYSTORE_ALIAS)
            }
            aliases.forEach { alias ->
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            }
        }
    }

    private fun generateBiometricKeystoreKeyPair(
        alias: String,
        mode: BiometricSealMode
    ): KeyPair {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authType = if (mode == BiometricSealMode.RSA_DEVICE_CREDENTIAL) {
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            } else {
                KeyProperties.AUTH_BIOMETRIC_STRONG
            }
            builder.setUserAuthenticationParameters(0, authType)
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

    private fun deriveKek(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int,
        algorithm: String
    ): SecretKey {
        require(algorithm == KDF_SHA256) {
            "Unsupported KDF algorithm: $algorithm"
        }
        val factory = SecretKeyFactory.getInstance(algorithm)
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        try {
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Verifier v2 binds salt, iteration count and algorithm into the digest. */
    private fun computePinVerifierV2(
        kek: SecretKey,
        salt: ByteArray,
        iterations: Int,
        algorithm: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(VERIFIER_V2_DOMAIN.toByteArray(Charsets.UTF_8))
        digest.update(salt)
        digest.update(ByteBuffer.allocate(4).putInt(iterations).array())
        digest.update(algorithm.toByteArray(Charsets.UTF_8))
        digest.update(kek.encoded)
        return encodeBase64(digest.digest())
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)
}