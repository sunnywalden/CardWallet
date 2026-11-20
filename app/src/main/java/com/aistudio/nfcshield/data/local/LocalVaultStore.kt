package com.aistudio.nfcshield.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.nfcshield.auth.BiometricHelper
import com.aistudio.nfcshield.crypto.VaultCryptoEngine
import com.aistudio.nfcshield.data.model.ActiveHceCardConfig
import com.aistudio.nfcshield.data.model.BiometricSealMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "shield_nfc_vault_prefs")

data class LocalSecurityPreferences(
    val biometricEnabled: Boolean = true,
    val autoLockMinutes: Int = 5,
    val requireVaultUnlockedForHce: Boolean = false
)

data class SealedDekRecord(
    val mode: BiometricSealMode,
    val cipherBase64: String,
    val ivBase64: String
)

data class PinLockoutState(
    val failedAttempts: Int = 0,
    val lockoutUntilElapsedMillis: Long = 0L
)

/**
 * Seals/unseals the active-HCE configuration. Injected so unit tests can exercise
 * quick-toggle semantics without a real AndroidKeyStore (unavailable under Robolectric).
 */
interface ActiveHceSealer {
    fun seal(plaintext: ByteArray): Pair<String, String>
    fun unseal(cipherBase64: String, ivBase64: String): ByteArray?
}

object KeystoreActiveHceSealer : ActiveHceSealer {
    override fun seal(plaintext: ByteArray): Pair<String, String> =
        VaultCryptoEngine.sealWithHardwareKeystore(plaintext)

    override fun unseal(cipherBase64: String, ivBase64: String): ByteArray? =
        VaultCryptoEngine.unsealWithHardwareKeystore(cipherBase64, ivBase64)
}

