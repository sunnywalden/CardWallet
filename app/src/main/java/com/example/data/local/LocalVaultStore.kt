package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.auth.BiometricHelper
import com.example.crypto.VaultCryptoEngine
import com.example.data.model.ActiveHceCardConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "shield_nfc_vault_prefs")

data class LocalSecurityPreferences(
    val biometricEnabled: Boolean = true,
    val autoLockMinutes: Int = 5,
    val requireVaultUnlockedForHce: Boolean = false
)

enum class BiometricSealMode {
    RSA_CRYPTO_OBJECT,
    AES_KEYSTORE
}

data class SealedDekRecord(
    val mode: BiometricSealMode,
    val cipherBase64: String,
    val ivBase64: String
)

class LocalVaultStore(private val context: Context) {
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

    suspend fun sealDekForBiometric(uid: String, dek: ByteArray) {
        val useStrongKeystore = BiometricHelper.isStrongBiometricAvailable(context)
        val rsaSealed = if (useStrongKeystore) {
            runCatching { VaultCryptoEngine.sealWithBiometricKeystorePublicKey(dek) }.getOrNull()
        } else null

        if (rsaSealed != null) {
            dataStore.edit { prefs ->
                prefs[Keys.SEALED_DEK_CIPHER] = rsaSealed
                prefs[Keys.SEALED_DEK_IV] = ""
                prefs[Keys.SEALED_DEK_UID] = uid
                prefs[Keys.SEALED_DEK_MODE] = BiometricSealMode.RSA_CRYPTO_OBJECT.name
            }
        } else {
            val (cipher, iv) = VaultCryptoEngine.sealWithHardwareKeystore(dek)
            dataStore.edit { prefs ->
                prefs[Keys.SEALED_DEK_CIPHER] = cipher
                prefs[Keys.SEALED_DEK_IV] = iv
                prefs[Keys.SEALED_DEK_UID] = uid
                prefs[Keys.SEALED_DEK_MODE] = BiometricSealMode.AES_KEYSTORE.name
            }
        }
    }

    suspend fun hasSealedDekForUser(uid: String): Boolean {
        val prefs = dataStore.data.first()
        val mode = prefs[Keys.SEALED_DEK_MODE]
        val hasCipher = !prefs[Keys.SEALED_DEK_CIPHER].isNullOrBlank()
        val hasValidIv = mode == BiometricSealMode.RSA_CRYPTO_OBJECT.name || !prefs[Keys.SEALED_DEK_IV].isNullOrBlank()
        return prefs[Keys.SEALED_DEK_UID] == uid && hasCipher && hasValidIv
    }

    suspend fun readSealedDekRecord(uid: String): SealedDekRecord? {
        val prefs = dataStore.data.first()
        if (prefs[Keys.SEALED_DEK_UID] != uid) return null
        val cipher = prefs[Keys.SEALED_DEK_CIPHER]?.takeIf { it.isNotBlank() } ?: return null
        val rawMode = prefs[Keys.SEALED_DEK_MODE]
        val mode = if (rawMode == BiometricSealMode.RSA_CRYPTO_OBJECT.name) {
            BiometricSealMode.RSA_CRYPTO_OBJECT
        } else {
            BiometricSealMode.AES_KEYSTORE
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

    suspend fun unsealDekForUser(uid: String): ByteArray? {
        val record = readSealedDekRecord(uid) ?: return null
        if (record.mode == BiometricSealMode.RSA_CRYPTO_OBJECT || record.ivBase64.isBlank()) {
            return null
        }
        return VaultCryptoEngine.unsealWithHardwareKeystore(record.cipherBase64, record.ivBase64)
    }

    suspend fun saveActiveHceCard(config: ActiveHceCardConfig?) {
        dataStore.edit { prefs ->
            if (config == null) {
                prefs.remove(Keys.ACTIVE_HCE_CIPHER)
                prefs.remove(Keys.ACTIVE_HCE_IV)
                prefs[Keys.QUICK_HCE_ENABLED] = false
            } else {
                val (cipher, iv) = VaultCryptoEngine.sealWithHardwareKeystore(
                    config.toJsonString().toByteArray(Charsets.UTF_8)
                )
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

    suspend fun isQuickHceEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[Keys.QUICK_HCE_ENABLED] ?: true
    }

    suspend fun readConfiguredHceCard(): ActiveHceCardConfig? {
        val prefs = dataStore.data.first()
        val cipher = prefs[Keys.ACTIVE_HCE_CIPHER] ?: return null
        val iv = prefs[Keys.ACTIVE_HCE_IV] ?: return null
        val plainBytes = VaultCryptoEngine.unsealWithHardwareKeystore(cipher, iv) ?: return null
        return ActiveHceCardConfig.fromJsonString(String(plainBytes, Charsets.UTF_8))
    }

    suspend fun readActiveHceCard(): ActiveHceCardConfig? {
        val prefs = dataStore.data.first()
        val quickEnabled = prefs[Keys.QUICK_HCE_ENABLED] ?: true
        if (!quickEnabled) return null
        val cipher = prefs[Keys.ACTIVE_HCE_CIPHER] ?: return null
        val iv = prefs[Keys.ACTIVE_HCE_IV] ?: return null
        val plainBytes = VaultCryptoEngine.unsealWithHardwareKeystore(cipher, iv) ?: return null
        return ActiveHceCardConfig.fromJsonString(String(plainBytes, Charsets.UTF_8))
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
        }
    }
}
