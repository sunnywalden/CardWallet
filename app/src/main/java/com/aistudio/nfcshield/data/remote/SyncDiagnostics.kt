package com.aistudio.nfcshield.data.remote

import java.io.IOException

/** Result of proactively refreshing both credentials Firestore depends on. */
data class CredentialRefreshResult(
    val authOk: Boolean,
    val authError: String?,
    val appCheckOk: Boolean,
    val appCheckFailure: AppCheckFailureKind,
    val appCheckError: String?,
    val networkAvailable: Boolean,
    val capturedAtMillis: Long
)

enum class SyncProbeStatus { OK, FAILED }

/** Coarse App Check failure classes used by the diagnostics UI. */
enum class AppCheckFailureKind {
    NONE,
    DEBUG_TOKEN_UNREGISTERED,
    PLAY_INTEGRITY_FAILED,
    NETWORK,
    CONFIGURATION,
    UNKNOWN;

    fun displayHint(debugBuild: Boolean): String = when (this) {
        NONE -> "设备证明正常"
        DEBUG_TOKEN_UNREGISTERED -> if (debugBuild) {
            "调试令牌未注册：请将下方 Debug Token 注册到 Firebase Console → App Check → 调试令牌"
        } else {
            "App Check 配置被拒绝"
        }
        PLAY_INTEGRITY_FAILED -> "Play Integrity 证明失败（设备环境或 Play 服务异常）"
        NETWORK -> "App Check 网络请求超时或不可达"
        CONFIGURATION -> "App Check 配置错误（应用未注册或提供商不可用）"
        UNKNOWN -> "App Check 校验失败（未知原因）"
    }
}

/**
 * Snapshot of the three dependencies behind a Firestore refusal:
 * Google Auth session, App Check device attestation and network reachability.
 */
data class SyncDiagnosticReport(
    val authStatus: SyncProbeStatus,
    val authDetail: String?,
    val appCheckStatus: SyncProbeStatus,
    val appCheckFailure: AppCheckFailureKind,
    val appCheckDetail: String?,
    val networkAvailable: Boolean,
    val firestoreCode: String?,
    val capturedAtMillis: Long,
    val debugTokenInfo: AppCheckDebugTokenInfo? = null
) {
    val hasFailure: Boolean
        get() = authStatus == SyncProbeStatus.FAILED ||
            appCheckStatus == SyncProbeStatus.FAILED ||
            !networkAvailable
}

fun CredentialRefreshResult.toSyncDiagnosticReport(
    firestoreCode: String? = null,
    debugTokenInfo: AppCheckDebugTokenInfo? = null
): SyncDiagnosticReport = SyncDiagnosticReport(
    authStatus = if (authOk) SyncProbeStatus.OK else SyncProbeStatus.FAILED,
    authDetail = authError,
    appCheckStatus = if (appCheckOk) SyncProbeStatus.OK else SyncProbeStatus.FAILED,
    appCheckFailure = appCheckFailure,
    appCheckDetail = appCheckError,
    networkAvailable = networkAvailable,
    firestoreCode = firestoreCode,
    capturedAtMillis = capturedAtMillis,
    debugTokenInfo = debugTokenInfo
)

/**
 * Firebase App Check 20.x does not expose a typed exception (no FirebaseAppCheckException
 * class), so failures are classified from the exception chain. Debug-provider exchange
 * failures explicitly mention "debug token"; Play Integrity failures carry its name.
 */
fun classifyAppCheckFailure(error: Throwable?, debugBuild: Boolean): AppCheckFailureKind {
    if (error == null) return AppCheckFailureKind.NONE
    val chain = generateSequence(error) { it.cause }.toList()
    val text = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
    return when {
        text.contains("debug token") || text.contains("debugtoken") || text.contains("debug_token") ->
            AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED
        text.contains("play integrity") || text.contains("playintegrity") ->
            AppCheckFailureKind.PLAY_INTEGRITY_FAILED
        text.contains("timeout") || text.contains("timed out") ||
            text.contains("unable to resolve host") || text.contains("network") ||
            text.contains("socket") || text.contains("connect") ||
            chain.any { it is IOException } -> AppCheckFailureKind.NETWORK
        text.contains("configuration") || text.contains("provider") ||
            text.contains("api key") || text.contains("not installed") ||
            text.contains("permission") || text.contains("forbidden") ||
            text.contains("unauthorized") -> AppCheckFailureKind.CONFIGURATION
        debugBuild && text.contains("403") -> AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED
        else -> AppCheckFailureKind.UNKNOWN
    }
}

/** Human-readable Auth failure classification; message text stays debug-only at call sites. */
fun classifyAuthFailure(error: Throwable?): String = when {
    error == null -> "登录会话不可用"
    generateSequence(error) { it.cause }.any { it is IOException } -> "网络不可用或 Auth 请求超时"
    else -> "登录会话刷新失败，请重新登录 Google 账号"
}