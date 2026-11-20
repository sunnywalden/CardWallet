package com.aistudio.nfcshield.data.remote

import android.util.Log
import com.aistudio.nfcshield.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Logs Firestore failures with redacted, non-identifying metadata.
 *
 * Never include the account email, provider list or tenant id: logcat entries end up in
 * bug reports and crash tooling. The uid is logged only as a truncated SHA-256 prefix so
 * support can correlate sessions without revealing the identity.
 */
fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val firestoreCode = (rootError as? FirebaseFirestoreException)?.code
    val authUid = FirebaseAuth.getInstance().currentUser?.uid
    val uidHash = authUid?.let(::hashUidPrefix)
    // The path embeds the raw Firebase UID; replace it with the same truncated hash so the
    // account identifier never reaches logcat (threat model T7).
    val safePath = path?.let { redactUidInPath(it, authUid ?: "", authUid) }

    val errorInfoJson = JSONObject().apply {
        put("errorCode", firestoreCode?.name ?: rootError.javaClass.simpleName)
        put("operationType", operationType.value)
        put("path", safePath)
        put("uidHash", uidHash)
        if (BuildConfig.DEBUG) {
            put("message", rootError.message ?: rootError.toString())
        }
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}