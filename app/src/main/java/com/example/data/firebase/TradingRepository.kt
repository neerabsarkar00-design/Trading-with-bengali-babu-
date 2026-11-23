package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.analytics.TradingAnalytics
import com.example.data.model.AccountStatus
import com.example.data.model.DisciplineEntry
import com.example.data.model.InviteStatus
import com.example.data.model.StudentInvite
import com.example.data.model.TradeEntry
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun isVerifiedAdmin(user: FirebaseUser? = auth.currentUser): Boolean {
        val email = user?.email?.trim()?.lowercase(Locale.US)
        return user != null && user.isEmailVerified && email == ADMIN_EMAIL
    }

    suspend fun syncOrCreateUserProfile(firebaseUser: FirebaseUser): Result<UserProfile> {
        val docPath = "users/${firebaseUser.uid}"
        return try {
            val uid = firebaseUser.uid
            val email = (firebaseUser.email ?: "").trim().lowercase(Locale.US)
            val isAdmin = isVerifiedAdmin(firebaseUser)
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()

            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    if (isAdmin && existing.roleEnum != UserRole.ADMIN) {
                        docRef.update(
                            mapOf(
                                "role" to UserRole.ADMIN.name,
                                "status" to AccountStatus.ACTIVE.name,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        return Result.success(
                            existing.copy(
                                role = UserRole.ADMIN.name,
                                status = AccountStatus.ACTIVE.name
                            )
                        )
                    }
                    return Result.success(existing)
                }
            }

            // Check if Admin pre-invited this student
            var matchedInvite: StudentInvite? = null
            if (!isAdmin && email.isNotBlank() && firebaseUser.isEmailVerified) {
                try {
                    val inviteSnap = db.collection("student_invites")
                        .whereEqualTo("email", email)
                        .get()
                        .await()
                    matchedInvite = inviteSnap.toObjects(
                        StudentInvite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).firstOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, "student_invites")
                }
            }

            if (matchedInvite?.statusEnum == InviteStatus.SUSPENDED) {
                return Result.success(
                    UserProfile(
                        id = uid,
                        userId = uid,
                        name = matchedInvite.name,
                        email = email,
                        role = UserRole.STUDENT.name,
                        status = AccountStatus.SUSPENDED.name,
                        tradingCapital = matchedInvite.tradingCapital,
                        dailyGoal = matchedInvite.dailyGoal,
                        dailyMaxLoss = matchedInvite.dailyMaxLoss,
                        maxTradesPerDay = matchedInvite.maxTradesPerDay
                    )
                )
            }

            val displayName = (matchedInvite?.name?.takeIf { it.isNotBlank() }
                ?: firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Trader" })
                .take(80)

            val role = if (isAdmin) UserRole.ADMIN.name else UserRole.STUDENT.name
            val capital = matchedInvite?.tradingCapital ?: 100000.0
            val goal = matchedInvite?.dailyGoal ?: 2500.0
            val maxLoss = matchedInvite?.dailyMaxLoss ?: 1500.0
            val maxTrades = (matchedInvite?.maxTradesPerDay ?: 3L).coerceIn(1L, 100L)

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "name" to displayName,
                "email" to email.ifBlank { "$uid@trader.local" }.take(120),
                "role" to role,
                "status" to AccountStatus.ACTIVE.name,
                "tradingCapital" to capital,
                "dailyGoal" to goal,
                "dailyMaxLoss" to maxLoss,
                "maxTradesPerDay" to maxTrades,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firebaseUser.photoUrl?.toString()?.take(500)?.let { url ->
                if (url.isNotBlank()) payload["photoUrl"] = url
            }

            docRef.set(payload).await()
            val createdSnap = docRef.get().await()
            val profile = createdSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(
                id = uid,
                userId = uid,
                name = displayName,
                email = email,
                role = role,
                status = AccountStatus.ACTIVE.name,
                tradingCapital = capital,
                dailyGoal = goal,
                dailyMaxLoss = maxLoss,
                maxTradesPerDay = maxTrades
            )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyTrades(uid: String): Flow<List<TradeEntry>> = flow {
        val path = "trades"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TradeEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.tradeTimestampMillis }
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllTradesForAdmin(adminUid: String): Flow<List<TradeEntry>> = flow {
        val path = "trades"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TradeEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.tradeTimestampMillis }
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == adminUid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyDisciplineEntries(uid: String): Flow<List<DisciplineEntry>> = flow {
        val path = "discipline_entries"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DisciplineEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.periodLabel }
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllDisciplineEntriesForAdmin(adminUid: String): Flow<List<DisciplineEntry>> = flow {
        val path = "discipline_entries"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DisciplineEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.periodLabel }
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == adminUid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllUsersForAdmin(adminUid: String): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase(Locale.US) }
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == adminUid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeStudentInvitesForAdmin(adminUid: String): Flow<List<StudentInvite>> = flow {
        val path = "student_invites"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StudentInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase(Locale.US) }
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == adminUid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveTrade(
        existingTradeId: String?,
        ownerUid: String,
        ownerName: String,
        tradeDate: String,
        tradeTimestampMillis: Long,
        instrument: String,
        direction: String,
        timeframe: String,
        entryPrice: Double,
        exitPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        lotSize: Double,
        riskAmount: Double,
        profitLoss: Double,
        result: String,
        strategy: String,
        entryReason: String,
        exitReason: String,
        mistakes: String,
        lessonsLearned: String,
        notes: String,
        chartScreenshotBase64: String?
    ): Result<String> {
        var docPath = "trades"
        return try {
            requireUserId()
            val cleanInstrument = instrument.trim().uppercase(Locale.US).take(40)
            require(cleanInstrument.isNotBlank()) { "Trading instrument is required." }
            require(lotSize > 0.0) { "Lot size must be greater than 0." }
            require(entryPrice >= 0.0 && exitPrice >= 0.0 && stopLoss >= 0.0 && takeProfit >= 0.0 && riskAmount >= 0.0) {
                "Prices and risk amount cannot be negative."
            }

            val rr = TradingAnalytics.calculateRiskReward(entryPrice, stopLoss, takeProfit, riskAmount, profitLoss)
            val slUsed = stopLoss > 0.0
            val cleanUserName = ownerName.trim().ifBlank { "Trader" }.take(80)
            val cleanDate = tradeDate.trim().ifBlank { TradingAnalytics.todayIsoDate() }.take(20)
            val tsMillis = if (tradeTimestampMillis > 0L) tradeTimestampMillis else System.currentTimeMillis()
            val cleanScreenshot = chartScreenshotBase64?.takeIf { it.isNotBlank() && it.length <= 400_000 }

            if (existingTradeId.isNullOrBlank()) {
                val docRef = db.collection("trades").document()
                docPath = docRef.path
                val datePart = cleanDate.replace("-", "").take(8)
                val suffix = docRef.id.takeLast(4).uppercase(Locale.US)
                val autoTradeCode = "TBB-$datePart-$suffix".take(40)

                val payload = mutableMapOf<String, Any>(
                    "userId" to ownerUid,
                    "tradeCode" to autoTradeCode,
                    "userName" to cleanUserName,
                    "tradeDate" to cleanDate,
                    "tradeTimestampMillis" to tsMillis,
                    "instrument" to cleanInstrument,
                    "direction" to direction,
                    "timeframe" to timeframe,
                    "entryPrice" to entryPrice,
                    "exitPrice" to exitPrice,
                    "stopLoss" to stopLoss,
                    "takeProfit" to takeProfit,
                    "lotSize" to lotSize,
                    "riskAmount" to riskAmount,
                    "profitLoss" to profitLoss,
                    "result" to result,
                    "riskRewardRatio" to rr,
                    "stopLossUsed" to slUsed,
                    "strategy" to strategy.trim().take(100),
                    "entryReason" to entryReason.trim().take(1000),
                    "exitReason" to exitReason.trim().take(1000),
                    "mistakes" to mistakes.trim().take(1000),
                    "lessonsLearned" to lessonsLearned.trim().take(1000),
                    "notes" to notes.trim().take(1500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanScreenshot != null) {
                    payload["chartScreenshotBase64"] = cleanScreenshot
                }
                docRef.set(payload).await()
                Result.success(autoTradeCode)
            } else {
                val docRef = db.collection("trades").document(existingTradeId)
                docPath = docRef.path
                val updates = mutableMapOf<String, Any>(
                    "userName" to cleanUserName,
                    "tradeDate" to cleanDate,
                    "tradeTimestampMillis" to tsMillis,
                    "instrument" to cleanInstrument,
                    "direction" to direction,
                    "timeframe" to timeframe,
                    "entryPrice" to entryPrice,
                    "exitPrice" to exitPrice,
                    "stopLoss" to stopLoss,
                    "takeProfit" to takeProfit,
                    "lotSize" to lotSize,
                    "riskAmount" to riskAmount,
                    "profitLoss" to profitLoss,
                    "result" to result,
                    "riskRewardRatio" to rr,
                    "stopLossUsed" to slUsed,
                    "strategy" to strategy.trim().take(100),
                    "entryReason" to entryReason.trim().take(1000),
                    "exitReason" to exitReason.trim().take(1000),
                    "mistakes" to mistakes.trim().take(1000),
                    "lessonsLearned" to lessonsLearned.trim().take(1000),
                    "notes" to notes.trim().take(1500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanScreenshot != null) {
                    updates["chartScreenshotBase64"] = cleanScreenshot
                } else {
                    updates["chartScreenshotBase64"] = FieldValue.delete()
                }
                docRef.update(updates).await()
                Result.success("Updated")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTrade(tradeId: String): Result<Unit> {
        val docPath = "trades/$tradeId"
        return try {
            requireUserId()
            require(tradeId.isNotBlank()) { "Trade ID cannot be blank." }
            db.collection("trades").document(tradeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveDisciplineEntry(
        existingEntryId: String?,
        ownerUid: String,
        ownerName: String,
        entryType: String,
        periodLabel: String,
        moodTag: String,
        followedRules: Boolean,
        respectedStopLoss: Boolean,
        overtraded: Boolean,
        psychologyNotes: String,
        mistakesSummary: String,
        improvementsPlan: String,
        disciplineScore: Long
    ): Result<Unit> {
        var docPath = "discipline_entries"
        return try {
            requireUserId()
            val cleanPeriod = periodLabel.trim().ifBlank {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            }.take(40)
            val cleanMood = moodTag.trim().ifBlank { "Calm" }.take(40)
            val cleanScore = disciplineScore.coerceIn(1L, 10L)
            val cleanName = ownerName.trim().ifBlank { "Trader" }.take(80)

            if (existingEntryId.isNullOrBlank()) {
                val docRef = db.collection("discipline_entries").document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to ownerUid,
                    "userName" to cleanName,
                    "entryType" to entryType,
                    "periodLabel" to cleanPeriod,
                    "moodTag" to cleanMood,
                    "followedRules" to followedRules,
                    "respectedStopLoss" to respectedStopLoss,
                    "overtraded" to overtraded,
                    "psychologyNotes" to psychologyNotes.trim().take(1500),
                    "mistakesSummary" to mistakesSummary.trim().take(1500),
                    "improvementsPlan" to improvementsPlan.trim().take(1500),
                    "disciplineScore" to cleanScore,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val docRef = db.collection("discipline_entries").document(existingEntryId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "userName" to cleanName,
                        "entryType" to entryType,
                        "periodLabel" to cleanPeriod,
                        "moodTag" to cleanMood,
                        "followedRules" to followedRules,
                        "respectedStopLoss" to respectedStopLoss,
                        "overtraded" to overtraded,
                        "psychologyNotes" to psychologyNotes.trim().take(1500),
                        "mistakesSummary" to mistakesSummary.trim().take(1500),
                        "improvementsPlan" to improvementsPlan.trim().take(1500),
                        "disciplineScore" to cleanScore,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDisciplineEntry(entryId: String): Result<Unit> {
        val docPath = "discipline_entries/$entryId"
        return try {
            requireUserId()
            require(entryId.isNotBlank())
            db.collection("discipline_entries").document(entryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMyRiskTargets(
        uid: String,
        name: String,
        tradingCapital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long
    ): Result<Unit> {
        val docPath = "users/$uid"
        return try {
            val current = requireUserId()
            check(current == uid) { "Cannot update another user's risk targets from student mode." }
            db.document(docPath).update(
                mapOf(
                    "name" to name.trim().ifBlank { "Trader" }.take(80),
                    "tradingCapital" to tradingCapital.coerceAtLeast(0.0),
                    "dailyGoal" to dailyGoal.coerceAtLeast(0.0),
                    "dailyMaxLoss" to dailyMaxLoss.coerceAtLeast(0.0),
                    "maxTradesPerDay" to maxTradesPerDay.coerceIn(1L, 100L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateStudentAccount(
        studentUid: String,
        name: String,
        status: String,
        tradingCapital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        adminNotes: String
    ): Result<Unit> {
        val docPath = "users/$studentUid"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "name" to name.trim().ifBlank { "Student" }.take(80),
                "status" to status,
                "tradingCapital" to tradingCapital.coerceAtLeast(0.0),
                "dailyGoal" to dailyGoal.coerceAtLeast(0.0),
                "dailyMaxLoss" to dailyMaxLoss.coerceAtLeast(0.0),
                "maxTradesPerDay" to maxTradesPerDay.coerceIn(1L, 100L),
                "adminNotes" to adminNotes.trim().take(1000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.document(docPath).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminSaveStudentInvite(
        existingInviteId: String?,
        name: String,
        email: String,
        status: String,
        tradingCapital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        notes: String
    ): Result<Unit> {
        var docPath = "student_invites"
        return try {
            val adminUid = requireUserId()
            val cleanEmail = email.trim().lowercase(Locale.US).take(120)
            require(cleanEmail.contains("@") && cleanEmail.length >= 3) { "Valid student email is required." }
            val cleanName = name.trim().ifBlank { cleanEmail.substringBefore("@") }.take(80)

            if (existingInviteId.isNullOrBlank()) {
                val docRef = db.collection("student_invites").document()
                docPath = docRef.path
                val payload = mutableMapOf<String, Any>(
                    "email" to cleanEmail,
                    "name" to cleanName,
                    "status" to status,
                    "tradingCapital" to tradingCapital.coerceAtLeast(0.0),
                    "dailyGoal" to dailyGoal.coerceAtLeast(0.0),
                    "dailyMaxLoss" to dailyMaxLoss.coerceAtLeast(0.0),
                    "maxTradesPerDay" to maxTradesPerDay.coerceIn(1L, 100L),
                    "notes" to notes.trim().take(1000),
                    "createdByUid" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val docRef = db.collection("student_invites").document(existingInviteId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "email" to cleanEmail,
                        "name" to cleanName,
                        "status" to status,
                        "tradingCapital" to tradingCapital.coerceAtLeast(0.0),
                        "dailyGoal" to dailyGoal.coerceAtLeast(0.0),
                        "dailyMaxLoss" to dailyMaxLoss.coerceAtLeast(0.0),
                        "maxTradesPerDay" to maxTradesPerDay.coerceIn(1L, 100L),
                        "notes" to notes.trim().take(1000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteStudentInvite(inviteId: String): Result<Unit> {
        val docPath = "student_invites/$inviteId"
        return try {
            requireUserId()
            require(inviteId.isNotBlank())
            db.collection("student_invites").document(inviteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }
}
