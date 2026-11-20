package com.aistudio.nfcshield

import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenInfo
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenResolver
import com.aistudio.nfcshield.data.remote.AppCheckDebugTokenSource
import com.aistudio.nfcshield.data.remote.AppCheckFailureKind
import com.aistudio.nfcshield.data.remote.CredentialRefreshResult
import com.aistudio.nfcshield.data.remote.SyncDiagnosticReport
import com.aistudio.nfcshield.data.remote.SyncProbeStatus
import com.aistudio.nfcshield.data.remote.classifyAppCheckFailure
import com.aistudio.nfcshield.data.remote.classifyAuthFailure
import com.aistudio.nfcshield.data.remote.toDiagnosticText
import com.aistudio.nfcshield.data.remote.toSyncDiagnosticReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class SyncDiagnosticsTest {

    @Test
    fun `classifies debug token exchange refusal as unregistered token`() {
        val error = RuntimeException(
            "com.google.android.gms.tasks.RuntimeExecutionException",
            IOException("Failed to exchange debug token (403 Forbidden). If you haven't registered it…")
        )
        assertEquals(
            AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED,
            classifyAppCheckFailure(error, debugBuild = true)
        )
    }

    @Test
    fun `classifies play integrity failures before generic IO errors`() {
        val error = IOException("Play Integrity API returned an error")
        assertEquals(
            AppCheckFailureKind.PLAY_INTEGRITY_FAILED,
            classifyAppCheckFailure(error, debugBuild = false)
        )
    }

    @Test
    fun `classifies Play Integrity service errors and sideload recognition failures`() {
        val integrity = IllegalStateException(
            "com.google.android.play.core.integrity.IntegrityServiceException: -10006: Integrity API error."
        )
        assertEquals(
            AppCheckFailureKind.PLAY_INTEGRITY_FAILED,
            classifyAppCheckFailure(integrity, debugBuild = false)
        )
        assertEquals(
            AppCheckFailureKind.PLAY_INTEGRITY_FAILED,
            classifyAppCheckFailure(
                IOException("The app is not installed by Play and returned UNRECOGNIZED_VERSION"),
                debugBuild = false
            )
        )
    }

    @Test
    fun `debug build classifies a 403 exchange as an unregistered token`() {
        val error = IOException(
            "Error returned from API. code: 403 body: {\"error\":{\"message\":\"denied\"}}"
        )
        assertEquals(
            AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED,
            classifyAppCheckFailure(error, debugBuild = true)
        )
        // The same 403 in release is an attestation/config problem, not a debug token.
        assertEquals(
            AppCheckFailureKind.CONFIGURATION,
            classifyAppCheckFailure(error, debugBuild = false)
        )
    }

    @Test
    fun `diagnostic text includes build type, classifications and never the debug token`() {
        val report = SyncDiagnosticReport(
            authStatus = SyncProbeStatus.OK,
            authDetail = null,
            appCheckStatus = SyncProbeStatus.FAILED,
            appCheckFailure = AppCheckFailureKind.PLAY_INTEGRITY_FAILED,
            appCheckDetail = "IntegrityServiceException: -10006",
            networkAvailable = true,
            firestoreCode = "PERMISSION_DENIED",
            capturedAtMillis = 1_700_000_000_000L,
            debugTokenInfo = AppCheckDebugTokenInfo(
                token = "secret-debug-token-value",
                source = AppCheckDebugTokenSource.SECRETS
            ),
            debugBuild = false
        )
        val text = report.toDiagnosticText()
        assertTrue(text.contains("Release / Play Integrity"))
        assertTrue(text.contains("PLAY_INTEGRITY_FAILED"))
        assertTrue(text.contains("PERMISSION_DENIED"))
        assertTrue(text.contains("IntegrityServiceException"))
        assertTrue(!text.contains("secret-debug-token-value"))
    }

    @Test
    fun `classifies timeouts, unavailable network and unknown failures`() {
        assertEquals(
            AppCheckFailureKind.NETWORK,
            classifyAppCheckFailure(SocketTimeoutException("timeout"), debugBuild = true)
        )
        assertEquals(
            AppCheckFailureKind.NETWORK,
            classifyAppCheckFailure(IOException("Unable to resolve host firebaseappcheck.googleapis.com"), debugBuild = false)
        )
        assertEquals(
            AppCheckFailureKind.UNKNOWN,
            classifyAppCheckFailure(IllegalStateException("unexpected"), debugBuild = false)
        )
        assertEquals(AppCheckFailureKind.NONE, classifyAppCheckFailure(null, debugBuild = true))
    }

    @Test
    fun `credential refresh result maps to a diagnostic report without losing state`() {
        val result = CredentialRefreshResult(
            authOk = false,
            authError = "登录会话刷新失败",
            appCheckOk = false,
            appCheckFailure = AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED,
            appCheckError = "403",
            networkAvailable = true,
            capturedAtMillis = 123L
        )

        val report = result.toSyncDiagnosticReport(firestoreCode = "PERMISSION_DENIED")

        assertEquals(SyncProbeStatus.FAILED, report.authStatus)
        assertEquals(SyncProbeStatus.FAILED, report.appCheckStatus)
        assertEquals(AppCheckFailureKind.DEBUG_TOKEN_UNREGISTERED, report.appCheckFailure)
        assertEquals("PERMISSION_DENIED", report.firestoreCode)
        assertTrue(report.networkAvailable)
        assertTrue(report.hasFailure)
        assertEquals(123L, report.capturedAtMillis)
    }

    @Test
    fun `debug token resolver prefers intent over secrets and ignores blanks`() {
        val fromIntent = AppCheckDebugTokenResolver.resolve(
            intentToken = "  intent-token  ",
            secretsToken = "secrets-token"
        )
        assertEquals("intent-token", fromIntent?.token)
        assertEquals(AppCheckDebugTokenSource.INTENT, fromIntent?.source)

        val fromSecrets = AppCheckDebugTokenResolver.resolve(" ", " secrets-token ")
        assertEquals("secrets-token", fromSecrets?.token)
        assertEquals(AppCheckDebugTokenSource.SECRETS, fromSecrets?.source)

        assertNull(AppCheckDebugTokenResolver.resolve(null, ""))
    }

    @Test
    fun `auth failure classification distinguishes network from session errors`() {
        assertEquals(
            "网络不可用或 Auth 请求超时",
            classifyAuthFailure(IOException("Unable to resolve host"))
        )
        assertEquals(
            "登录会话刷新失败，请重新登录 Google 账号",
            classifyAuthFailure(IllegalStateException("user token revoked"))
        )
    }
}