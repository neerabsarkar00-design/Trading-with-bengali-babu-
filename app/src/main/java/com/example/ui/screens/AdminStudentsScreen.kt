package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.analytics.StudentPerformanceSummary
import com.example.data.analytics.formatSignedCurrency
import com.example.data.model.AccountStatus
import com.example.data.model.DisciplineEntry
import com.example.data.model.InviteStatus
import com.example.data.model.StudentInvite
import com.example.data.model.TradeEntry
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FullScreenScreenshotDialog
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalTradingColors
import com.example.utils.ReportExporter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun AdminStudentsScreen(
    summaries: List<StudentPerformanceSummary>,
    invites: List<StudentInvite>,
    allTrades: List<TradeEntry>,
    allDiscipline: List<DisciplineEntry>,
    isBusy: Boolean,
    onUpdateStudentAccount: (
        studentUid: String,
        name: String,
        status: String,
        capital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        adminNotes: String
    ) -> Unit,
    onSaveStudentInvite: (
        existingId: String?,
        name: String,
        email: String,
        status: String,
        capital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        notes: String
    ) -> Unit,
    onDeleteInvite: (String) -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTradingColors.current
    val isDark = colors.isDark
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, SUSPENDED
    var selectedStudentUid by remember { mutableStateOf<String?>(null) }
    var editingStudent by remember { mutableStateOf<UserProfile?>(null) }
    var editingInvite by remember { mutableStateOf<StudentInvite?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }

    // If a student is opened, show their full Individual Performance Page
    val openedSummary = summaries.firstOrNull { it.profile.userId == selectedStudentUid }
    if (openedSummary != null) {
        val stTrades = remember(allTrades, openedSummary.profile.userId) {
            allTrades.filter { it.userId == openedSummary.profile.userId }
        }
        val stDisc = remember(allDiscipline, openedSummary.profile.userId) {
            allDiscipline.filter { it.userId == openedSummary.profile.userId }
        }
        StudentDetailScreen(
            summary = openedSummary,
            trades = stTrades,
            disciplineEntries = stDisc,
            onBack = { selectedStudentUid = null },
            onEditAccount = { editingStudent = openedSummary.profile },
            onNotice = onNotice
        )
        if (editingStudent != null) {
            EditStudentAccountDialog(
                student = editingStudent!!,
                isBusy = isBusy,
                onDismiss = { editingStudent = null },
                onSave = { name, status, cap, goal, maxLoss, maxTrades, notes ->
                    onUpdateStudentAccount(editingStudent!!.userId, name, status, cap, goal, maxLoss, maxTrades, notes)
                    editingStudent = null
                }
            )
        }
        return
    }

    val registeredEmails = remember(summaries) {
        summaries.map { it.profile.email.lowercase(Locale.US) }.toSet()
    }
    val pendingInvites = remember(invites, registeredEmails, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.US)
        invites.filter { inv ->
            inv.email.lowercase(Locale.US) !in registeredEmails &&
                (q.isBlank() || inv.name.lowercase(Locale.US).contains(q) || inv.email.lowercase(Locale.US).contains(q))
        }
    }

    val filteredSummaries = remember(summaries, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase(Locale.US)
        summaries.filter { s ->
            val matchQ = q.isBlank() ||
                s.profile.name.lowercase(Locale.US).contains(q) ||
                s.profile.email.lowercase(Locale.US).contains(q)
            val matchStatus = when (statusFilter) {
                "ACTIVE" -> s.profile.statusEnum == AccountStatus.ACTIVE
                "SUSPENDED" -> s.profile.statusEnum == AccountStatus.SUSPENDED
                else -> true
            }
            matchQ && matchStatus
        }
    }

    val activeCount = summaries.count { it.profile.statusEnum == AccountStatus.ACTIVE }
    val suspendedCount = summaries.count { it.profile.statusEnum == AccountStatus.SUSPENDED }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GradientKpiCard(
                        title = "Total Students",
                        value = summaries.size.toString(),
                        subtitle = "$activeCount active • $suspendedCount suspended",
                        icon = Icons.Default.Group,
                        gradientColors = if (isDark) listOf(Color(0xFF102448), Color(0xFF17315E)) else listOf(Color.White, Color(0xFFEFF6FF)),
                        accentColor = colors.electricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Pending Invites",
                        value = pendingInvites.size.toString(),
                        subtitle = "Pre-configured accounts",
                        icon = Icons.Default.PersonAdd,
                        gradientColors = if (isDark) listOf(Color(0xFF221A4A), Color(0xFF302266)) else listOf(Color.White, Color(0xFFF3E8FF)),
                        accentColor = colors.purple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (summaries.isNotEmpty()) {
                item {
                    StudentComparisonCard(summaries = summaries)
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search students by name or email...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search students") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_student_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All (${summaries.size})", "ACTIVE" to "Active ($activeCount)", "SUSPENDED" to "Suspended ($suspendedCount)").forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                SectionTitle(
                    title = "Registered Student Accounts",
                    subtitle = "Tap any student to open their full trading performance page",
                    accentColor = colors.gold
                )
            }

            if (filteredSummaries.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Registered Students Match",
                        description = "Add student accounts using + Add Student, or wait for students to sign in with their Google Account.",
                        icon = Icons.Default.Group
                    )
                }
            } else {
                items(filteredSummaries, key = { it.profile.userId }) { summary ->
                    StudentSummaryCard(
                        summary = summary,
                        onOpenDetail = { selectedStudentUid = summary.profile.userId },
                        onEditClick = { editingStudent = summary.profile }
                    )
                }
            }

            if (pendingInvites.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Pre-Registered / Invited Students",
                        subtitle = "Settings apply automatically when the student signs in with Google",
                        accentColor = colors.purple
                    )
                }
                items(pendingInvites, key = { it.id.ifBlank { it.email } }) { invite ->
                    StudentInviteCard(
                        invite = invite,
                        onEdit = {
                            editingInvite = invite
                            showInviteDialog = true
                        },
                        onDelete = { onDeleteInvite(invite.id) }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editingInvite = null
                showInviteDialog = true
            },
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
            text = { Text("Add Student") },
            containerColor = colors.gold,
            contentColor = Color(0xFF071022),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("admin_add_student_fab")
        )
    }

    if (editingStudent != null) {
        EditStudentAccountDialog(
            student = editingStudent!!,
            isBusy = isBusy,
            onDismiss = { editingStudent = null },
            onSave = { name, status, cap, goal, maxLoss, maxTrades, notes ->
                onUpdateStudentAccount(editingStudent!!.userId, name, status, cap, goal, maxLoss, maxTrades, notes)
                editingStudent = null
            }
        )
    }

    if (showInviteDialog) {
        StudentInviteDialog(
            existing = editingInvite,
            isBusy = isBusy,
            onDismiss = { showInviteDialog = false },
            onSave = { name, email, status, cap, goal, maxLoss, maxTrades, notes ->
                onSaveStudentInvite(editingInvite?.id, name, email, status, cap, goal, maxLoss, maxTrades, notes)
                showInviteDialog = false
            }
        )
    }
}

