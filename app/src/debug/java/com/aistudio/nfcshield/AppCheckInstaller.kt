package com.aistudio.nfcshield

import android.content.Context
import android.util.Log
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenInfo
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenResolver
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenSource
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use App Check's debug provider.
 *
 * A fixed debug token can arrive from a launch Intent (highest priority) or from AI Studio
 * Secrets via BuildConfig.APPCHECK_DEBUG_TOKEN. It is written synchronously (commit()) to
 * the SDK's debug store before the provider factory is installed, so the very first token
 * fetch already uses the registered token under Firestore's ENFORCED enforcement.
 */
object AppCheckInstaller {
    private const val PREFS_TEMPLATE = "com.google.firebase.appcheck.debug.store.%s"
    private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
    private const val TAG = "AppCheckDebug"

    @Volatile
    private var lastInstalledSource: AppCheckDebugTokenSource? = null

    fun install(context: Context, intentToken: String? = null) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val app = FirebaseApp.getInstance()
        val resolved = AppCheckDebugTokenResolver.resolve(
            intentToken = intentToken,
            secretsToken = BuildConfig.APPCHECK_DEBUG_TOKEN
        )
        if (resolved != null) {
            persistDebugToken(context, app, resolved.token)
            lastInstalledSource = resolved.source
            Log.i(
                TAG,
                "Using debug token from ${resolved.source.displayName} (length=${resolved.token.length})"
            )
        } else {
            lastInstalledSource = AppCheckDebugTokenSource.GENERATED
        }
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        Log.i(
            TAG,
            "Debug provider installed. Register the token in Firebase Console > App Check > " +
                "Apps > debug tokens (or the CLI) while enforcement is ENFORCED."
        )
    }

    /** Debug-only: the token currently used by the SDK plus its configured source. */
    fun activeDebugTokenInfo(context: Context): AppCheckDebugTokenInfo? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        val stored = runCatching {
            val app = FirebaseApp.getInstance()
            context.getSharedPreferences(
                PREFS_TEMPLATE.format(app.persistenceKey),
                Context.MODE_PRIVATE
            ).getString(DEBUG_SECRET_KEY, null)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val source = lastInstalledSource ?: when {
            stored == BuildConfig.APPCHECK_DEBUG_TOKEN.trim().takeIf { it.isNotBlank() } ->
                AppCheckDebugTokenSource.SECRETS
            else -> AppCheckDebugTokenSource.GENERATED
        }
        return AppCheckDebugTokenInfo(token = stored, source = source)
    }

    private fun persistDebugToken(context: Context, app: FirebaseApp, token: String) {
        // commits synchronously so the value is durable before the first token fetch.
        context.getSharedPreferences(
            PREFS_TEMPLATE.format(app.persistenceKey),
            Context.MODE_PRIVATE
        ).edit()
            .putString(DEBUG_SECRET_KEY, token)
            .commit()
    }
}