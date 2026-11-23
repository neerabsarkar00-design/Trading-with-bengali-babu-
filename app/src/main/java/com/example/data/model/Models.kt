package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class UserRole {
    ADMIN,
    STUDENT;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: STUDENT
    }
}

enum class AccountStatus {
    ACTIVE,
    SUSPENDED;

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class InviteStatus {
    INVITED,
    ACTIVE,
    SUSPENDED;

    companion object {
        fun fromRaw(raw: String?): InviteStatus =
            entries.firstOrNull { it.name == raw } ?: INVITED
    }
}

enum class TradeDirection(val label: String) {
    BUY("Buy"),
    SELL("Sell");

    companion object {
        fun fromRaw(raw: String?): TradeDirection =
            entries.firstOrNull { it.name == raw } ?: BUY
    }
}

enum class Timeframe(val label: String) {
    M1("M1"),
    M5("M5"),
    M15("M15"),
    M30("M30"),
    H1("H1"),
    H4("H4"),
    D1("D1");

    companion object {
        fun fromRaw(raw: String?): Timeframe =
            entries.firstOrNull { it.name == raw } ?: M15
    }
}

enum class TradeResult(val label: String) {
    WIN("Win"),
    LOSS("Loss"),
    BREAK_EVEN("Break Even");

    companion object {
        fun fromRaw(raw: String?): TradeResult =
            entries.firstOrNull { it.name == raw } ?: BREAK_EVEN
    }
}

enum class DisciplineEntryType(val label: String) {
    DAILY_PSYCHOLOGY("Daily Psychology"),
    WEEKLY_REVIEW("Weekly Self-Review"),
    MONTHLY_REVIEW("Monthly Trading Review");

    companion object {
        fun fromRaw(raw: String?): DisciplineEntryType =
            entries.firstOrNull { it.name == raw } ?: DAILY_PSYCHOLOGY
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = UserRole.STUDENT.name,
    val status: String = AccountStatus.ACTIVE.name,
    val tradingCapital: Double = 100000.0,
    val dailyGoal: Double = 2500.0,
    val dailyMaxLoss: Double = 1500.0,
    val maxTradesPerDay: Long = 3L,
    val photoUrl: String? = null,
    val adminNotes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude val roleEnum: UserRole get() = UserRole.fromRaw(role)
    @get:Exclude val statusEnum: AccountStatus get() = AccountStatus.fromRaw(status)
    @get:Exclude val isAdminUser: Boolean get() = roleEnum == UserRole.ADMIN
    @get:Exclude val isSuspendedUser: Boolean get() = statusEnum == AccountStatus.SUSPENDED
}

@Keep
@IgnoreExtraProperties
data class StudentInvite(
    @DocumentId val id: String = "",
    val email: String = "",
    val name: String = "",
    val status: String = InviteStatus.INVITED.name,
    val tradingCapital: Double = 100000.0,
    val dailyGoal: Double = 2500.0,
    val dailyMaxLoss: Double = 1500.0,
    val maxTradesPerDay: Long = 3L,
    val notes: String? = null,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude val statusEnum: InviteStatus get() = InviteStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class TradeEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tradeCode: String = "",
    val userName: String = "",
    val tradeDate: String = "",
    val tradeTimestampMillis: Long = 0L,
    val instrument: String = "",
    val direction: String = TradeDirection.BUY.name,
    val timeframe: String = Timeframe.M15.name,
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val lotSize: Double = 1.0,
    val riskAmount: Double = 0.0,
    val profitLoss: Double = 0.0,
    val result: String = TradeResult.BREAK_EVEN.name,
    val riskRewardRatio: Double = 0.0,
    val stopLossUsed: Boolean = true,
    val strategy: String = "",
    val entryReason: String = "",
    val exitReason: String = "",
    val mistakes: String = "",
    val lessonsLearned: String = "",
    val notes: String = "",
    val chartScreenshotBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude val directionEnum: TradeDirection get() = TradeDirection.fromRaw(direction)
    @get:Exclude val timeframeEnum: Timeframe get() = Timeframe.fromRaw(timeframe)
    @get:Exclude val resultEnum: TradeResult get() = TradeResult.fromRaw(result)
}

@Keep
@IgnoreExtraProperties
data class DisciplineEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val entryType: String = DisciplineEntryType.DAILY_PSYCHOLOGY.name,
    val periodLabel: String = "",
    val moodTag: String = "Calm",
    val followedRules: Boolean = true,
    val respectedStopLoss: Boolean = true,
    val overtraded: Boolean = false,
    val psychologyNotes: String = "",
    val mistakesSummary: String = "",
    val improvementsPlan: String = "",
    val disciplineScore: Long = 8L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude val entryTypeEnum: DisciplineEntryType get() = DisciplineEntryType.fromRaw(entryType)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
