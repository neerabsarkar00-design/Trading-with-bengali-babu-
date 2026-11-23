package com.example.data.analytics

import com.example.data.model.DisciplineEntry
import com.example.data.model.TradeEntry
import com.example.data.model.TradeResult
import com.example.data.model.Timeframe
import com.example.data.model.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class DayPnlPoint(
    val date: String,
    val label: String,
    val pnl: Double,
    val tradeCount: Int
)

data class MonthPnlPoint(
    val monthKey: String,
    val label: String,
    val pnl: Double,
    val tradeCount: Int
)

data class EquityPoint(
    val index: Int,
    val label: String,
    val cumulativePnl: Double,
    val tradeCode: String
)

data class BreakdownStat(
    val name: String,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val netPnl: Double
)

data class MistakeStat(
    val mistake: String,
    val count: Int
)

data class DashboardStats(
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val breakEvenTrades: Int = 0,
    val winRatePercent: Double = 0.0,
    val lossRatePercent: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalLoss: Double = 0.0,
    val netProfitLoss: Double = 0.0,
    val averageWin: Double = 0.0,
    val averageLoss: Double = 0.0,
    val profitFactor: Double = 0.0,
    val profitFactorInfinite: Boolean = false,
    val averageRiskReward: Double = 0.0,
    val bestTradingDay: DayPnlPoint? = null,
    val worstTradingDay: DayPnlPoint? = null,
    val currentStreakCount: Int = 0,
    val currentStreakType: String = "NONE", // "WIN", "LOSS", "EVEN", "NONE"
    val stopLossDisciplinePercent: Double = 0.0,
    val overtradingDaysCount: Int = 0,
    val todayTradeCount: Int = 0,
    val todayNetPnl: Double = 0.0,
    val averageRiskPercentOfCapital: Double = 0.0,
    val dailySeries: List<DayPnlPoint> = emptyList(),
    val monthlySeries: List<MonthPnlPoint> = emptyList(),
    val equityCurve: List<EquityPoint> = emptyList(),
    val instrumentBreakdown: List<BreakdownStat> = emptyList(),
    val timeframeBreakdown: List<BreakdownStat> = emptyList(),
    val mistakeBreakdown: List<MistakeStat> = emptyList()
)

data class StudentPerformanceSummary(
    val profile: UserProfile,
    val stats: DashboardStats,
    val avgDisciplineScore: Double,
    val disciplineEntriesCount: Int
)

object TradingAnalytics {

    fun todayIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun calculateRiskReward(
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        riskAmount: Double,
        profitLoss: Double
    ): Double {
        val priceRisk = abs(entryPrice - stopLoss)
        val priceReward = abs(takeProfit - entryPrice)
        if (priceRisk > 0.0 && priceReward > 0.0) {
            return (priceReward / priceRisk).round2()
        }
        if (riskAmount > 0.0 && profitLoss != 0.0) {
            return (abs(profitLoss) / riskAmount).round2()
        }
        return 0.0
    }

