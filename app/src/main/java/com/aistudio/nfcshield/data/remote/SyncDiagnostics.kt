package com.aistudio.nfcshield.data.remote

import com.aistudio.nfcshield.BuildConfig
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.DateFormat
import java.util.Date

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
        PLAY_INTEGRITY_FAILED ->
            "Play Integrity 证明失败（设备完整性未通过，或侧载安装的签名/来源不被 Play 认可）"
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
    val debugTokenInfo: AppCheckDebugTokenInfo? = null,
    val debugBuild: Boolean = false
) {
    val hasFailure: Boolean
        get() = authStatus == SyncProbeStatus.FAILED ||
            appCheckStatus == SyncProbeStatus.FAILED ||
            !networkAvailable
}

fun CredentialRefreshResult.toSyncDiagnosticReport(
    firestoreCode: String? = null,
    debugTokenInfo: AppCheckDebugTokenInfo? = null,
    debugBuild: Boolean = BuildConfig.DEBUG
): SyncDiagnosticReport = SyncDiagnosticReport(
    authStatus = if (authOk) SyncProbeStatus.OK else SyncProbeStatus.FAILED,
    authDetail = authError,
    appCheckStatus = if (appCheckOk) SyncProbeStatus.OK else SyncProbeStatus.FAILED,
    appCheckFailure = appCheckFailure,
    appCheckDetail = appCheckError,
    networkAvailable = networkAvailable,
    firestoreCode = firestoreCode,
    capturedAtMillis = capturedAtMillis,
    debugTokenInfo = debugTokenInfo,
    debugBuild = debugBuild
)

/** Support-friendly report text (never includes the debug token value). */
fun SyncDiagnosticReport.toDiagnosticText(): String = buildString {
    appendLine("盾匙 NFC 云端同步诊断")
    appendLine("构建: ${if (debugBuild) "Debug / App Check Debug Provider" else "Release / Play Integrity"}")
    appendLine("Auth: ${statusLabel(authStatus)}${authDetail?.let { " ($it)" }.orEmpty()}")
    appendLine("App Check: ${statusLabel(appCheckStatus)} [${appCheckFailure.name}]")
    appCheckDetail?.takeIf { it.isNotBlank() }?.let { appendLine("  detail: $it") }
    appendLine("网络: ${if (networkAvailable) "可用" else "不可用"}")
    firestoreCode?.let { appendLine("Firestore: $it") }
    appendLine(
        "时间: " + runCatching {
            DateFormat.getDateTimeInstance().format(Date(capturedAtMillis))
        }.getOrDefault(capturedAtMillis.toString())
    )
}

private fun statusLabel(status: SyncProbeStatus): String =
    if (status == SyncProbeStatus.OK) "正常" else "受限"

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
        // "IntegrityServiceException", "-10006: Integrity API error", standard-request and
        // Play-recognition errors all indicate the Play Integrity path, not the network.
        text.contains("integrity") || text.contains("-1000") ||
            text.contains("not installed by play") || text.contains("app not recognized") ||
            text.contains("unrecognized_version") || text.contains("play_recognized") ->
            AppCheckFailureKind.PLAY_INTEGRITY_FAILED
        debugBuild && text.contains("code: 403") ->
            AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED
        text.contains("timeout") || text.contains("timed out") ||
            text.contains("unable to resolve host") || text.contains("network") ||
            text.contains("socket") || text.contains("connect") ||
            Regex("code: (429|5\\d\\d)").containsMatchIn(text) ||
            chain.any {
                it is SocketTimeoutException || it is UnknownHostException ||
                    it is ConnectException || it is SocketException
            } -> AppCheckFailureKind.NETWORK
        text.contains("code: 403") ||
            text.contains("configuration") || text.contains("provider") ||
            text.contains("api key") || text.contains("not installed") ||
            text.contains("permission") || text.contains("forbidden") ||
            text.contains("unauthorized") -> AppCheckFailureKind.CONFIGURATION
        else -> AppCheckFailureKind.UNKNOWN
    }
}

/** Human-readable Auth failure classification; message text stays debug-only at call sites. */
fun classifyAuthFailure(error: Throwable?): String = when {
    error == null -> "登录会话不可用"
    generateSequence(error) { it.cause }.any { it is IOException } -> "网络不可用或 Auth 请求超时"
    else -> "登录会话刷新失败，请重新登录 Google 账号"
}