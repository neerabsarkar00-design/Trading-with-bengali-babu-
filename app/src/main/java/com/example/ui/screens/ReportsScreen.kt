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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.analytics.TradingAnalytics
import com.example.data.analytics.formatSignedCurrency
import com.example.data.model.DisciplineEntry
import com.example.data.model.StudentInvite
import com.example.data.model.ThemeMode
import com.example.data.model.TradeEntry
import com.example.data.model.UserProfile
import com.example.ui.components.SectionTitle
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.VibrantPurple
import com.example.utils.ReportExporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class ReportPeriod(val label: String) {
    DAILY("Daily (Today)"),
    WEEKLY("Weekly (7 Days)"),
    MONTHLY("Monthly (30 Days)"),
    ALL("All Time"),
    CUSTOM("Custom Range")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    currentUser: UserProfile,
    myTrades: List<TradeEntry>,
    myDiscipline: List<DisciplineEntry>,
    allStudents: List<UserProfile>,
    allInvites: List<StudentInvite>,
    allAdminTrades: List<TradeEntry>,
    allAdminDiscipline: List<DisciplineEntry>,
    themeMode: ThemeMode,
    isBusy: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onUpdateTargets: (name: String, capital: Double, dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Long) -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalTradingColors.current

    var selectedPeriod by remember { mutableStateOf(ReportPeriod.MONTHLY) }
    var customStartMillis by remember { mutableLongStateOf(System.currentTimeMillis() - 7L * 86400_000L) }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Admin scope: "MY_OWN", "ALL_STUDENTS", or a specific student's userId
    var adminScopeId by remember { mutableStateOf("MY_OWN") }
    var showTargetsDialog by remember { mutableStateOf(false) }

    val baseTrades = remember(currentUser, adminScopeId, myTrades, allAdminTrades) {
        if (!currentUser.isAdminUser) {
            myTrades
        } else {
            when (adminScopeId) {
                "MY_OWN" -> myTrades
                "ALL_STUDENTS" -> allAdminTrades
                else -> allAdminTrades.filter { it.userId == adminScopeId }
            }
        }
    }

    val baseDiscipline = remember(currentUser, adminScopeId, myDiscipline, allAdminDiscipline) {
        if (!currentUser.isAdminUser) {
            myDiscipline
        } else {
            when (adminScopeId) {
                "MY_OWN" -> myDiscipline
                "ALL_STUDENTS" -> allAdminDiscipline
                else -> allAdminDiscipline.filter { it.userId == adminScopeId }
            }
        }
    }

    val traderLabel = remember(currentUser, adminScopeId, allStudents) {
        if (!currentUser.isAdminUser) {
            currentUser.name
        } else {
            when (adminScopeId) {
                "MY_OWN" -> "${currentUser.name} (Admin)"
                "ALL_STUDENTS" -> "All Traders Combined"
                else -> allStudents.firstOrNull { it.userId == adminScopeId }?.name ?: "Student"
            }
        }
    }

    val (startIso, endIso, periodDisplay) = remember(selectedPeriod, customStartMillis, customEndMillis) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        when (selectedPeriod) {
            ReportPeriod.DAILY -> Triple(today, today, "Daily ($today)")
            ReportPeriod.WEEKLY -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                val start = sdf.format(cal.time)
                Triple(start, today, "Weekly ($start to $today)")
            }
            ReportPeriod.MONTHLY -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -29) }
                val start = sdf.format(cal.time)
                Triple(start, today, "Monthly ($start to $today)")
            }
            ReportPeriod.ALL -> Triple("0000-01-01", "9999-12-31", "All Time")
            ReportPeriod.CUSTOM -> {
                val s = sdf.format(Date(customStartMillis))
                val e = sdf.format(Date(customEndMillis))
                Triple(s, e, "Custom ($s to $e)")
            }
        }
    }

    val filteredTrades = remember(baseTrades, startIso, endIso) {
        baseTrades.filter { t ->
            t.tradeDate >= startIso && t.tradeDate <= endIso
        }
    }

    val filteredStats = remember(filteredTrades, currentUser) {
        TradingAnalytics.computeStats(
            trades = filteredTrades,
            maxTradesPerDay = currentUser.maxTradesPerDay.toInt().coerceAtLeast(1),
            tradingCapital = currentUser.tradingCapital
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                title = "Trading Reports & Exports",
                subtitle = "Generate PDF, Excel/CSV, and full JSON backups",
                accentColor = colors.gold
            )
        }

        if (currentUser.isAdminUser) {
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
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Admin Report Scope", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = adminScopeId == "MY_OWN",
                                onClick = { adminScopeId = "MY_OWN" },
                                label = { Text("My Trades") }
                            )
                            FilterChip(
                                selected = adminScopeId == "ALL_STUDENTS",
                                onClick = { adminScopeId = "ALL_STUDENTS" },
                                label = { Text("Combined All (${allAdminTrades.size})") }
                            )
                            allStudents.filter { !it.isAdminUser }.forEach { st ->
                                FilterChip(
                                    selected = adminScopeId == st.userId,
                                    onClick = { adminScopeId = st.userId },
                                    label = { Text(st.name) }
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
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Report Date Range", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = {
                                    selectedPeriod = period
                                    if (period == ReportPeriod.CUSTOM) showDateRangePicker = true
                                },
                                label = { Text(period.label) }
                            )
                        }
                    }
                    if (selectedPeriod == ReportPeriod.CUSTOM) {
                        OutlinedButton(
                            onClick = { showDateRangePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(periodDisplay)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.subtleSurface)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Matching Trades", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = filteredStats.totalTrades.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            )
                        }
                        Column {
                            Text("Win Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "%.1f%%", filteredStats.winRatePercent),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                color = colors.electricBlue
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Net P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatSignedCurrency(filteredStats.netProfitLoss),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                color = if (filteredStats.netProfitLoss >= 0) colors.profit else colors.loss
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                ReportExporter.exportAndSharePdf(
                                    context = context,
                                    reportTitle = "${selectedPeriod.label} Trading Report",
                                    periodLabel = periodDisplay,
                                    traderLabel = traderLabel,
                                    stats = filteredStats,
                                    trades = filteredTrades,
                                    disciplineEntries = baseDiscipline
                                ).onSuccess { fileName ->
                                    onNotice("PDF ready: $fileName")
                                }.onFailure { err ->
                                    onNotice(err.localizedMessage ?: "PDF export failed")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_pdf_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF", color = Color.White)
                        }

                        Button(
                            onClick = {
                                ReportExporter.exportAndShareCsv(
                                    context = context,
                                    trades = filteredTrades
                                ).onSuccess { fileName ->
                                    onNotice("CSV ready: $fileName")
                                }.onFailure { err ->
                                    onNotice(err.localizedMessage ?: "CSV export failed")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_csv_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Excel / CSV", color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            SectionTitle(
                title = "Database Backup & Settings",
                subtitle = "Download JSON backup, customize theme & risk limits",
                accentColor = colors.electricBlue
            )
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { onSetThemeMode(ThemeMode.DARK) },
                            leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                            label = { Text("Dark Navy") }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                            leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null) },
                            label = { Text("Light") }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                            label = { Text("System") }
                        )
                    }

                    OutlinedButton(
                        onClick = { showTargetsDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Personal Capital, Goal & Loss Limits")
                    }

                    Button(
                        onClick = {
                            ReportExporter.exportAndShareBackupJson(
                                context = context,
                                currentUser = currentUser,
                                trades = if (currentUser.isAdminUser) allAdminTrades else myTrades,
                                disciplineEntries = if (currentUser.isAdminUser) allAdminDiscipline else myDiscipline,
                                students = allStudents,
                                invites = allInvites
                            ).onSuccess { fileName ->
                                onNotice("Backup ready: $fileName")
                            }.onFailure { err ->
                                onNotice(err.localizedMessage ?: "Backup export failed")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_backup_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(RoyalBlue, VibrantPurple))),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, tint = Color.White)
                                Text(
                                    text = if (currentUser.isAdminUser) "Full Platform Database Backup (JSON)" else "Export My Journal Backup (JSON)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customStartMillis,
            initialSelectedEndDateMillis = customEndMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    rangeState.selectedStartDateMillis?.let { customStartMillis = it }
                    rangeState.selectedEndDateMillis?.let { customEndMillis = it }
                    showDateRangePicker = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(420.dp))
        }
    }

    if (showTargetsDialog) {
        RiskTargetsDialog(
            profile = currentUser,
            isBusy = isBusy,
            onDismiss = { showTargetsDialog = false },
            onSave = { name, cap, goal, maxLoss, maxTrades ->
                onUpdateTargets(name, cap, goal, maxLoss, maxTrades)
                showTargetsDialog = false
            }
        )
    }
}