    fun computeStats(
        trades: List<TradeEntry>,
        maxTradesPerDay: Int = 3,
        tradingCapital: Double = 100000.0
    ): DashboardStats {
        if (trades.isEmpty()) return DashboardStats()

        val sortedChronological = trades.sortedWith(
            compareBy<TradeEntry> { it.tradeDate }.thenBy { it.tradeTimestampMillis }
        )

        var wins = 0
        var losses = 0
        var breakEvens = 0
        var totalProfit = 0.0
        var totalLossAbs = 0.0
        var netPnl = 0.0
        var rrSum = 0.0
        var rrCount = 0
        var slUsedCount = 0
        var riskSum = 0.0

        sortedChronological.forEach { t ->
            netPnl += t.profitLoss
            when (t.resultEnum) {
                TradeResult.WIN -> {
                    wins++
                    if (t.profitLoss > 0) totalProfit += t.profitLoss
                }
                TradeResult.LOSS -> {
                    losses++
                    if (t.profitLoss < 0) totalLossAbs += abs(t.profitLoss)
                }
                TradeResult.BREAK_EVEN -> {
                    breakEvens++
                    if (t.profitLoss > 0) totalProfit += t.profitLoss
                    else if (t.profitLoss < 0) totalLossAbs += abs(t.profitLoss)
                }
            }
            if (t.riskRewardRatio > 0.0) {
                rrSum += t.riskRewardRatio
                rrCount++
            }
            if (t.stopLossUsed || t.stopLoss > 0.0) slUsedCount++
            riskSum += t.riskAmount
        }

        val total = sortedChronological.size
        val winRate = (wins.toDouble() / total) * 100.0
        val lossRate = (losses.toDouble() / total) * 100.0
        val avgWin = if (wins > 0) totalProfit / wins else 0.0
        val avgLoss = if (losses > 0) totalLossAbs / losses else 0.0
        val pfInfinite = totalLossAbs == 0.0 && totalProfit > 0.0
        val profitFactor = if (totalLossAbs > 0.0) totalProfit / totalLossAbs else 0.0
        val avgRr = if (rrCount > 0) rrSum / rrCount else 0.0
        val slDiscipline = (slUsedCount.toDouble() / total) * 100.0
        val avgRiskPct = if (tradingCapital > 0.0 && total > 0) ((riskSum / total) / tradingCapital) * 100.0 else 0.0

        // Current Streak (from most recent trade backwards)
        val latestFirst = sortedChronological.asReversed()
        val firstResult = latestFirst.first().resultEnum
        var streakCount = 0
        for (t in latestFirst) {
            if (t.resultEnum == firstResult) streakCount++ else break
        }
        val streakType = when (firstResult) {
            TradeResult.WIN -> "WIN"
            TradeResult.LOSS -> "LOSS"
            TradeResult.BREAK_EVEN -> "EVEN"
        }

        // Daily series
        val dailyMap = sortedChronological.groupBy { it.tradeDate.ifBlank { "Unknown" } }
        val dailySeries = dailyMap.entries
            .sortedBy { it.key }
            .map { (date, dayTrades) ->
                val shortLabel = if (date.length >= 10) date.substring(5, 10) else date
                DayPnlPoint(
                    date = date,
                    label = shortLabel,
                    pnl = dayTrades.sumOf { it.profitLoss },
                    tradeCount = dayTrades.size
                )
            }

        val bestDay = dailySeries.maxByOrNull { it.pnl }
        val worstDay = dailySeries.minByOrNull { it.pnl }
        val overtradingDays = dailySeries.count { it.tradeCount > maxTradesPerDay }

        val today = todayIsoDate()
        val todayTrades = sortedChronological.filter { it.tradeDate == today }

        // Monthly series
        val monthlySeries = sortedChronological
            .groupBy { if (it.tradeDate.length >= 7) it.tradeDate.substring(0, 7) else "Unknown" }
            .entries
            .sortedBy { it.key }
            .map { (month, mTrades) ->
                MonthPnlPoint(
                    monthKey = month,
                    label = formatMonthLabel(month),
                    pnl = mTrades.sumOf { it.profitLoss },
                    tradeCount = mTrades.size
                )
            }

        // Equity curve
        var running = 0.0
        val equityCurve = sortedChronological.mapIndexed { idx, t ->
            running += t.profitLoss
            EquityPoint(
                index = idx + 1,
                label = if (t.tradeDate.length >= 10) t.tradeDate.substring(5, 10) else "#${idx + 1}",
                cumulativePnl = running,
                tradeCode = t.tradeCode
            )
        }

        // Instrument breakdown
        val instrumentBreakdown = sortedChronological
            .groupBy { it.instrument.uppercase(Locale.US).ifBlank { "OTHER" } }
            .entries
            .map { (sym, list) ->
                val w = list.count { it.resultEnum == TradeResult.WIN }
                val l = list.count { it.resultEnum == TradeResult.LOSS }
                BreakdownStat(
                    name = sym,
                    trades = list.size,
                    wins = w,
                    losses = l,
                    winRate = if (list.isNotEmpty()) (w.toDouble() / list.size) * 100.0 else 0.0,
                    netPnl = list.sumOf { it.profitLoss }
                )
            }
            .sortedByDescending { it.netPnl }

        // Timeframe breakdown
        val timeframeOrder = Timeframe.entries.map { it.name }
        val timeframeBreakdown = sortedChronological
            .groupBy { it.timeframeEnum.name }
            .entries
            .sortedBy { timeframeOrder.indexOf(it.key) }
            .map { (tf, list) ->
                val w = list.count { it.resultEnum == TradeResult.WIN }
                val l = list.count { it.resultEnum == TradeResult.LOSS }
                BreakdownStat(
                    name = tf,
                    trades = list.size,
                    wins = w,
                    losses = l,
                    winRate = if (list.isNotEmpty()) (w.toDouble() / list.size) * 100.0 else 0.0,
                    netPnl = list.sumOf { it.profitLoss }
                )
            }

        // Mistake frequency
        val mistakeBreakdown = sortedChronological
            .asSequence()
            .flatMap { it.mistakes.split(",", ";", "\n").asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { MistakeStat(it.key, it.value) }

        return DashboardStats(
            totalTrades = total,
            winningTrades = wins,
            losingTrades = losses,
            breakEvenTrades = breakEvens,
            winRatePercent = winRate.round2(),
            lossRatePercent = lossRate.round2(),
            totalProfit = totalProfit.round2(),
            totalLoss = totalLossAbs.round2(),
            netProfitLoss = netPnl.round2(),
            averageWin = avgWin.round2(),
            averageLoss = avgLoss.round2(),
            profitFactor = profitFactor.round2(),
            profitFactorInfinite = pfInfinite,
            averageRiskReward = avgRr.round2(),
            bestTradingDay = bestDay,
            worstTradingDay = worstDay,
            currentStreakCount = streakCount,
            currentStreakType = streakType,
            stopLossDisciplinePercent = slDiscipline.round2(),
            overtradingDaysCount = overtradingDays,
            todayTradeCount = todayTrades.size,
            todayNetPnl = todayTrades.sumOf { it.profitLoss }.round2(),
            averageRiskPercentOfCapital = avgRiskPct.round2(),
            dailySeries = dailySeries,
            monthlySeries = monthlySeries,
            equityCurve = equityCurve,
            instrumentBreakdown = instrumentBreakdown,
            timeframeBreakdown = timeframeBreakdown,
            mistakeBreakdown = mistakeBreakdown
        )
    }

    fun computeStudentSummaries(
        students: List<UserProfile>,
        allTrades: List<TradeEntry>,
        allDiscipline: List<DisciplineEntry>
    ): List<StudentPerformanceSummary> {
        val tradesByUid = allTrades.groupBy { it.userId }
        val discByUid = allDiscipline.groupBy { it.userId }
        return students.map { student ->
            val stTrades = tradesByUid[student.userId].orEmpty()
            val stDisc = discByUid[student.userId].orEmpty()
            val stats = computeStats(
                trades = stTrades,
                maxTradesPerDay = student.maxTradesPerDay.toInt().coerceAtLeast(1),
                tradingCapital = student.tradingCapital
            )
            val avgScore = if (stDisc.isNotEmpty()) {
                stDisc.map { it.disciplineScore.toDouble() }.average().round2()
            } else {
                0.0
            }
            StudentPerformanceSummary(
                profile = student,
                stats = stats,
                avgDisciplineScore = avgScore,
                disciplineEntriesCount = stDisc.size
            )
        }.sortedByDescending { it.stats.netProfitLoss }
    }

    private fun formatMonthLabel(monthKey: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM", Locale.US)
            val fmt = SimpleDateFormat("MMM yy", Locale.US)
            val date = parser.parse(monthKey)
            if (date != null) fmt.format(date) else monthKey
        } catch (_: Exception) {
            monthKey
        }
    }

    private fun Double.round2(): Double = kotlin.math.round(this * 100.0) / 100.0
}

fun formatCurrency(amount: Double): String {
    val sign = if (amount < 0) "-" else ""
    val absVal = abs(amount)
    return if (absVal >= 1000000.0) {
        String.format(Locale.US, "%s₹%,.2f", sign, absVal)
    } else {
        String.format(Locale.US, "%s₹%,.2f", sign, absVal)
    }
}

fun formatSignedCurrency(amount: Double): String {
    val sign = when {
        amount > 0 -> "+"
        amount < 0 -> "-"
        else -> ""
    }
    return String.format(Locale.US, "%s₹%,.2f", sign, abs(amount))
}
