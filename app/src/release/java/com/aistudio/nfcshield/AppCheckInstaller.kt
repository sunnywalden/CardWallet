package com.aistudio.nfcshield

import android.content.Context
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds use Play Integrity attestation exclusively.
 * Debug-token APIs intentionally return null and no debug information is exposed.
 */
object AppCheckInstaller {
    fun install(context: Context, intentToken: String? = null) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }

    fun activeDebugTokenInfo(context: Context): AppCheckDebugTokenInfo? = null
}