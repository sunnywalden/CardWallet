package com.aistudio.nfcshield

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release builds use Play Integrity attestation. */
object AppCheckInstaller {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}