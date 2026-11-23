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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.analytics.DashboardStats
import com.example.data.analytics.TradingAnalytics
import com.example.data.analytics.formatCurrency
import com.example.data.analytics.formatSignedCurrency
import com.example.data.model.DisciplineEntry
import com.example.data.model.DisciplineEntryType
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalTradingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun DisciplineScreen(
    profile: UserProfile,
    stats: DashboardStats,
    entries: List<DisciplineEntry>,
    isBusy: Boolean,
    allowEdit: Boolean = true,
    onSaveEntry: (
        existingId: String?,
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
    ) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onUpdateTargets: (
        name: String,
        capital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    var typeFilter by remember { mutableStateOf<DisciplineEntryType?>(null) }
    var editingEntry by remember { mutableStateOf<DisciplineEntry?>(null) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var showTargetsDialog by remember { mutableStateOf(false) }

    val filteredEntries = remember(entries, typeFilter) {
        if (typeFilter == null) entries else entries.filter { it.entryTypeEnum == typeFilter }
    }

    val todayPnl = stats.todayNetPnl
    val goalProgress = if (profile.dailyGoal > 0.0 && todayPnl > 0.0) {
        (todayPnl / profile.dailyGoal).toFloat().coerceIn(0f, 1f)
    } else 0f
    val lossUsed = if (todayPnl < 0.0) abs(todayPnl) else 0.0
    val lossProgress = if (profile.dailyMaxLoss > 0.0) {
        (lossUsed / profile.dailyMaxLoss).toFloat().coerceIn(0f, 1f)
    } else 0f
    val isLossLimitBreached = profile.dailyMaxLoss > 0.0 && lossUsed >= profile.dailyMaxLoss
    val isOvertradingToday = stats.todayTradeCount > profile.maxTradesPerDay

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(
                        title = "Risk & Discipline Tracker",
                        subtitle = "Live tracking against your daily trading rules",
                        accentColor = colors.gold,
                        modifier = Modifier.weight(1f)
                    )
                    if (allowEdit) {
                        OutlinedButton(
                            onClick = { showTargetsDialog = true },
                            modifier = Modifier.testTag("edit_risk_targets_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Limits")
                        }
                    }
                }
            }

            if (isLossLimitBreached || isOvertradingToday) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.loss, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.lossContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.loss)
                            Column {
                                Text(
                                    text = "Trading Discipline Alert",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.loss
                                )
                                if (isLossLimitBreached) {
                                    Text(
                                        text = "Daily Maximum Loss Limit (${formatCurrency(profile.dailyMaxLoss)}) reached today. Stop trading for the day.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.loss
                                    )
                                }
                                if (isOvertradingToday) {
                                    Text(
                                        text = "Overtrading detected: ${stats.todayTradeCount} trades logged today (Limit: ${profile.maxTradesPerDay}).",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.loss
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Daily Goal
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Daily Trading Goal", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${formatSignedCurrency(todayPnl)} / ${formatCurrency(profile.dailyGoal)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = if (todayPnl >= 0) colors.profit else colors.loss
                                )
                            }
                            LinearProgressIndicator(
                                progress = { goalProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = colors.profit,
                                trackColor = colors.subtleSurface
                            )
                        }

                        // Daily Max Loss Limit
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Daily Maximum Loss Limit", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${formatCurrency(lossUsed)} / ${formatCurrency(profile.dailyMaxLoss)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = if (isLossLimitBreached) colors.loss else colors.gold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { lossProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (lossProgress >= 0.8f) colors.loss else colors.gold,
                                trackColor = colors.subtleSurface
                            )
                        }

                        // Risk & Discipline Meters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DisciplineMetricPill(
                                label = "Stop-Loss Discipline",
                                value = String.format(Locale.US, "%.0f%%", stats.stopLossDisciplinePercent),
                                color = if (stats.stopLossDisciplinePercent >= 80) colors.profit else colors.gold,
                                modifier = Modifier.weight(1f)
                            )
                            DisciplineMetricPill(
                                label = "Overtrading Days",
                                value = "${stats.overtradingDaysCount} days",
                                color = if (stats.overtradingDaysCount == 0) colors.profit else colors.loss,
                                modifier = Modifier.weight(1f)
                            )
                            DisciplineMetricPill(
                                label = "Avg Risk / Capital",
                                value = String.format(Locale.US, "%.2f%%", stats.averageRiskPercentOfCapital),
                                color = if (stats.averageRiskPercentOfCapital <= 2.0) colors.electricBlue else colors.loss,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (stats.mistakeBreakdown.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionTitle(
                                title = "Mistake Tracking Summary",
                                subtitle = "Most frequent errors across your saved trades",
                                accentColor = colors.loss
                            )
                            stats.mistakeBreakdown.take(6).forEach { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.subtleSurface)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = m.mistake,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${m.count}x",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                        color = colors.loss
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    title = "Psychology Notes & Reviews",
                    subtitle = "Daily mindset notes, weekly self-reviews & monthly reviews",
                    accentColor = colors.purple
                )
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text("All (${entries.size})") }
                    )
                    DisciplineEntryType.entries.forEach { t ->
                        FilterChip(
                            selected = typeFilter == t,
                            onClick = { typeFilter = if (typeFilter == t) null else t },
                            label = { Text(t.label) }
                        )
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Psychology or Review Notes Yet",
                        description = "Add Daily Psychology Notes, Weekly Self-Reviews, or Monthly Trading Reviews to track mindset & consistency.",
                        icon = Icons.Default.Psychology
                    )
                }
            } else {
                items(filteredEntries, key = { it.id.ifBlank { it.periodLabel + it.entryType } }) { entry ->
                    DisciplineEntryCard(
                        entry = entry,
                        allowEdit = allowEdit,
                        onEdit = {
                            editingEntry = entry
                            showEntryDialog = true
                        },
                        onDelete = { onDeleteEntry(entry.id) }
                    )
                }
            }
        }

        if (allowEdit) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingEntry = null
                    showEntryDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Review / Note") },
                containerColor = colors.purple,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_discipline_entry_fab")
            )
        }
    }

    if (showEntryDialog) {
        DisciplineEntryDialog(
            existing = editingEntry,
            isBusy = isBusy,
            onDismiss = { showEntryDialog = false },
            onSave = { type, period, mood, rules, sl, over, notes, mistakes, plan, score ->
                onSaveEntry(
                    editingEntry?.id,
                    type,
                    period,
                    mood,
                    rules,
                    sl,
                    over,
                    notes,
                    mistakes,
                    plan,
                    score
                )
                showEntryDialog = false
            }
        )
    }

    if (showTargetsDialog) {
        RiskTargetsDialog(
            profile = profile,
            isBusy = isBusy,
            onDismiss = { showTargetsDialog = false },
            onSave = { name, cap, goal, maxLoss, maxTrades ->
                onUpdateTargets(name, cap, goal, maxLoss, maxTrades)
                showTargetsDialog = false
            }
        )
    }
}

