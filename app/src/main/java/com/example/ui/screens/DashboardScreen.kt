package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.analytics.DashboardStats
import com.example.data.analytics.formatCurrency
import com.example.data.analytics.formatSignedCurrency
import com.example.data.model.UserProfile
import com.example.ui.components.DailyPnlBarChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.EquityCurveChart
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.InstrumentPerformanceChart
import com.example.ui.components.MonthlyPerformanceChart
import com.example.ui.components.SectionTitle
import com.example.ui.components.TimeframePerformanceChart
import com.example.ui.components.WinLossPieChart
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@Composable
fun DashboardScreen(
    profile: UserProfile,
    stats: DashboardStats,
    onAddTradeClick: () -> Unit,
    showAddFab: Boolean = true,
    headerBannerTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    val isDark = colors.isDark

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroNetPnlCard(
                    profile = profile,
                    stats = stats,
                    customLabel = headerBannerTitle
                )
            }

            if (stats.totalTrades == 0) {
                item {
                    EmptyStateCard(
                        title = "No Trades Recorded Yet",
                        description = "All dashboard KPIs and charts calculate automatically from saved trades. Tap + Log Trade to record your first setup.",
                        icon = Icons.AutoMirrored.Filled.ShowChart
                    )
                }
            }

            item {
                SectionTitle(
                    title = "Performance Metrics",
                    subtitle = "100% calculated from ${stats.totalTrades} live database records",
                    accentColor = colors.gold
                )
            }

            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 700.dp) 4 else 2
                    val kpis = buildKpiItems(stats, colors)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        kpis.chunked(columns).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { item ->
                                    GradientKpiCard(
                                        title = item.title,
                                        value = item.value,
                                        subtitle = item.subtitle,
                                        icon = item.icon,
                                        gradientColors = if (isDark) item.darkGrad else item.lightGrad,
                                        accentColor = item.accent,
                                        valueColor = item.valueColor ?: MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    title = "Interactive Trading Charts",
                    subtitle = "Tap bars or curve points to inspect exact figures",
                    accentColor = colors.electricBlue
                )
            }

            item {
                EquityCurveChart(points = stats.equityCurve)
            }

            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 720.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DailyPnlBarChart(
                                points = stats.dailySeries,
                                modifier = Modifier.weight(1f)
                            )
                            WinLossPieChart(
                                wins = stats.winningTrades,
                                losses = stats.losingTrades,
                                breakEven = stats.breakEvenTrades,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DailyPnlBarChart(points = stats.dailySeries)
                            WinLossPieChart(
                                wins = stats.winningTrades,
                                losses = stats.losingTrades,
                                breakEven = stats.breakEvenTrades
                            )
                        }
                    }
                }
            }

            item {
                MonthlyPerformanceChart(points = stats.monthlySeries)
            }

            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 720.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InstrumentPerformanceChart(
                                items = stats.instrumentBreakdown,
                                modifier = Modifier.weight(1f)
                            )
                            TimeframePerformanceChart(
                                items = stats.timeframeBreakdown,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InstrumentPerformanceChart(items = stats.instrumentBreakdown)
                            TimeframePerformanceChart(items = stats.timeframeBreakdown)
                        }
                    }
                }
            }
        }

        if (showAddFab) {
            ExtendedFloatingActionButton(
                onClick = onAddTradeClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log Trade") },
                containerColor = colors.gold,
                contentColor = Color(0xFF071022),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("dashboard_add_trade_fab")
            )
        }
    }
}

