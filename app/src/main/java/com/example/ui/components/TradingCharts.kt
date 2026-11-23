package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.analytics.BreakdownStat
import com.example.data.analytics.DayPnlPoint
import com.example.data.analytics.EquityPoint
import com.example.data.analytics.MonthPnlPoint
import com.example.data.analytics.formatSignedCurrency
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalTradingColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ChartContainerCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalTradingColors.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle(title = title, subtitle = subtitle, accentColor = accentColor)
            content()
        }
    }
}

@Composable
fun DailyPnlBarChart(
    points: List<DayPnlPoint>,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    val recent = remember(points) { points.takeLast(14) }
    var selectedIdx by remember(recent) { mutableIntStateOf(recent.lastIndex.coerceAtLeast(0)) }

    ChartContainerCard(
        title = "Daily Profit & Loss",
        subtitle = "Tap any bar to inspect day P&L and trade count",
        accentColor = colors.profit,
        modifier = modifier
    ) {
        if (recent.isEmpty()) {
            Text(
                text = "Log trades to visualize daily P&L bars.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ChartContainerCard
        }

        val selected = recent.getOrNull(selectedIdx)
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.subtleSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selected.date} (${selected.tradeCount} trades)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatSignedCurrency(selected.pnl),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (selected.pnl >= 0) colors.profit else colors.loss
                )
            }
        }

        val gridColor = colors.cardBorder.copy(alpha = 0.6f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(recent) {
                    detectTapGestures { offset ->
                        val slot = size.width / recent.size.toFloat()
                        val idx = (offset.x / slot).toInt().coerceIn(0, recent.lastIndex)
                        selectedIdx = idx
                    }
                }
        ) {
            val maxAbs = max(1.0, recent.maxOf { abs(it.pnl) })
            val hasPositive = recent.any { it.pnl > 0 }
            val hasNegative = recent.any { it.pnl < 0 }
            val zeroY = when {
                hasPositive && hasNegative -> size.height * 0.5f
                hasNegative -> size.height * 0.12f
                else -> size.height * 0.88f
            }

            // Baseline
            drawLine(
                color = gridColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 2f
            )

            val slotW = size.width / recent.size.toFloat()
            val barW = (slotW * 0.58f).coerceIn(8f, 44f)
            val usableTop = (zeroY - 10f).coerceAtLeast(12f)
            val usableBottom = (size.height - zeroY - 10f).coerceAtLeast(12f)

            recent.forEachIndexed { idx, pt ->
                val centerX = slotW * idx + slotW / 2f
                val left = centerX - barW / 2f
                val isPos = pt.pnl >= 0
                val barColor = if (isPos) colors.profit else colors.loss
                val alpha = if (idx == selectedIdx) 1f else 0.78f
                val mag = if (isPos) {
                    (pt.pnl / maxAbs).toFloat() * usableTop
                } else {
                    (abs(pt.pnl) / maxAbs).toFloat() * usableBottom
                }.coerceAtLeast(4f)

                val top = if (isPos) zeroY - mag else zeroY
                drawRoundRect(
                    color = barColor.copy(alpha = alpha),
                    topLeft = Offset(left, top),
                    size = Size(barW, mag),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                if (idx == selectedIdx) {
                    drawRoundRect(
                        color = colors.gold,
                        topLeft = Offset(left, top),
                        size = Size(barW, mag),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = recent.first().label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Last ${recent.size} trading days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = recent.last().label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MonthlyPerformanceChart(
    points: List<MonthPnlPoint>,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    val recent = remember(points) { points.takeLast(8) }
    var selectedIdx by remember(recent) { mutableIntStateOf(recent.lastIndex.coerceAtLeast(0)) }

    ChartContainerCard(
        title = "Monthly Performance",
        subtitle = "Net profit & loss across recent months",
        accentColor = colors.purple,
        modifier = modifier
    ) {
        if (recent.isEmpty()) {
            Text(
                text = "Monthly performance bars will appear after saving trades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ChartContainerCard
        }

        val selected = recent.getOrNull(selectedIdx)
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.subtleSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selected.label} (${selected.tradeCount} trades)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatSignedCurrency(selected.pnl),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (selected.pnl >= 0) colors.profit else colors.loss
                )
            }
        }

        val gridColor = colors.cardBorder.copy(alpha = 0.6f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(recent) {
                    detectTapGestures { offset ->
                        val slot = size.width / recent.size.toFloat()
                        selectedIdx = (offset.x / slot).toInt().coerceIn(0, recent.lastIndex)
                    }
                }
        ) {
            val maxAbs = max(1.0, recent.maxOf { abs(it.pnl) })
            val hasPos = recent.any { it.pnl > 0 }
            val hasNeg = recent.any { it.pnl < 0 }
            val zeroY = when {
                hasPos && hasNeg -> size.height * 0.5f
                hasNeg -> size.height * 0.14f
                else -> size.height * 0.86f
            }

            drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)

            val slotW = size.width / recent.size.toFloat()
            val barW = (slotW * 0.5f).coerceIn(14f, 48f)
            val usableTop = (zeroY - 10f).coerceAtLeast(12f)
            val usableBottom = (size.height - zeroY - 10f).coerceAtLeast(12f)

            recent.forEachIndexed { idx, pt ->
                val isPos = pt.pnl >= 0
                val left = slotW * idx + (slotW - barW) / 2f
                val h = if (isPos) {
                    (pt.pnl / maxAbs).toFloat() * usableTop
                } else {
                    (abs(pt.pnl) / maxAbs).toFloat() * usableBottom
                }.coerceAtLeast(6f)
                val top = if (isPos) zeroY - h else zeroY
                val brush = if (isPos) {
                    Brush.verticalGradient(listOf(colors.electricBlue, colors.profit))
                } else {
                    Brush.verticalGradient(listOf(colors.loss, colors.purple))
                }
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, top),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                if (idx == selectedIdx) {
                    drawRoundRect(
                        color = colors.gold,
                        topLeft = Offset(left, top),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            recent.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WinLossPieChart(
    wins: Int,
    losses: Int,
    breakEven: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    val total = wins + losses + breakEven

    ChartContainerCard(
        title = "Win vs Loss Breakdown",
        subtitle = "Distribution across all saved outcomes",
        accentColor = colors.gold,
        modifier = modifier
    ) {
        if (total == 0) {
            Text(
                text = "No outcome distribution available yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ChartContainerCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(124.dp)) {
                    val strokeW = 24.dp.toPx()
                    val diameter = size.minDimension - strokeW
                    val topLeft = Offset(strokeW / 2f, strokeW / 2f)
                    val arcSize = Size(diameter, diameter)

                    var startAngle = -90f
                    val segments = listOf(
                        wins to colors.profit,
                        losses to colors.loss,
                        breakEven to colors.gold
                    )
                    segments.forEach { (count, segColor) ->
                        if (count > 0) {
                            val sweep = (count.toFloat() / total.toFloat()) * 360f
                            drawArc(
                                color = segColor,
                                startAngle = startAngle,
                                sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val winPct = (wins.toDouble() / total) * 100.0
                    Text(
                        text = String.format(Locale.US, "%.0f%%", winPct),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.profit
                    )
                    Text(
                        text = "Win Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PieLegendRow("Winning Trades", wins, total, colors.profit)
                PieLegendRow("Losing Trades", losses, total, colors.loss)
                PieLegendRow("Break Even", breakEven, total, colors.gold)
            }
        }
    }
}

@Composable
private fun PieLegendRow(label: String, count: Int, total: Int, color: Color) {
    val pct = if (total > 0) (count.toDouble() / total) * 100.0 else 0.0
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = String.format(Locale.US, "%d (%.1f%%)", count, pct),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
            color = color
        )
    }
}

@Composable
fun EquityCurveChart(
    points: List<EquityPoint>,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    var selectedIdx by remember(points) { mutableIntStateOf(points.lastIndex.coerceAtLeast(0)) }

    ChartContainerCard(
        title = "Trading History Equity Curve",
        subtitle = "Cumulative net P&L progression across trades",
        accentColor = colors.electricBlue,
        modifier = modifier
    ) {
        if (points.isEmpty()) {
            Text(
                text = "Your equity curve builds automatically as trades are logged.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ChartContainerCard
        }

        val selected = points.getOrNull(selectedIdx)
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.subtleSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Trade #${selected.index} • ${selected.tradeCode}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatSignedCurrency(selected.cumulativePnl),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (selected.cumulativePnl >= 0) colors.profit else colors.loss
                )
            }
        }

        val lineColor = if ((points.lastOrNull()?.cumulativePnl ?: 0.0) >= 0.0) colors.profit else colors.loss
        val gridColor = colors.cardBorder.copy(alpha = 0.5f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size == 1) {
                            selectedIdx = 0
                        } else {
                            val step = size.width / (points.size - 1).toFloat()
                            selectedIdx = (offset.x / step).toInt().coerceIn(0, points.lastIndex)
                        }
                    }
                }
        ) {
            val minVal = minOf(0.0, points.minOf { it.cumulativePnl })
            val maxVal = maxOf(0.0, points.maxOf { it.cumulativePnl })
            val span = max(1.0, maxVal - minVal)
            val padY = 14f
            val usableH = size.height - padY * 2

            fun mapY(value: Double): Float {
                val ratio = ((value - minVal) / span).toFloat()
                return size.height - padY - (ratio * usableH)
            }

            val zeroY = mapY(0.0)
            drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)

            if (points.size == 1) {
                val cx = size.width / 2f
                val cy = mapY(points[0].cumulativePnl)
                drawCircle(lineColor, radius = 8f, center = Offset(cx, cy))
                return@Canvas
            }

            val stepX = size.width / (points.size - 1).toFloat()
            val linePath = Path()
            val fillPath = Path()

            points.forEachIndexed { i, pt ->
                val x = i * stepX
                val y = mapY(pt.cumulativePnl)
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, zeroY)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (i == points.lastIndex) {
                    fillPath.lineTo(x, zeroY)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Selected dot
            val sx = selectedIdx * stepX
            val sy = mapY(points[selectedIdx].cumulativePnl)
            drawCircle(colors.gold, radius = 9f, center = Offset(sx, sy))
            drawCircle(lineColor, radius = 5f, center = Offset(sx, sy))
        }
    }
}

@Composable
fun InstrumentPerformanceChart(
    items: List<BreakdownStat>,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    val topItems = remember(items) { items.take(8) }
    val maxAbs = remember(topItems) { max(1.0, topItems.maxOfOrNull { abs(it.netPnl) } ?: 1.0) }

    ChartContainerCard(
        title = "Performance by Instrument",
        subtitle = "Net P&L, trade count & win rate per symbol",
        accentColor = colors.teal,
        modifier = modifier
    ) {
        if (topItems.isEmpty()) {
            Text(
                text = "Instrument analytics appear once you log trades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ChartContainerCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            topItems.forEach { stat ->
                val isPos = stat.netPnl >= 0
                val barColor = if (isPos) colors.profit else colors.loss
                val progress = (abs(stat.netPnl) / maxAbs).toFloat().coerceIn(0.05f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${stat.name} • ${stat.trades} trades (${String.format(Locale.US, "%.0f%%", stat.winRate)} WR)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatSignedCurrency(stat.netPnl),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = barColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = colors.subtleSurface
                    )
                }
            }
        }
    }
}

@Composable
fun TimeframePerformanceChart(
    items: List<BreakdownStat>,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    val maxAbs = remember(items) { max(1.0, items.maxOfOrNull { abs(it.netPnl) } ?: 1.0) }

    ChartContainerCard(
        title = "Performance by Timeframe",
        subtitle = "M1, M5, M15, M30, H1, H4, D1 win rate & net P&L",
        accentColor = colors.cyan,
        modifier = modifier
    ) {
        if (items.isEmpty()) {
            Text(
                text = "Timeframe analytics appear once you log trades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ChartContainerCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { stat ->
                val isPos = stat.netPnl >= 0
                val barColor = if (isPos) colors.cyan else colors.loss
                val progress = (abs(stat.netPnl) / maxAbs).toFloat().coerceIn(0.05f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.electricBlue.copy(alpha = 0.16f))
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stat.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.electricBlue
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${stat.trades} trades • ${String.format(Locale.US, "%.0f%%", stat.winRate)} WR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSignedCurrency(stat.netPnl),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                color = if (isPos) colors.profit else colors.loss
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = barColor,
                            trackColor = colors.subtleSurface
                        )
                    }
                }
            }
        }
    }
}