@Composable
private fun DisciplineMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.subtleSurface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

@Composable
fun DisciplineEntryCard(
    entry: DisciplineEntry,
    allowEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTradingColors.current
    val badgeColor = when (entry.entryTypeEnum) {
        DisciplineEntryType.DAILY_PSYCHOLOGY -> colors.electricBlue
        DisciplineEntryType.WEEKLY_REVIEW -> colors.purple
        DisciplineEntryType.MONTHLY_REVIEW -> colors.gold
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp)),
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = entry.entryTypeEnum.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor
                        )
                    }
                    Text(
                        text = entry.periodLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Score ${entry.disciplineScore}/10",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                    color = if (entry.disciplineScore >= 7) colors.profit else colors.loss
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusTag("Mood: ${entry.moodTag}", colors.cyan)
                StatusTag(
                    if (entry.followedRules) "Followed Plan" else "Broke Rules",
                    if (entry.followedRules) colors.profit else colors.loss
                )
                if (entry.overtraded) {
                    StatusTag("Overtraded", colors.loss)
                }
            }

            if (entry.psychologyNotes.isNotBlank()) {
                Text(
                    text = "Psychology: ${entry.psychologyNotes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (entry.mistakesSummary.isNotBlank()) {
                Text(
                    text = "Mistakes: ${entry.mistakesSummary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.loss
                )
            }
            if (entry.improvementsPlan.isNotBlank()) {
                Text(
                    text = "Action Plan: ${entry.improvementsPlan}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.teal
                )
            }

            if (allowEdit) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit review", tint = colors.electricBlue)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete review", tint = colors.loss)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun DisciplineEntryDialog(
    existing: DisciplineEntry?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    var entryType by remember(existing) {
        mutableStateOf(existing?.entryTypeEnum ?: DisciplineEntryType.DAILY_PSYCHOLOGY)
    }
    var periodLabel by remember(existing) {
        mutableStateOf(
            existing?.periodLabel ?: TradingAnalytics.todayIsoDate()
        )
    }
    var moodTag by remember(existing) { mutableStateOf(existing?.moodTag ?: "Calm") }
    var followedRules by remember(existing) { mutableStateOf(existing?.followedRules ?: true) }
    var respectedSl by remember(existing) { mutableStateOf(existing?.respectedStopLoss ?: true) }
    var overtraded by remember(existing) { mutableStateOf(existing?.overtraded ?: false) }
    var psychologyNotes by remember(existing) { mutableStateOf(existing?.psychologyNotes ?: "") }
    var mistakesSummary by remember(existing) { mutableStateOf(existing?.mistakesSummary ?: "") }
    var improvementsPlan by remember(existing) { mutableStateOf(existing?.improvementsPlan ?: "") }
    var score by remember(existing) { mutableFloatStateOf((existing?.disciplineScore ?: 8L).toFloat()) }

    val moods = listOf("Calm", "Focused", "Confident", "FOMO", "Revenge", "Fearful", "Greedy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Discipline / Review" else "Edit Discipline Entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DisciplineEntryType.entries.forEach { t ->
                        FilterChip(
                            selected = entryType == t,
                            onClick = {
                                entryType = t
                                if (existing == null) {
                                    periodLabel = when (t) {
                                        DisciplineEntryType.DAILY_PSYCHOLOGY -> TradingAnalytics.todayIsoDate()
                                        DisciplineEntryType.WEEKLY_REVIEW -> SimpleDateFormat("yyyy-'W'ww", Locale.US).format(Date())
                                        DisciplineEntryType.MONTHLY_REVIEW -> SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                                    }
                                }
                            },
                            label = { Text(t.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = periodLabel,
                    onValueChange = { periodLabel = it },
                    label = { Text("Period (Date / Week / Month)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Emotional State", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    moods.forEach { m ->
                        FilterChip(
                            selected = moodTag == m,
                            onClick = { moodTag = m },
                            label = { Text(m) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = followedRules, onCheckedChange = { followedRules = it })
                    Text("Followed Trading Rules")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = respectedSl, onCheckedChange = { respectedSl = it })
                    Text("Respected Stop Loss")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = overtraded, onCheckedChange = { overtraded = it })
                    Text("Overtraded")
                }

                Text("Discipline Score: ${score.roundToLong()}/10", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = score,
                    onValueChange = { score = it },
                    valueRange = 1f..10f,
                    steps = 8
                )

                OutlinedTextField(
                    value = psychologyNotes,
                    onValueChange = { psychologyNotes = it },
                    label = { Text("Trading Psychology Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mistakesSummary,
                    onValueChange = { mistakesSummary = it },
                    label = { Text("Mistakes Made") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = improvementsPlan,
                    onValueChange = { improvementsPlan = it },
                    label = { Text("Improvement Plan") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    onSave(
                        entryType.name,
                        periodLabel,
                        moodTag,
                        followedRules,
                        respectedSl,
                        overtraded,
                        psychologyNotes,
                        mistakesSummary,
                        improvementsPlan,
                        score.roundToLong().coerceIn(1L, 10L)
                    )
                },
                modifier = Modifier.testTag("save_discipline_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RiskTargetsDialog(
    profile: UserProfile,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, capital: Double, dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Long) -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var capital by remember(profile) { mutableStateOf(profile.tradingCapital.toString()) }
    var goal by remember(profile) { mutableStateOf(profile.dailyGoal.toString()) }
    var maxLoss by remember(profile) { mutableStateOf(profile.dailyMaxLoss.toString()) }
    var maxTrades by remember(profile) { mutableStateOf(profile.maxTradesPerDay.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trading Goals & Risk Limits") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = capital,
                    onValueChange = { capital = it },
                    label = { Text("Trading Capital (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Daily Profit Goal (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxLoss,
                    onValueChange = { maxLoss = it },
                    label = { Text("Daily Max Loss Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxTrades,
                    onValueChange = { maxTrades = it },
                    label = { Text("Max Trades Per Day (Overtrading Alert)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    onSave(
                        name.ifBlank { profile.name },
                        capital.toDoubleOrNull()?.coerceAtLeast(0.0) ?: profile.tradingCapital,
                        goal.toDoubleOrNull()?.coerceAtLeast(0.0) ?: profile.dailyGoal,
                        maxLoss.toDoubleOrNull()?.coerceAtLeast(0.0) ?: profile.dailyMaxLoss,
                        maxTrades.toLongOrNull()?.coerceIn(1L, 100L) ?: profile.maxTradesPerDay
                    )
                },
                modifier = Modifier.testTag("save_risk_targets_button")
            ) {
                Text("Save Targets")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
