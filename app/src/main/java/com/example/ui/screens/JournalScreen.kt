package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.analytics.formatSignedCurrency
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntry
import com.example.data.model.TradeResult
import com.example.ui.components.DirectionBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FullScreenScreenshotDialog
import com.example.ui.components.ResultBadge
import com.example.ui.components.ScreenshotThumbnail
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@Composable
fun JournalScreen(
    trades: List<TradeEntry>,
    onAddTradeClick: () -> Unit,
    onEditTradeClick: (TradeEntry) -> Unit,
    onDeleteTradeClick: (String) -> Unit,
    allowAddEditDelete: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    var searchQuery by remember { mutableStateOf("") }
    var resultFilter by remember { mutableStateOf<TradeResult?>(null) }
    var directionFilter by remember { mutableStateOf<TradeDirection?>(null) }
    var screenshotToZoom by remember { mutableStateOf<TradeEntry?>(null) }
    var tradePendingDelete by remember { mutableStateOf<TradeEntry?>(null) }

    val filteredTrades = remember(trades, searchQuery, resultFilter, directionFilter) {
        val q = searchQuery.trim().lowercase(Locale.US)
        trades.filter { t ->
            val matchesQ = q.isBlank() ||
                t.instrument.lowercase(Locale.US).contains(q) ||
                t.tradeCode.lowercase(Locale.US).contains(q) ||
                t.strategy.lowercase(Locale.US).contains(q) ||
                t.mistakes.lowercase(Locale.US).contains(q) ||
                t.tradeDate.contains(q) ||
                t.userName.lowercase(Locale.US).contains(q)
            val matchesRes = resultFilter == null || t.resultEnum == resultFilter
            val matchesDir = directionFilter == null || t.directionEnum == directionFilter
            matchesQ && matchesRes && matchesDir
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search instrument, Trade ID, strategy, mistakes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("journal_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = resultFilter == null && directionFilter == null,
                        onClick = {
                            resultFilter = null
                            directionFilter = null
                        },
                        label = { Text("All (${trades.size})") }
                    )
                    TradeResult.entries.forEach { res ->
                        FilterChip(
                            selected = resultFilter == res,
                            onClick = { resultFilter = if (resultFilter == res) null else res },
                            label = { Text(res.label) }
                        )
                    }
                    TradeDirection.entries.forEach { dir ->
                        FilterChip(
                            selected = directionFilter == dir,
                            onClick = { directionFilter = if (directionFilter == dir) null else dir },
                            label = { Text(dir.label) }
                        )
                    }
                }
            }

            if (filteredTrades.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = if (trades.isEmpty()) "Your Trading Journal is Empty" else "No Matching Trades",
                        description = if (trades.isEmpty()) {
                            "Record your trades with Entry/Exit prices, Stop Loss, Strategy, Mistakes, and Chart Screenshots."
                        } else {
                            "Try clearing filters or searching another instrument."
                        },
                        icon = Icons.AutoMirrored.Filled.ReceiptLong
                    )
                }
            } else {
                items(filteredTrades, key = { it.id.ifBlank { it.tradeCode } }) { trade ->
                    TradeRecordCard(
                        trade = trade,
                        allowEditDelete = allowAddEditDelete,
                        onEdit = { onEditTradeClick(trade) },
                        onDelete = { tradePendingDelete = trade },
                        onZoomScreenshot = { screenshotToZoom = trade }
                    )
                }
            }
        }

        if (allowAddEditDelete) {
            ExtendedFloatingActionButton(
                onClick = onAddTradeClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log Trade") },
                containerColor = colors.gold,
                contentColor = Color(0xFF071022),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("journal_add_trade_fab")
            )
        }
    }

    screenshotToZoom?.let { t ->
        t.chartScreenshotBase64?.let { b64 ->
            FullScreenScreenshotDialog(
                base64 = b64,
                title = "${t.instrument} • ${t.tradeCode}",
                onDismiss = { screenshotToZoom = null }
            )
        }
    }

    tradePendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { tradePendingDelete = null },
            title = { Text("Delete Trade ${t.tradeCode}?") },
            text = { Text("This permanently deletes the ${t.instrument} record from your journal.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTradeClick(t.id)
                    tradePendingDelete = null
                }) {
                    Text("Delete", color = colors.loss)
                }
            },
            dismissButton = {
                TextButton(onClick = { tradePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TradeRecordCard(
    trade: TradeEntry,
    allowEditDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onZoomScreenshot: () -> Unit
) {
    val colors = LocalTradingColors.current
    val pnlColor = when {
        trade.profitLoss > 0 -> colors.profit
        trade.profitLoss < 0 -> colors.loss
        else -> colors.gold
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
            .testTag("trade_card_${trade.tradeCode}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = trade.instrument,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DirectionBadge(trade.directionEnum)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.subtleSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = trade.timeframeEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.electricBlue
                        )
                    }
                }
                Text(
                    text = formatSignedCurrency(trade.profitLoss),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = pnlColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${trade.tradeCode} • ${trade.tradeDate} • ${trade.userName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ResultBadge(trade.resultEnum)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.subtleSurface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceColumn("Entry", trade.entryPrice.toString())
                PriceColumn("Exit", trade.exitPrice.toString())
                PriceColumn("SL", if (trade.stopLoss > 0) trade.stopLoss.toString() else "None")
                PriceColumn("R:R", "1:${trade.riskRewardRatio}")
                PriceColumn("Lot", trade.lotSize.toString())
            }

            if (trade.strategy.isNotBlank() || trade.entryReason.isNotBlank()) {
                Text(
                    text = buildString {
                        if (trade.strategy.isNotBlank()) append("Strategy: ${trade.strategy}")
                        if (trade.entryReason.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(trade.entryReason)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (trade.mistakes.isNotBlank()) {
                Text(
                    text = "Mistakes: ${trade.mistakes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.loss
                )
            }
            if (trade.lessonsLearned.isNotBlank()) {
                Text(
                    text = "Lesson: ${trade.lessonsLearned}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.teal
                )
            }
            if (trade.notes.isNotBlank()) {
                Text(
                    text = "Notes: ${trade.notes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!trade.chartScreenshotBase64.isNullOrBlank()) {
                ScreenshotThumbnail(
                    base64 = trade.chartScreenshotBase64,
                    onClick = onZoomScreenshot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }

            if (allowEditDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_trade_${trade.tradeCode}")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit trade", tint = colors.electricBlue)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_trade_${trade.tradeCode}")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete trade", tint = colors.loss)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
