package com.aistudio.nfcshield

import android.app.Application
import android.util.Log

class ShieldVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            MainActivity.installUncaughtExceptionHandler(this)
            MainActivity.recordInitStep("ShieldVaultApplication.onCreate start")
            val ok = FirebaseBootstrap.ensureInitialized(this)
            MainActivity.recordInitStep("ShieldVaultApplication FirebaseBootstrap initialized=$ok")
            AppCheckInstaller.install(this)
            MainActivity.recordInitStep("ShieldVaultApplication AppCheck installed")
        } catch (t: Throwable) {
            Log.e(MainActivity.STARTUP_LOG_TAG, "Exception during ShieldVaultApplication.onCreate", t)
            MainActivity.logStartupException("ShieldVaultApplication.onCreate", t)
        }
    }
}
