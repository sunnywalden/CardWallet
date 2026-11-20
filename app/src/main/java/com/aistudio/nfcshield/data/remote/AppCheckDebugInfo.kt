package com.aistudio.nfcshield.data.remote

/** Where the active App Check debug token came from (debug builds only). */
enum class AppCheckDebugTokenSource {
    INTENT,
    SECRETS,
    GENERATED;

    val displayName: String
        get() = when (this) {
            INTENT -> "启动参数"
            SECRETS -> "AI Studio Secrets (.env)"
            GENERATED -> "本机随机生成"
        }
}

/** Debug-only descriptor of the active App Check debug token. */
data class AppCheckDebugTokenInfo(
    val token: String,
    val source: AppCheckDebugTokenSource
)

data class ResolvedDebugToken(
    val token: String,
    val source: AppCheckDebugTokenSource
)

/**
 * Token priority is fixed and testable: launch Intent override first, then the AI Studio
 * Secrets value baked into BuildConfig (debug variant only). A random token persisted by
 * the SDK is the implicit third source and never passes through this resolver.
 */
object AppCheckDebugTokenResolver {
    fun resolve(intentToken: String?, secretsToken: String?): ResolvedDebugToken? {
        val intent = intentToken?.trim()
        if (!intent.isNullOrBlank()) {
            return ResolvedDebugToken(intent, AppCheckDebugTokenSource.INTENT)
        }
        val secrets = secretsToken?.trim()
        if (!secrets.isNullOrBlank()) {
            return ResolvedDebugToken(secrets, AppCheckDebugTokenSource.SECRETS)
        }
        return null
    }
}