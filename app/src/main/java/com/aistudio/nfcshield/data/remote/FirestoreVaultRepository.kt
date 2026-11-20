package com.aistudio.nfcshield.data.remote

import android.content.Context
import android.util.Log
import com.aistudio.nfcshield.R
import com.aistudio.nfcshield.crypto.WrappedDekPayload
import com.aistudio.nfcshield.data.model.CardAccentColor
import com.aistudio.nfcshield.data.model.CardCategory
import com.aistudio.nfcshield.data.model.EncryptedCardDoc
import com.aistudio.nfcshield.data.model.NfcTechType
import com.aistudio.nfcshield.data.model.VaultConfigDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreVaultRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("请先使用 Google 账号登录后再访问云端保险库。")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "当前登录账号已切换，已拒绝跨账号数据操作。" }
        }
        return uid
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
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
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
        val path = "users/$uid/cards"
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
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
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
}