@Composable
private fun StudentComparisonCard(summaries: List<StudentPerformanceSummary>) {
    val colors = LocalTradingColors.current
    val maxAbs = max(1.0, summaries.maxOf { abs(it.stats.netProfitLoss) })
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Leaderboard, contentDescription = null, tint = colors.gold)
                Text(
                    text = "Student Performance Comparison",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            summaries.take(6).forEach { s ->
                val isPos = s.stats.netProfitLoss >= 0
                val barColor = if (isPos) colors.profit else colors.loss
                val progress = (abs(s.stats.netProfitLoss) / maxAbs).toFloat().coerceIn(0.05f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${s.profile.name} (${s.stats.totalTrades} trades • ${String.format(Locale.US, "%.0f%%", s.stats.winRatePercent)} WR)",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatSignedCurrency(s.stats.netProfitLoss),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = barColor
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

@Composable
private fun StudentSummaryCard(
    summary: StudentPerformanceSummary,
    onOpenDetail: () -> Unit,
    onEditClick: () -> Unit
) {
    val colors = LocalTradingColors.current
    val student = summary.profile
    val stats = summary.stats
    val isSuspended = student.isSuspendedUser
    val pnlColor = when {
        stats.netProfitLoss > 0 -> colors.profit
        stats.netProfitLoss < 0 -> colors.loss
        else -> colors.gold
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSuspended) colors.loss.copy(alpha = 0.55f) else colors.cardBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onOpenDetail)
            .testTag("student_card_${student.userId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.electricBlue.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.name.take(1).uppercase(Locale.US),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.electricBlue
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = student.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSuspended) colors.lossContainer else colors.profitContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = student.status,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSuspended) colors.loss else colors.profit
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Manage student", tint = colors.gold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.subtleSurface)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Trades", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stats.totalTrades.toString(),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono)
                    )
                }
                Column {
                    Text("Win Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stats.winRatePercent),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                        color = colors.electricBlue
                    )
                }
                Column {
                    Text("SL Disc.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(Locale.US, "%.0f%%", stats.stopLossDisciplinePercent),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                        color = colors.purple
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Net P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatSignedCurrency(stats.netProfitLoss),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = pnlColor
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentInviteCard(
    invite: StudentInvite,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTradingColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.purple.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(invite.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(invite.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Status: ${invite.status} • Goal ₹${invite.dailyGoal.toInt()} • Max Loss ₹${invite.dailyMaxLoss.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.purple
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit invite", tint = colors.electricBlue)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete invite", tint = colors.loss)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    summary: StudentPerformanceSummary,
    trades: List<TradeEntry>,
    disciplineEntries: List<DisciplineEntry>,
    onBack: () -> Unit,
    onEditAccount: () -> Unit,
    onNotice: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val colors = LocalTradingColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var screenshotToZoom by remember { mutableStateOf<TradeEntry?>(null) }
    val tabs = listOf("Analytics", "Trades (${trades.size})", "Discipline (${disciplineEntries.size})")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(summary.profile.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(summary.profile.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("student_detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to students")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ReportExporter.exportAndSharePdf(
                            context = context,
                            reportTitle = "Student Performance Report",
                            periodLabel = "All Time",
                            traderLabel = summary.profile.name,
                            stats = summary.stats,
                            trades = trades,
                            disciplineEntries = disciplineEntries
                        ).onFailure { onNotice(it.localizedMessage ?: "PDF export failed") }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Student PDF", tint = colors.loss)
                    }
                    IconButton(onClick = {
                        ReportExporter.exportAndShareCsv(
                            context = context,
                            trades = trades,
                            filePrefix = "BangaliBabu_${summary.profile.name.replace(" ", "_")}"
                        ).onFailure { onNotice(it.localizedMessage ?: "CSV export failed") }
                    }) {
                        Icon(Icons.Default.TableChart, contentDescription = "Export Student CSV", tint = colors.profit)
                    }
                    IconButton(onClick = onEditAccount) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit student account", tint = colors.gold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DashboardScreen(
                    profile = summary.profile,
                    stats = summary.stats,
                    onAddTradeClick = {},
                    showAddFab = false,
                    headerBannerTitle = "${summary.profile.name} • Net P&L"
                )
                1 -> JournalScreen(
                    trades = trades,
                    onAddTradeClick = {},
                    onEditTradeClick = {},
                    onDeleteTradeClick = {},
                    allowAddEditDelete = false
                )
                2 -> DisciplineScreen(
                    profile = summary.profile,
                    stats = summary.stats,
                    entries = disciplineEntries,
                    isBusy = false,
                    allowEdit = false,
                    onSaveEntry = { _, _, _, _, _, _, _, _, _, _, _ -> },
                    onDeleteEntry = {},
                    onUpdateTargets = { _, _, _, _, _ -> }
                )
            }
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
}

@Composable
private fun EditStudentAccountDialog(
    student: UserProfile,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        status: String,
        capital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        adminNotes: String
    ) -> Unit
) {
    var name by remember(student) { mutableStateOf(student.name) }
    var status by remember(student) { mutableStateOf(student.statusEnum) }
    var capital by remember(student) { mutableStateOf(student.tradingCapital.toString()) }
    var goal by remember(student) { mutableStateOf(student.dailyGoal.toString()) }
    var maxLoss by remember(student) { mutableStateOf(student.dailyMaxLoss.toString()) }
    var maxTrades by remember(student) { mutableStateOf(student.maxTradesPerDay.toString()) }
    var notes by remember(student) { mutableStateOf(student.adminNotes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Student Account") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(student.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Account Status", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = status == AccountStatus.ACTIVE,
                        onClick = { status = AccountStatus.ACTIVE },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                        label = { Text("ACTIVE") }
                    )
                    FilterChip(
                        selected = status == AccountStatus.SUSPENDED,
                        onClick = { status = AccountStatus.SUSPENDED },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        label = { Text("SUSPENDED") }
                    )
                }
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
                    label = { Text("Daily Goal (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxLoss,
                    onValueChange = { maxLoss = it },
                    label = { Text("Daily Max Loss (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxTrades,
                    onValueChange = { maxTrades = it },
                    label = { Text("Max Trades / Day") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Mentor / Admin Notes") },
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
                        name.ifBlank { student.name },
                        status.name,
                        capital.toDoubleOrNull()?.coerceAtLeast(0.0) ?: student.tradingCapital,
                        goal.toDoubleOrNull()?.coerceAtLeast(0.0) ?: student.dailyGoal,
                        maxLoss.toDoubleOrNull()?.coerceAtLeast(0.0) ?: student.dailyMaxLoss,
                        maxTrades.toLongOrNull()?.coerceIn(1L, 100L) ?: student.maxTradesPerDay,
                        notes
                    )
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StudentInviteDialog(
    existing: StudentInvite?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        email: String,
        status: String,
        capital: Double,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Long,
        notes: String
    ) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var email by remember(existing) { mutableStateOf(existing?.email ?: "") }
    var status by remember(existing) { mutableStateOf(existing?.statusEnum ?: InviteStatus.INVITED) }
    var capital by remember(existing) { mutableStateOf((existing?.tradingCapital ?: 100000.0).toString()) }
    var goal by remember(existing) { mutableStateOf((existing?.dailyGoal ?: 2500.0).toString()) }
    var maxLoss by remember(existing) { mutableStateOf((existing?.dailyMaxLoss ?: 1500.0).toString()) }
    var maxTrades by remember(existing) { mutableStateOf((existing?.maxTradesPerDay ?: 3L).toString()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add / Pre-Register Student" else "Edit Student Invite") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!error.isNullOrBlank()) {
                    Text(error!!, color = LocalTradingColors.current.loss, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Student Google Email *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InviteStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = capital,
                    onValueChange = { capital = it },
                    label = { Text("Starting Capital (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Daily Goal (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxLoss,
                    onValueChange = { maxLoss = it },
                    label = { Text("Daily Max Loss (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxTrades,
                    onValueChange = { maxTrades = it },
                    label = { Text("Max Trades / Day") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Mentor Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val cleanEmail = email.trim()
                    if (!cleanEmail.contains("@")) {
                        error = "Enter a valid Google email address."
                    } else {
                        onSave(
                            name.ifBlank { cleanEmail.substringBefore("@") },
                            cleanEmail,
                            status.name,
                            capital.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 100000.0,
                            goal.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 2500.0,
                            maxLoss.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1500.0,
                            maxTrades.toLongOrNull()?.coerceIn(1L, 100L) ?: 3L,
                            notes
                        )
                    }
                }
            ) {
                Text("Save Student")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
