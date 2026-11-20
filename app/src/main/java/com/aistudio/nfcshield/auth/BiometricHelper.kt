package com.aistudio.nfcshield.auth

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aistudio.nfcshield.R
import javax.crypto.Cipher

object BiometricHelper {
    /**
     * Biometric strength is never weakened: weak (class 2) biometrics cannot unlock the
     * vault. When a device credential may accompany biometrics, that is the only
     * relaxation, and the Keystore key is bound to the same authenticator set.
     */
    const val STRONG_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    val DEVICE_CREDENTIAL_AUTHENTICATORS: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isStrongBiometricAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(STRONG_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** True when biometrics or the device credential can authenticate (API 30+ prompts). */
    fun isDeviceCredentialBiometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return BiometricManager.from(context).canAuthenticate(DEVICE_CREDENTIAL_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricAvailable(context: Context): Boolean = isStrongBiometricAvailable(context)

    /**
     * Authenticates using BiometricPrompt.CryptoObject bound to an AndroidKeyStore PrivateKey
     * so the hardware key is unlocked exclusively upon verified authentication.
     *
     * @param deviceCredentialAllowed use the combined biometric-or-device-credential prompt.
     * Note: Android does not allow a negative button with DEVICE_CREDENTIAL, so the
     * "备用 PIN" hint is surfaced on cancellation instead.
     */
    fun authenticateWithCryptoObject(
        activity: FragmentActivity,
        cipher: Cipher,
        deviceCredentialAllowed: Boolean,
        onSuccess: (Cipher) -> Unit,
        onUsePinFallback: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val unlockedCipher = result.cryptoObject?.cipher
                if (unlockedCipher != null) {
                    onSuccess(unlockedCipher)
                } else {
                    onError("硬件密钥解密上下文缺失，请改用备用 PIN 码")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> onUsePinFallback()
                    else -> onError(errString.toString())
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = PromptInfoBuilder.build(activity, deviceCredentialAllowed)
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onUsePinFallback: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> onUsePinFallback()
                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // BiometricPrompt keeps dialog open for retry
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        prompt.authenticate(PromptInfoBuilder.build(activity, allowDeviceCredential = false))
    }

    private object PromptInfoBuilder {
        fun build(
            activity: FragmentActivity,
            allowDeviceCredential: Boolean
        ): BiometricPrompt.PromptInfo {
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.vault_biometric_title))
                .setSubtitle(activity.getString(R.string.vault_biometric_subtitle))
                .setConfirmationRequired(false)
            return if (allowDeviceCredential) {
                builder.setAllowedAuthenticators(DEVICE_CREDENTIAL_AUTHENTICATORS).build()
            } else {
                builder
                    .setAllowedAuthenticators(STRONG_AUTHENTICATORS)
                    .setNegativeButtonText(activity.getString(R.string.vault_biometric_negative))
                    .build()
            }
        }
    }
}