package com.example

import com.example.data.analytics.TradingAnalytics
import com.example.data.model.TradeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingAnalyticsTest {

    @Test
    fun computeStats_calculatesExactMetricsFromSavedTrades() {
        val trades = listOf(
            TradeEntry(
                id = "1",
                tradeCode = "TBB-1",
                tradeDate = "2026-11-20",
                tradeTimestampMillis = 1000L,
                instrument = "NIFTY",
                direction = "BUY",
                timeframe = "M15",
                entryPrice = 24000.0,
                exitPrice = 24100.0,
                stopLoss = 23950.0,
                takeProfit = 24100.0,
                lotSize = 50.0,
                riskAmount = 2500.0,
                profitLoss = 5000.0,
                result = "WIN",
                riskRewardRatio = 2.0,
                stopLossUsed = true,
                mistakes = "FOMO"
            ),
            TradeEntry(
                id = "2",
                tradeCode = "TBB-2",
                tradeDate = "2026-11-20",
                tradeTimestampMillis = 2000L,
                instrument = "BANKNIFTY",
                direction = "SELL",
                timeframe = "M5",
                entryPrice = 51000.0,
                exitPrice = 51100.0,
                stopLoss = 51100.0,
                takeProfit = 50800.0,
                lotSize = 15.0,
                riskAmount = 1500.0,
                profitLoss = -1500.0,
                result = "LOSS",
                riskRewardRatio = 2.0,
                stopLossUsed = true,
                mistakes = "Early Exit, FOMO"
            ),
            TradeEntry(
                id = "3",
                tradeCode = "TBB-3",
                tradeDate = "2026-11-21",
                tradeTimestampMillis = 3000L,
                instrument = "NIFTY",
                direction = "BUY",
                timeframe = "M15",
                entryPrice = 24200.0,
                exitPrice = 24200.0,
                stopLoss = 0.0,
                takeProfit = 0.0,
                lotSize = 50.0,
                riskAmount = 0.0,
                profitLoss = 0.0,
                result = "BREAK_EVEN",
                riskRewardRatio = 0.0,
                stopLossUsed = false
            ),
            TradeEntry(
                id = "4",
                tradeCode = "TBB-4",
                tradeDate = "2026-11-22",
                tradeTimestampMillis = 4000L,
                instrument = "XAUUSD",
                direction = "BUY",
                timeframe = "H1",
                entryPrice = 2600.0,
                exitPrice = 2630.0,
                stopLoss = 2590.0,
                takeProfit = 2630.0,
                lotSize = 1.0,
                riskAmount = 1000.0,
                profitLoss = 3000.0,
                result = "WIN",
                riskRewardRatio = 3.0,
                stopLossUsed = true
            )
        )

        val stats = TradingAnalytics.computeStats(trades, maxTradesPerDay = 1, tradingCapital = 100000.0)

        assertEquals(4, stats.totalTrades)
        assertEquals(2, stats.winningTrades)
        assertEquals(1, stats.losingTrades)
        assertEquals(1, stats.breakEvenTrades)
        assertEquals(50.0, stats.winRatePercent, 0.01)
        assertEquals(25.0, stats.lossRatePercent, 0.01)
        assertEquals(8000.0, stats.totalProfit, 0.01)
        assertEquals(1500.0, stats.totalLoss, 0.01)
        assertEquals(6500.0, stats.netProfitLoss, 0.01)
        assertEquals(4000.0, stats.averageWin, 0.01)
        assertEquals(1500.0, stats.averageLoss, 0.01)
        assertFalse(stats.profitFactorInfinite)
        assertEquals(5.33, stats.profitFactor, 0.01)
        assertEquals(2.33, stats.averageRiskReward, 0.01)
        assertEquals("2026-11-20", stats.bestTradingDay?.date)
        assertEquals(3500.0, stats.bestTradingDay?.pnl ?: 0.0, 0.01)
        assertEquals("2026-11-21", stats.worstTradingDay?.date)
        assertEquals(1, stats.currentStreakCount)
        assertEquals("WIN", stats.currentStreakType)
        assertEquals(75.0, stats.stopLossDisciplinePercent, 0.01)
        assertEquals(1, stats.overtradingDaysCount)
        assertEquals("FOMO", stats.mistakeBreakdown.first().mistake)
        assertEquals(2, stats.mistakeBreakdown.first().count)
        assertTrue(stats.instrumentBreakdown.any { it.name == "NIFTY" && it.netPnl == 5000.0 })
        assertTrue(stats.timeframeBreakdown.any { it.name == "M15" && it.trades == 2 })
    }
}