class LocalVaultStore(
    private val context: Context,
    private val hceSealer: ActiveHceSealer = KeystoreActiveHceSealer
) {
    private val dataStore = context.applicationContext.vaultDataStore

    private object Keys {
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val REQUIRE_UNLOCK_FOR_HCE = booleanPreferencesKey("require_unlock_for_hce")
        val SEALED_DEK_CIPHER = stringPreferencesKey("sealed_dek_cipher")
        val SEALED_DEK_IV = stringPreferencesKey("sealed_dek_iv")
        val SEALED_DEK_UID = stringPreferencesKey("sealed_dek_uid")
        val SEALED_DEK_MODE = stringPreferencesKey("sealed_dek_mode")
        val ACTIVE_HCE_CIPHER = stringPreferencesKey("active_hce_cipher")
        val ACTIVE_HCE_IV = stringPreferencesKey("active_hce_iv")
        val QUICK_HCE_ENABLED = booleanPreferencesKey("quick_hce_enabled")
        val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val PIN_LOCKOUT_UNTIL = longPreferencesKey("pin_lockout_until_elapsed")
    }

    val preferencesFlow: Flow<LocalSecurityPreferences> = dataStore.data.map { prefs ->
        LocalSecurityPreferences(
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            autoLockMinutes = prefs[Keys.AUTO_LOCK_MINUTES] ?: 5,
            requireVaultUnlockedForHce = prefs[Keys.REQUIRE_UNLOCK_FOR_HCE] ?: false
        )
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
            if (!enabled) {
                prefs.remove(Keys.SEALED_DEK_CIPHER)
                prefs.remove(Keys.SEALED_DEK_IV)
                prefs.remove(Keys.SEALED_DEK_UID)
                prefs.remove(Keys.SEALED_DEK_MODE)
            }
        }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_MINUTES] = minutes
        }
    }

    suspend fun setRequireVaultUnlockedForHce(required: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.REQUIRE_UNLOCK_FOR_HCE] = required
        }
    }

    /**
     * Seals the DEK for quick unlock. Returns the mode actually used, or null when this
     * device cannot provide an authentication-bound hardware key (fail closed: PIN-only).
     *
     * Only auth-bound RSA keys are written for new seals. The legacy non-auth-bound AES
     * seal is never produced anymore; it is only read so existing installs can upgrade.
     */
    suspend fun sealDekForBiometric(uid: String, dek: ByteArray): BiometricSealMode? {
        val mode = when {
            BiometricHelper.isStrongBiometricAvailable(context) -> BiometricSealMode.RSA_CRYPTO_OBJECT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                BiometricHelper.isDeviceCredentialBiometricAvailable(context) ->
                BiometricSealMode.RSA_DEVICE_CREDENTIAL
            else -> return null
        }

        val sealed = runCatching {
            VaultCryptoEngine.sealWithBiometricKeystorePublicKey(dek, mode)
        }.getOrNull() ?: return null

        dataStore.edit { prefs ->
            prefs[Keys.SEALED_DEK_CIPHER] = sealed
            prefs[Keys.SEALED_DEK_IV] = ""
            prefs[Keys.SEALED_DEK_UID] = uid
            prefs[Keys.SEALED_DEK_MODE] = mode.name
        }
        return mode
    }

    suspend fun hasSealedDekForUser(uid: String): Boolean {
        val prefs = dataStore.data.first()
        val mode = prefs[Keys.SEALED_DEK_MODE]
        val hasCipher = !prefs[Keys.SEALED_DEK_CIPHER].isNullOrBlank()
        val hasValidIv = mode == BiometricSealMode.RSA_CRYPTO_OBJECT.name ||
            mode == BiometricSealMode.RSA_DEVICE_CREDENTIAL.name ||
            !prefs[Keys.SEALED_DEK_IV].isNullOrBlank()
        return prefs[Keys.SEALED_DEK_UID] == uid && hasCipher && hasValidIv
    }

    suspend fun readSealedDekRecord(uid: String): SealedDekRecord? {
        val prefs = dataStore.data.first()
        if (prefs[Keys.SEALED_DEK_UID] != uid) return null
        val cipher = prefs[Keys.SEALED_DEK_CIPHER]?.takeIf { it.isNotBlank() } ?: return null
        val mode = when (prefs[Keys.SEALED_DEK_MODE]) {
            BiometricSealMode.RSA_CRYPTO_OBJECT.name -> BiometricSealMode.RSA_CRYPTO_OBJECT
            BiometricSealMode.RSA_DEVICE_CREDENTIAL.name -> BiometricSealMode.RSA_DEVICE_CREDENTIAL
            else -> BiometricSealMode.AES_KEYSTORE
        }
        return SealedDekRecord(
            mode = mode,
            cipherBase64 = cipher,
            ivBase64 = prefs[Keys.SEALED_DEK_IV].orEmpty()
        )
    }

    suspend fun clearBiometricSealedDek() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SEALED_DEK_CIPHER)
            prefs.remove(Keys.SEALED_DEK_IV)
            prefs.remove(Keys.SEALED_DEK_UID)
            prefs.remove(Keys.SEALED_DEK_MODE)
        }
    }

    /**
     * Unseals the DEK via the legacy device-bound AES key. Only meaningful for
     * [BiometricSealMode.AES_KEYSTORE] records; auth-bound modes require the
     * BiometricPrompt CryptoObject flow instead and always return null here.
     */
    suspend fun unsealDekForUser(uid: String): ByteArray? {
        val record = readSealedDekRecord(uid) ?: return null
        if (record.mode != BiometricSealMode.AES_KEYSTORE || record.ivBase64.isBlank()) {
            return null
        }
        return VaultCryptoEngine.unsealWithHardwareKeystore(record.cipherBase64, record.ivBase64)
    }

    /**
     * Writes the sealed active-HCE configuration without touching the quick-toggle flag.
     * Used by state synchronization so a user-paused HCE slot is never silently resumed.
     */
    suspend fun sealActiveHceCard(config: ActiveHceCardConfig) {
        dataStore.edit { prefs ->
            val (cipher, iv) = hceSealer.seal(config.toJsonString().toByteArray(Charsets.UTF_8))
            prefs[Keys.ACTIVE_HCE_CIPHER] = cipher
            prefs[Keys.ACTIVE_HCE_IV] = iv
        }
    }

    /**
     * Explicit user action: activate (config != null) or clear (config == null) the HCE slot.
     * Only this path may flip the quick-toggle flag on.
     */
    suspend fun saveActiveHceCard(config: ActiveHceCardConfig?) {
        dataStore.edit { prefs ->
            if (config == null) {
                prefs.remove(Keys.ACTIVE_HCE_CIPHER)
                prefs.remove(Keys.ACTIVE_HCE_IV)
                prefs[Keys.QUICK_HCE_ENABLED] = false
            } else {
                val (cipher, iv) = hceSealer.seal(config.toJsonString().toByteArray(Charsets.UTF_8))
                prefs[Keys.ACTIVE_HCE_CIPHER] = cipher
                prefs[Keys.ACTIVE_HCE_IV] = iv
                prefs[Keys.QUICK_HCE_ENABLED] = true
            }
        }
    }

    suspend fun setQuickHceEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.QUICK_HCE_ENABLED] = enabled
        }
    }

    /** Fail-closed default: without an explicit opt-in the HCE slot stays off. */
    suspend fun isQuickHceEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[Keys.QUICK_HCE_ENABLED] ?: false
    }

    /** Reads the sealed configuration regardless of the quick-toggle flag. */
    suspend fun readConfiguredHceCard(): ActiveHceCardConfig? {
        val prefs = dataStore.data.first()
        val cipher = prefs[Keys.ACTIVE_HCE_CIPHER] ?: return null
        val iv = prefs[Keys.ACTIVE_HCE_IV] ?: return null
        val plainBytes = hceSealer.unseal(cipher, iv) ?: return null
        return ActiveHceCardConfig.fromJsonString(String(plainBytes, Charsets.UTF_8))
    }

    /** Reads the configuration only when the user has enabled quick HCE. */
    suspend fun readActiveHceCard(): ActiveHceCardConfig? {
        if (!isQuickHceEnabled()) return null
        return readConfiguredHceCard()
    }

    suspend fun readPinLockoutState(): PinLockoutState {
        val prefs = dataStore.data.first()
        return PinLockoutState(
            failedAttempts = prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0,
            lockoutUntilElapsedMillis = prefs[Keys.PIN_LOCKOUT_UNTIL] ?: 0L
        )
    }

    suspend fun persistPinLockoutState(state: PinLockoutState) {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_FAILED_ATTEMPTS] = state.failedAttempts
            prefs[Keys.PIN_LOCKOUT_UNTIL] = state.lockoutUntilElapsedMillis
        }
    }

    suspend fun clearPinLockoutState() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PIN_FAILED_ATTEMPTS)
            prefs.remove(Keys.PIN_LOCKOUT_UNTIL)
        }
    }

    suspend fun clearSensitiveLocalSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SEALED_DEK_CIPHER)
            prefs.remove(Keys.SEALED_DEK_IV)
            prefs.remove(Keys.SEALED_DEK_UID)
            prefs.remove(Keys.SEALED_DEK_MODE)
            prefs.remove(Keys.ACTIVE_HCE_CIPHER)
            prefs.remove(Keys.ACTIVE_HCE_IV)
            prefs.remove(Keys.QUICK_HCE_ENABLED)
            // Security preferences are per-account: never leak them to the next sign-in.
            prefs.remove(Keys.BIOMETRIC_ENABLED)
            prefs.remove(Keys.AUTO_LOCK_MINUTES)
            prefs.remove(Keys.REQUIRE_UNLOCK_FOR_HCE)
            prefs.remove(Keys.PIN_FAILED_ATTEMPTS)
            prefs.remove(Keys.PIN_LOCKOUT_UNTIL)
        }
    }
}