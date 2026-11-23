package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.analytics.DashboardStats
import com.example.data.analytics.StudentPerformanceSummary
import com.example.data.analytics.TradingAnalytics
import com.example.data.firebase.TradingRepository
import com.example.data.model.DisciplineEntry
import com.example.data.model.StudentInvite
import com.example.data.model.TradeEntry
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TradingRepository,
    private val firebaseUser: FirebaseUser
) : ViewModel() {

    private val uid: String = firebaseUser.uid

    private val _profileState = MutableStateFlow<UiState<UserProfile>>(UiState.Loading)
    val profileState: StateFlow<UiState<UserProfile>> = _profileState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        bootstrapProfile()
    }

    fun bootstrapProfile() {
        viewModelScope.launch {
            _profileState.value = UiState.Loading
            val res = repository.syncOrCreateUserProfile(firebaseUser)
            res.onFailure { err ->
                _profileState.value = UiState.Error(err.localizedMessage ?: "Failed to load user profile")
            }
        }
        viewModelScope.launch {
            repository.observeUserProfile(uid)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val root = fsError ?: error
                    Log.w(TAG, "Profile stream error", root)
                    _profileState.value = UiState.Error(root.message ?: "Failed to load profile")
                }
                .collect { profile ->
                    if (profile != null) {
                        _profileState.value = UiState.Success(profile)
                    }
                }
        }
    }

    val myTradesState: StateFlow<UiState<List<TradeEntry>>> = repository.observeMyTrades(uid)
        .map<List<TradeEntry>, UiState<List<TradeEntry>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "My trades stream error", root)
            emit(UiState.Error(root.message ?: "Failed to observe trades"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val myDisciplineState: StateFlow<UiState<List<DisciplineEntry>>> = repository.observeMyDisciplineEntries(uid)
        .map<List<DisciplineEntry>, UiState<List<DisciplineEntry>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "My discipline stream error", root)
            emit(UiState.Error(root.message ?: "Failed to observe discipline entries"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val myStats: StateFlow<DashboardStats> = combine(myTradesState, profileState) { tradesUi, profUi ->
        val trades = (tradesUi as? UiState.Success)?.data.orEmpty()
        val prof = (profUi as? UiState.Success)?.data
        TradingAnalytics.computeStats(
            trades = trades,
            maxTradesPerDay = (prof?.maxTradesPerDay ?: 3L).toInt().coerceAtLeast(1),
            tradingCapital = prof?.tradingCapital ?: 100000.0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardStats()
    )

    // Admin-only streams: only subscribed when user is verified ADMIN
    private val isAdminFlow = profileState.map { (it as? UiState.Success)?.data?.roleEnum == UserRole.ADMIN }

    val adminUsersState: StateFlow<UiState<List<UserProfile>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf<UiState<List<UserProfile>>>(UiState.Success(emptyList()))
            else repository.observeAllUsersForAdmin(uid)
                .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(UiState.Error((fsError ?: error).message ?: "Failed to observe users"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val adminInvitesState: StateFlow<UiState<List<StudentInvite>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf<UiState<List<StudentInvite>>>(UiState.Success(emptyList()))
            else repository.observeStudentInvitesForAdmin(uid)
                .map<List<StudentInvite>, UiState<List<StudentInvite>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(UiState.Error((fsError ?: error).message ?: "Failed to observe invites"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val adminAllTradesState: StateFlow<UiState<List<TradeEntry>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf<UiState<List<TradeEntry>>>(UiState.Success(emptyList()))
            else repository.observeAllTradesForAdmin(uid)
                .map<List<TradeEntry>, UiState<List<TradeEntry>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(UiState.Error((fsError ?: error).message ?: "Failed to observe all trades"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val adminAllDisciplineState: StateFlow<UiState<List<DisciplineEntry>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf<UiState<List<DisciplineEntry>>>(UiState.Success(emptyList()))
            else repository.observeAllDisciplineEntriesForAdmin(uid)
                .map<List<DisciplineEntry>, UiState<List<DisciplineEntry>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(UiState.Error((fsError ?: error).message ?: "Failed to observe all discipline logs"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val studentPerformanceSummaries: StateFlow<List<StudentPerformanceSummary>> = combine(
        adminUsersState,
        adminAllTradesState,
        adminAllDisciplineState
    ) { usersUi, tradesUi, discUi ->
        val users = (usersUi as? UiState.Success)?.data.orEmpty()
            .filter { it.roleEnum != UserRole.ADMIN }
        val trades = (tradesUi as? UiState.Success)?.data.orEmpty()
        val disc = (discUi as? UiState.Success)?.data.orEmpty()
        TradingAnalytics.computeStudentSummaries(users, trades, disc)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    fun saveTrade(
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
        chartScreenshotBase64: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveTrade(
                existingTradeId = existingTradeId,
                ownerUid = ownerUid,
                ownerName = ownerName,
                tradeDate = tradeDate,
                tradeTimestampMillis = tradeTimestampMillis,
                instrument = instrument,
                direction = direction,
                timeframe = timeframe,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                lotSize = lotSize,
                riskAmount = riskAmount,
                profitLoss = profitLoss,
                result = result,
                strategy = strategy,
                entryReason = entryReason,
                exitReason = exitReason,
                mistakes = mistakes,
                lessonsLearned = lessonsLearned,
                notes = notes,
                chartScreenshotBase64 = chartScreenshotBase64
            )
            _isBusy.value = false
            res.onSuccess { code ->
                _events.tryEmit(if (existingTradeId == null) "Trade $code saved" else "Trade updated")
                onDone()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to save trade")
            }
        }
    }

    fun deleteTrade(tradeId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteTrade(tradeId)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Trade deleted")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to delete trade")
            }
        }
    }

    fun saveDisciplineEntry(
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
        disciplineScore: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveDisciplineEntry(
                existingEntryId = existingEntryId,
                ownerUid = ownerUid,
                ownerName = ownerName,
                entryType = entryType,
                periodLabel = periodLabel,
                moodTag = moodTag,
                followedRules = followedRules,
                respectedStopLoss = respectedStopLoss,
                overtraded = overtraded,
                psychologyNotes = psychologyNotes,
                mistakesSummary = mistakesSummary,
                improvementsPlan = improvementsPlan,
                disciplineScore = disciplineScore
            )
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Discipline entry saved")
                onDone()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to save discipline entry")
            }
        }
    }

    fun deleteDisciplineEntry(entryId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteDisciplineEntry(entryId)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Entry deleted")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to delete entry")
            }
        }
    }

    fun updateMyRiskTargets(
        name: String,
        tradingCapital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateMyRiskTargets(
                uid = uid,
                name = name,
                tradingCapital = tradingCapital,
                dailyGoal = dailyGoal,
                dailyMaxLoss = dailyMaxLoss,
                maxTradesPerDay = maxTradesPerDay
            )
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Trading targets updated")
                onDone()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to update targets")
            }
        }
    }

    fun adminUpdateStudentAccount(
        studentUid: String,
        name: String,
        status: String,
        tradingCapital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        adminNotes: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminUpdateStudentAccount(
                studentUid = studentUid,
                name = name,
                status = status,
                tradingCapital = tradingCapital,
                dailyGoal = dailyGoal,
                dailyMaxLoss = dailyMaxLoss,
                maxTradesPerDay = maxTradesPerDay,
                adminNotes = adminNotes
            )
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Student account updated")
                onDone()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to update student account")
            }
        }
    }

    fun adminSaveStudentInvite(
        existingInviteId: String?,
        name: String,
        email: String,
        status: String,
        tradingCapital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        notes: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminSaveStudentInvite(
                existingInviteId = existingInviteId,
                name = name,
                email = email,
                status = status,
                tradingCapital = tradingCapital,
                dailyGoal = dailyGoal,
                dailyMaxLoss = dailyMaxLoss,
                maxTradesPerDay = maxTradesPerDay,
                notes = notes
            )
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Student invite saved")
                onDone()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to save student invite")
            }
        }
    }

    fun adminDeleteStudentInvite(inviteId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminDeleteStudentInvite(inviteId)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit("Invite removed")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to remove invite")
            }
        }
    }

    fun emitNotice(message: String) {
        _events.tryEmit(message)
    }

    private companion object {
        const val TAG = "MainVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