@Composable
private fun HeroNetPnlCard(
    profile: UserProfile,
    stats: DashboardStats,
    customLabel: String?
) {
    val colors = LocalTradingColors.current
    val isDark = colors.isDark
    val pnlColor = when {
        stats.netProfitLoss > 0 -> colors.profit
        stats.netProfitLoss < 0 -> colors.loss
        else -> colors.gold
    }
    val heroGrad = if (isDark) {
        listOf(Color(0xFF0E224A), Color(0xFF1A1E4E), Color(0xFF0F2A3E))
    } else {
        listOf(Color(0xFFFFFFFF), Color(0xFFEBF3FF), Color(0xFFFEF7E5))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.gold.copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(heroGrad))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = customLabel ?: "Net Profit / Loss",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.gold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.electricBlue.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Capital ${formatCurrency(profile.tradingCapital)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = colors.electricBlue
                        )
                    }
                }

                Text(
                    text = formatSignedCurrency(stats.netProfitLoss),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = pnlColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniPillStat(
                        label = "Today P&L",
                        value = formatSignedCurrency(stats.todayNetPnl),
                        color = if (stats.todayNetPnl >= 0) colors.profit else colors.loss,
                        modifier = Modifier.weight(1f)
                    )
                    MiniPillStat(
                        label = "Today Trades",
                        value = "${stats.todayTradeCount} / ${profile.maxTradesPerDay}",
                        color = if (stats.todayTradeCount > profile.maxTradesPerDay) colors.loss else colors.cyan,
                        modifier = Modifier.weight(1f)
                    )
                    MiniPillStat(
                        label = "Streak",
                        value = when (stats.currentStreakType) {
                            "WIN" -> "${stats.currentStreakCount}W"
                            "LOSS" -> "${stats.currentStreakCount}L"
                            "EVEN" -> "${stats.currentStreakCount}BE"
                            else -> "0"
                        },
                        color = when (stats.currentStreakType) {
                            "WIN" -> colors.profit
                            "LOSS" -> colors.loss
                            else -> colors.gold
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPillStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.subtleSurface.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

private data class KpiCardSpec(
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val darkGrad: List<Color>,
    val lightGrad: List<Color>,
    val accent: Color,
    val valueColor: Color? = null
)

private fun buildKpiItems(
    stats: DashboardStats,
    colors: com.example.ui.theme.TradingColors
): List<KpiCardSpec> {
    val pfText = if (stats.profitFactorInfinite) "∞" else String.format(Locale.US, "%.2f", stats.profitFactor)
    val bestText = stats.bestTradingDay?.let { formatSignedCurrency(it.pnl) } ?: "—"
    val bestSub = stats.bestTradingDay?.date ?: "No days yet"
    val worstText = stats.worstTradingDay?.let { formatSignedCurrency(it.pnl) } ?: "—"
    val worstSub = stats.worstTradingDay?.date ?: "No days yet"
    val streakLabel = when (stats.currentStreakType) {
        "WIN" -> "${stats.currentStreakCount} Win Streak"
        "LOSS" -> "${stats.currentStreakCount} Loss Streak"
        "EVEN" -> "${stats.currentStreakCount} Break Even"
        else -> "No Streak"
    }

    return listOf(
        KpiCardSpec(
            title = "Total Trades",
            value = stats.totalTrades.toString(),
            subtitle = "${stats.winningTrades}W • ${stats.losingTrades}L • ${stats.breakEvenTrades}BE",
            icon = Icons.Default.AutoGraph,
            darkGrad = listOf(Color(0xFF102448), Color(0xFF17315E)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFEFF6FF)),
            accent = colors.electricBlue
        ),
        KpiCardSpec(
            title = "Win Rate / Loss Rate",
            value = String.format(Locale.US, "%.1f%%", stats.winRatePercent),
            subtitle = String.format(Locale.US, "Loss Rate: %.1f%%", stats.lossRatePercent),
            icon = Icons.Default.Percent,
            darkGrad = listOf(Color(0xFF0E2B2A), Color(0xFF123C39)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFECFDF5)),
            accent = colors.profit,
            valueColor = colors.profit
        ),
        KpiCardSpec(
            title = "Total Profit",
            value = formatCurrency(stats.totalProfit),
            subtitle = "${stats.winningTrades} winning trades",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            darkGrad = listOf(Color(0xFF092B24), Color(0xFF0D3D31)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFD1FAE5)),
            accent = colors.profit,
            valueColor = colors.profit
        ),
        KpiCardSpec(
            title = "Total Loss",
            value = formatCurrency(stats.totalLoss),
            subtitle = "${stats.losingTrades} losing trades",
            icon = Icons.AutoMirrored.Filled.TrendingDown,
            darkGrad = listOf(Color(0xFF331424), Color(0xFF471A2F)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFFFE4E6)),
            accent = colors.loss,
            valueColor = colors.loss
        ),
        KpiCardSpec(
            title = "Average Win",
            value = formatCurrency(stats.averageWin),
            subtitle = "Mean winning trade",
            icon = Icons.Default.CheckCircle,
            darkGrad = listOf(Color(0xFF0D2B38), Color(0xFF123B4B)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFE0F2FE)),
            accent = colors.teal,
            valueColor = colors.teal
        ),
        KpiCardSpec(
            title = "Average Loss",
            value = formatCurrency(stats.averageLoss),
            subtitle = "Mean losing trade",
            icon = Icons.Default.Warning,
            darkGrad = listOf(Color(0xFF2D192B), Color(0xFF3F1E37)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFFCE7F3)),
            accent = colors.loss,
            valueColor = colors.loss
        ),
        KpiCardSpec(
            title = "Profit Factor",
            value = pfText,
            subtitle = "Gross Profit / Gross Loss",
            icon = Icons.Default.AccountBalanceWallet,
            darkGrad = listOf(Color(0xFF221A4A), Color(0xFF302266)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFF3E8FF)),
            accent = colors.purple,
            valueColor = colors.purple
        ),
        KpiCardSpec(
            title = "Avg Risk : Reward",
            value = String.format(Locale.US, "1 : %.2f", stats.averageRiskReward),
            subtitle = String.format(Locale.US, "SL Discipline %.0f%%", stats.stopLossDisciplinePercent),
            icon = Icons.Default.Balance,
            darkGrad = listOf(Color(0xFF2B210B), Color(0xFF3F300F)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFFEF3C7)),
            accent = colors.gold,
            valueColor = colors.gold
        ),
        KpiCardSpec(
            title = "Best Trading Day",
            value = bestText,
            subtitle = bestSub,
            icon = Icons.Default.EmojiEvents,
            darkGrad = listOf(Color(0xFF0C2B27), Color(0xFF113F39)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFECFDF5)),
            accent = colors.profit,
            valueColor = colors.profit
        ),
        KpiCardSpec(
            title = "Worst Trading Day",
            value = worstText,
            subtitle = worstSub,
            icon = Icons.Default.CalendarMonth,
            darkGrad = listOf(Color(0xFF311622), Color(0xFF441D2D)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFFFE4E6)),
            accent = colors.loss,
            valueColor = colors.loss
        ),
        KpiCardSpec(
            title = "Current Streak",
            value = streakLabel,
            subtitle = "Consecutive outcomes",
            icon = Icons.Default.LocalFireDepartment,
            darkGrad = listOf(Color(0xFF192246), Color(0xFF242F5F)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFEDE9FE)),
            accent = colors.gold
        ),
        KpiCardSpec(
            title = "Break Even Trades",
            value = stats.breakEvenTrades.toString(),
            subtitle = "${stats.overtradingDaysCount} overtrading days",
            icon = Icons.Default.Bolt,
            darkGrad = listOf(Color(0xFF11263C), Color(0xFF183654)),
            lightGrad = listOf(Color(0xFFFFFFFF), Color(0xFFE0F7FA)),
            accent = colors.cyan
        )
    )
}
