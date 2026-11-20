package com.aistudio.nfcshield.data.remote

import android.content.Context
import android.util.Log
import com.aistudio.nfcshield.BuildConfig
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.crypto.WrappedDekPayload
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.EncryptedCardDoc
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.data.model.VaultConfigDoc
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreVaultRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val networkProbe: () -> Boolean = { true }
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth,
        { NetworkStatus.isAvailable(context.applicationContext) }
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("请先使用 Google 账号登录后再访问云端保险库。")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "当前登录账号已切换，已拒绝跨账号数据操作。" }
        }
        return uid
    }

    fun networkAvailable(): Boolean = runCatching { networkProbe() }.getOrDefault(true)

    /**
     * Proactively refreshes Firebase Auth ID token and Firebase App Check token, returning a
     * classified result for the sync diagnostics UI. Prevents transient PERMISSION_DENIED
     * errors when the app resumes after device lock/sleep and the cached token expired while
     * the process was suspended (Doze / Play Integrity round-trips can take several seconds).
     */
    suspend fun refreshSessionCredentials(forceRefresh: Boolean = true): CredentialRefreshResult {
        val capturedAt = System.currentTimeMillis()
        val network = networkAvailable()
        val user = auth.currentUser

        var authOk = false
        var authError: String? = null
        if (user == null) {
            authError = "未登录 Google 账号"
        } else {
            try {
                val tokenResult = withTimeoutOrNull(TOKEN_REFRESH_TIMEOUT_MS) {
                    user.getIdToken(forceRefresh).await()
                }
                authOk = tokenResult?.token?.isNotBlank() == true
                if (!authOk) {
                    authError = "Auth 令牌刷新超时（${TOKEN_REFRESH_TIMEOUT_MS / 1000} 秒）"
                    Log.w(
                        SYNC_LOG_TAG,
                        "Auth ID token refresh timed out or returned blank (forceRefresh=$forceRefresh, uidHash=${hashUidPrefix(user.uid)})"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                authError = classifyAuthFailure(e)
                Log.w(
                    SYNC_LOG_TAG,
                    "Auth ID token refresh failed (forceRefresh=$forceRefresh, uidHash=${hashUidPrefix(user.uid)}, type=${e.javaClass.simpleName})"
                )
            }
        }

        var appCheckOk = false
        var appCheckFailure = AppCheckFailureKind.NONE
        var appCheckError: String? = null
        try {
            val token = withTimeoutOrNull(TOKEN_REFRESH_TIMEOUT_MS) {
                FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await()
            }
            appCheckOk = token?.token?.isNotBlank() == true
            if (!appCheckOk) {
                appCheckFailure = AppCheckFailureKind.NETWORK
                appCheckError = "App Check 令牌刷新超时（${TOKEN_REFRESH_TIMEOUT_MS / 1000} 秒）"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            appCheckFailure = classifyAppCheckFailure(e, BuildConfig.DEBUG)
            appCheckError = e.message
            Log.w(
                SYNC_LOG_TAG,
                "App Check token refresh failed (forceRefresh=$forceRefresh, kind=$appCheckFailure, type=${e.javaClass.simpleName})"
            )
        }

        return CredentialRefreshResult(
            authOk = authOk,
            authError = authError,
            appCheckOk = appCheckOk,
            appCheckFailure = appCheckFailure,
            appCheckError = appCheckError,
            networkAvailable = network,
            capturedAtMillis = capturedAt
        )
    }

    private fun isRecoverableResumeError(error: Throwable): FirebaseFirestoreException? {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull() ?: return null
        return when (firestoreError.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED -> firestoreError
            else -> null
        }
    }

    fun observeVaultConfig(userId: String): Flow<VaultConfigDoc?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/vault/config"
        emitAll(
            db.collection("users").document(uid).collection("vault").document("config")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        VaultConfigDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }.retryWhen { error, attempt ->
        val recoverable = isRecoverableResumeError(error)
        val stillSameUser = auth.currentUser?.uid == userId
        if (recoverable != null && stillSameUser && attempt < MAX_STREAM_RETRIES) {
            Log.w(
                SYNC_LOG_TAG,
                "Transient vault config sync error (code=${recoverable.code}, attempt=${attempt + 1}/$MAX_STREAM_RETRIES, uidHash=${hashUidPrefix(userId)}); refreshing credentials and retrying",
                recoverable
            )
            refreshSessionCredentials(forceRefresh = true)
            delay(RETRY_BASE_DELAY_MS shl attempt.toInt())
            true
        } else {
            false
        }
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        val path = "users/$userId/vault/config"
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            Log.e(
                SYNC_LOG_TAG,
                "Vault config listener failed after retries [code=${firestoreError?.code ?: error.javaClass.simpleName}, uidHash=${hashUidPrefix(userId)}]: ${error.message}",
                exceptionToLog
            )
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
        }
        throw error
    }

    suspend fun saveVaultConfig(
        userId: String,
        wrapped: WrappedDekPayload,
        isUpdate: Boolean
    ): Result<Unit> {
        val path = "users/$userId/vault/config"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("vault").document("config")
            val bindingFields = wrapped.deviceBinding
                ?.takeIf { it.isNotBlank() }
                ?.let { mapOf("deviceBinding" to it) }
                .orEmpty()
            if (isUpdate) {
                docRef.update(
                    mapOf(
                        "wrappedDekBase64" to wrapped.wrappedDekBase64,
                        "wrappedDekIvBase64" to wrapped.wrappedDekIvBase64,
                        "kdfSaltBase64" to wrapped.kdfSaltBase64,
                        "kdfIterations" to wrapped.kdfIterations.toLong(),
                        "pinVerifierBase64" to wrapped.pinVerifierBase64,
                        "kdfAlgorithm" to wrapped.kdfAlgorithm,
                        "schemaVersion" to wrapped.schemaVersion.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ) + bindingFields
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "wrappedDekBase64" to wrapped.wrappedDekBase64,
                        "wrappedDekIvBase64" to wrapped.wrappedDekIvBase64,
                        "kdfSaltBase64" to wrapped.kdfSaltBase64,
                        "kdfIterations" to wrapped.kdfIterations.toLong(),
                        "pinVerifierBase64" to wrapped.pinVerifierBase64,
                        "kdfAlgorithm" to wrapped.kdfAlgorithm,
                        "schemaVersion" to wrapped.schemaVersion.toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ) + bindingFields
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeEncryptedCards(userId: String): Flow<List<EncryptedCardDoc>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("users").document(uid).collection("cards")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EncryptedCardDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }.retryWhen { error, attempt ->
        val recoverable = isRecoverableResumeError(error)
        val stillSameUser = auth.currentUser?.uid == userId
        if (recoverable != null && stillSameUser && attempt < MAX_STREAM_RETRIES) {
            Log.w(
                SYNC_LOG_TAG,
                "Transient cards sync error (code=${recoverable.code}, attempt=${attempt + 1}/$MAX_STREAM_RETRIES, uidHash=${hashUidPrefix(userId)}); refreshing credentials and retrying",
                recoverable
            )
            refreshSessionCredentials(forceRefresh = true)
            delay(RETRY_BASE_DELAY_MS shl attempt.toInt())
            true
        } else {
            false
        }
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        val path = "users/$userId/cards"
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            Log.e(
                SYNC_LOG_TAG,
                "Cards listener failed after retries [code=${firestoreError?.code ?: error.javaClass.simpleName}, uidHash=${hashUidPrefix(userId)}]: ${error.message}",
                exceptionToLog
            )
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
    }

    suspend fun createCard(
        userId: String,
        label: String,
        category: CardCategory,
        accentColor: CardAccentColor,
        primaryTech: NfcTechType,
        maskedUid: String,
        hceSupported: Boolean,
        activeHce: Boolean,
        encryptedPayloadBase64: String,
        payloadIvBase64: String,
        cryptoVersion: Int = 2
    ): Result<String> {
        val cleanLabel = label.trim().take(40)
        val safeMaskedUid = maskedUid.take(24)
        val path = "users/$userId/cards"
        return try {
            val uid = requireUserId(userId)
            val violations = validateCardWriteFields(
                userId = uid,
                label = cleanLabel,
                category = category,
                accentColor = accentColor,
                primaryTech = primaryTech,
                maskedUid = safeMaskedUid,
                encryptedPayloadBase64 = encryptedPayloadBase64,
                payloadIvBase64 = payloadIvBase64,
                cryptoVersion = cryptoVersion,
                isCreate = true
            )
            if (violations.isNotEmpty()) {
                val msg = "卡片字段校验失败：${violations.joinToString("；")}"
                Log.e(
                    CARD_SAVE_LOG_TAG,
                    "createCard pre-flight validation failed: violations=$violations, " +
                        buildRedactedCardDiagnostic(
                            op = "createCard",
                            expectedUserId = userId,
                            authUid = uid,
                            cardId = "(pending)",
                            labelLen = cleanLabel.length,
                            category = category,
                            accentColor = accentColor,
                            primaryTech = primaryTech,
                            maskedUidLen = safeMaskedUid.length,
                            hceSupported = hceSupported,
                            activeHce = activeHce,
                            encryptedPayloadLen = encryptedPayloadBase64.length,
                            payloadIvLen = payloadIvBase64.length,
                            cryptoVersion = cryptoVersion,
                            deactivateCount = 0
                        )
                )
                return Result.failure(IllegalArgumentException(msg))
            }

            val docRef = db.collection("users").document(uid).collection("cards").document()
            val payload = mapOf(
                "userId" to uid,
                "label" to cleanLabel,
                "category" to category.name,
                "accentColor" to accentColor.name,
                "primaryTech" to primaryTech.name,
                "maskedUid" to safeMaskedUid,
                "hceSupported" to hceSupported,
                "activeHce" to activeHce,
                "encryptedPayloadBase64" to encryptedPayloadBase64,
                "payloadIvBase64" to payloadIvBase64,
                "cryptoVersion" to cryptoVersion.toLong(),
                "emulationCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logCardSaveFailure(
                op = "createCard",
                path = path,
                expectedUserId = userId,
                cardId = "(auto)",
                labelLen = cleanLabel.length,
                category = category,
                accentColor = accentColor,
                primaryTech = primaryTech,
                maskedUidLen = safeMaskedUid.length,
                hceSupported = hceSupported,
                activeHce = activeHce,
                encryptedPayloadLen = encryptedPayloadBase64.length,
                payloadIvLen = payloadIvBase64.length,
                cryptoVersion = cryptoVersion,
                deactivateCount = 0,
                exception = e
            )
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Creates a card that immediately becomes the active HCE slot, atomically deactivating
     * every previously active card in the same batch. A crash mid-way can therefore never
     * leave two cards active.
     */
    suspend fun createCardAndActivate(
        userId: String,
        label: String,
        category: CardCategory,
        accentColor: CardAccentColor,
        primaryTech: NfcTechType,
        maskedUid: String,
        hceSupported: Boolean,
        encryptedPayloadBase64: String,
        payloadIvBase64: String,
        deactivateCardIds: List<String>,
        cryptoVersion: Int = 2
    ): Result<String> {
        val cleanLabel = label.trim().take(40)
        val safeMaskedUid = maskedUid.take(24)
        val validDeactivateIds = deactivateCardIds
            .map { it.trim() }
            .filter { VALID_DOC_ID_REGEX.matches(it) }
            .distinct()
        val path = "users/$userId/cards"
        return try {
            val uid = requireUserId(userId)
            val violations = validateCardWriteFields(
                userId = uid,
                label = cleanLabel,
                category = category,
                accentColor = accentColor,
                primaryTech = primaryTech,
                maskedUid = safeMaskedUid,
                encryptedPayloadBase64 = encryptedPayloadBase64,
                payloadIvBase64 = payloadIvBase64,
                cryptoVersion = cryptoVersion,
                isCreate = true
            )
            if (violations.isNotEmpty()) {
                val msg = "卡片字段校验失败：${violations.joinToString("；")}"
                Log.e(
                    CARD_SAVE_LOG_TAG,
                    "createCardAndActivate pre-flight validation failed: violations=$violations, " +
                        buildRedactedCardDiagnostic(
                            op = "createCardAndActivate",
                            expectedUserId = userId,
                            authUid = uid,
                            cardId = "(pending)",
                            labelLen = cleanLabel.length,
                            category = category,
                            accentColor = accentColor,
                            primaryTech = primaryTech,
                            maskedUidLen = safeMaskedUid.length,
                            hceSupported = hceSupported,
                            activeHce = true,
                            encryptedPayloadLen = encryptedPayloadBase64.length,
                            payloadIvLen = payloadIvBase64.length,
                            cryptoVersion = cryptoVersion,
                            deactivateCount = validDeactivateIds.size
                        )
                )
                return Result.failure(IllegalArgumentException(msg))
            }

            if (validDeactivateIds.size != deactivateCardIds.size) {
                Log.e(
                    CARD_SAVE_LOG_TAG,
                    "createCardAndActivate filtered invalid deactivateCardIds: rawCount=${deactivateCardIds.size}, validCount=${validDeactivateIds.size}"
                )
            }

            val cardsRef = db.collection("users").document(uid).collection("cards")
            val docRef = cardsRef.document()
            val batch = db.batch()
            batch.set(
                docRef,
                mapOf(
                    "userId" to uid,
                    "label" to cleanLabel,
                    "category" to category.name,
                    "accentColor" to accentColor.name,
                    "primaryTech" to primaryTech.name,
                    "maskedUid" to safeMaskedUid,
                    "hceSupported" to hceSupported,
                    "activeHce" to true,
                    "encryptedPayloadBase64" to encryptedPayloadBase64,
                    "payloadIvBase64" to payloadIvBase64,
                    "cryptoVersion" to cryptoVersion.toLong(),
                    "emulationCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            validDeactivateIds.filter { it != docRef.id }.forEach { oldId ->
                batch.update(
                    cardsRef.document(oldId),
                    mapOf("activeHce" to false, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logCardSaveFailure(
                op = "createCardAndActivate",
                path = path,
                expectedUserId = userId,
                cardId = "(auto-batch)",
                labelLen = cleanLabel.length,
                category = category,
                accentColor = accentColor,
                primaryTech = primaryTech,
                maskedUidLen = safeMaskedUid.length,
                hceSupported = hceSupported,
                activeHce = true,
                encryptedPayloadLen = encryptedPayloadBase64.length,
                payloadIvLen = payloadIvBase64.length,
                cryptoVersion = cryptoVersion,
                deactivateCount = validDeactivateIds.size,
                exception = e
            )
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCard(
        userId: String,
        cardId: String,
        label: String,
        category: CardCategory,
        accentColor: CardAccentColor,
        primaryTech: NfcTechType,
        maskedUid: String,
        hceSupported: Boolean,
        activeHce: Boolean,
        encryptedPayloadBase64: String,
        payloadIvBase64: String,
        cryptoVersion: Int = 2
    ): Result<Unit> {
        val cleanLabel = label.trim().take(40)
        val safeMaskedUid = maskedUid.take(24)
        val path = "users/$userId/cards/$cardId"
        return try {
            val uid = requireUserId(userId)
            require(VALID_DOC_ID_REGEX.matches(cardId)) { "卡片 ID 无效" }
            val violations = validateCardWriteFields(
                userId = uid,
                label = cleanLabel,
                category = category,
                accentColor = accentColor,
                primaryTech = primaryTech,
                maskedUid = safeMaskedUid,
                encryptedPayloadBase64 = encryptedPayloadBase64,
                payloadIvBase64 = payloadIvBase64,
                cryptoVersion = cryptoVersion,
                isCreate = false
            )
            if (violations.isNotEmpty()) {
                val msg = "卡片字段校验失败：${violations.joinToString("；")}"
                Log.e(
                    CARD_SAVE_LOG_TAG,
                    "updateCard pre-flight validation failed: violations=$violations, " +
                        buildRedactedCardDiagnostic(
                            op = "updateCard",
                            expectedUserId = userId,
                            authUid = uid,
                            cardId = cardId,
                            labelLen = cleanLabel.length,
                            category = category,
                            accentColor = accentColor,
                            primaryTech = primaryTech,
                            maskedUidLen = safeMaskedUid.length,
                            hceSupported = hceSupported,
                            activeHce = activeHce,
                            encryptedPayloadLen = encryptedPayloadBase64.length,
                            payloadIvLen = payloadIvBase64.length,
                            cryptoVersion = cryptoVersion,
                            deactivateCount = 0
                        )
                )
                return Result.failure(IllegalArgumentException(msg))
            }

            db.collection("users").document(uid).collection("cards").document(cardId)
                .update(
                    mapOf(
                        "label" to cleanLabel,
                        "category" to category.name,
                        "accentColor" to accentColor.name,
                        "primaryTech" to primaryTech.name,
                        "maskedUid" to safeMaskedUid,
                        "hceSupported" to hceSupported,
                        "activeHce" to activeHce,
                        "encryptedPayloadBase64" to encryptedPayloadBase64,
                        "payloadIvBase64" to payloadIvBase64,
                        "cryptoVersion" to cryptoVersion.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logCardSaveFailure(
                op = "updateCard",
                path = path,
                expectedUserId = userId,
                cardId = cardId,
                labelLen = cleanLabel.length,
                category = category,
                accentColor = accentColor,
                primaryTech = primaryTech,
                maskedUidLen = safeMaskedUid.length,
                hceSupported = hceSupported,
                activeHce = activeHce,
                encryptedPayloadLen = encryptedPayloadBase64.length,
                payloadIvLen = payloadIvBase64.length,
                cryptoVersion = cryptoVersion,
                deactivateCount = 0,
                exception = e
            )
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setActiveHceCard(
        userId: String,
        targetCardId: String?,
        currentlyActiveIds: List<String>
    ): Result<Unit> {
        val path = "users/$userId/cards"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val cardsRef = db.collection("users").document(uid).collection("cards")
            currentlyActiveIds.filter { it != targetCardId }.forEach { oldId ->
                batch.update(
                    cardsRef.document(oldId),
                    mapOf("activeHce" to false, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            if (!targetCardId.isNullOrBlank()) {
                batch.update(
                    cardsRef.document(targetCardId),
                    mapOf("activeHce" to true, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(
                CARD_SAVE_LOG_TAG,
                "setActiveHceCard failed [path=$path]: targetCardId=${targetCardId ?: "null"}, " +
                    "deactivateCount=${currentlyActiveIds.size}, signedIn=${auth.currentUser != null}, " +
                    "uidMatch=${auth.currentUser?.uid == userId} | message=${e.message}",
                e
            )
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordHceEmulation(
        userId: String,
        cardId: String
    ): Result<Unit> {
        val path = "users/$userId/cards/$cardId"
        return try {
            val uid = requireUserId(userId)
            require(cardId.isNotBlank()) { "卡片 ID 无效" }
            db.collection("users").document(uid).collection("cards").document(cardId)
                .update(
                    mapOf(
                        "emulationCount" to FieldValue.increment(1),
                        "lastEmulatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCard(userId: String, cardId: String): Result<Unit> {
        val path = "users/$userId/cards/$cardId"
        return try {
            val uid = requireUserId(userId)
            require(cardId.isNotBlank()) { "卡片 ID 无效" }
            db.collection("users").document(uid).collection("cards").document(cardId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logCardSaveFailure(
        op: String,
        path: String,
        expectedUserId: String,
        cardId: String,
        labelLen: Int,
        category: CardCategory,
        accentColor: CardAccentColor,
        primaryTech: NfcTechType,
        maskedUidLen: Int,
        hceSupported: Boolean,
        activeHce: Boolean,
        encryptedPayloadLen: Int,
        payloadIvLen: Int,
        cryptoVersion: Int,
        deactivateCount: Int,
        exception: Throwable
    ) {
        logCardSaveException(
            op = op,
            path = path,
            expectedUserId = expectedUserId,
            authUid = auth.currentUser?.uid,
            cardId = cardId,
            labelLen = labelLen,
            category = category,
            accentColor = accentColor,
            primaryTech = primaryTech,
            maskedUidLen = maskedUidLen,
            hceSupported = hceSupported,
            activeHce = activeHce,
            encryptedPayloadLen = encryptedPayloadLen,
            payloadIvLen = payloadIvLen,
            cryptoVersion = cryptoVersion,
            deactivateCount = deactivateCount,
            exception = exception,
            emulationCount = if (op == "updateCard") null else 0L
        )
    }

    companion object {
        private const val SYNC_LOG_TAG = "VaultSync"
        private const val MAX_STREAM_RETRIES = 4L
        private const val RETRY_BASE_DELAY_MS = 1000L
        private const val TOKEN_REFRESH_TIMEOUT_MS = 10_000L
    }
}
