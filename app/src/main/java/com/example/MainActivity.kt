package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.TradingRepository
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.signOutUser
import com.example.data.model.ThemeMode
import com.example.data.model.TradeEntry
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.prefs.ThemePreferences
import com.example.ui.components.AppTopHeader
import com.example.ui.screens.AdminStudentsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DisciplineScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SuspendedAccountScreen
import com.example.ui.screens.TradeEntryScreen
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.TradingJournalTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    JOURNAL("Journal", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook, "nav_journal"),
    STUDENTS("Students", Icons.Filled.Group, Icons.Outlined.Group, "nav_students"),
    DISCIPLINE("Discipline", Icons.Filled.Psychology, Icons.Outlined.Psychology, "nav_discipline"),
    REPORTS("Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment, "nav_reports")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePrefs = ThemePreferences(applicationContext)
        setContent {
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val scope = rememberCoroutineScope()
            TradingJournalTheme(themeMode = themeMode) {
                AppRoot(
                    themeMode = themeMode,
                    onSetThemeMode = { mode ->
                        scope.launch { themePrefs.setThemeMode(mode) }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var checkedAutoSignIn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (!checkedAutoSignIn) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    checkedAutoSignIn = true
                },
                onUnauthenticated = {
                    checkedAutoSignIn = true
                },
                scope = scope
            )
        }
    }

    val toggleTheme = {
        val next = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        onSetThemeMode(next)
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            themeMode = themeMode,
            onToggleTheme = toggleTheme,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedAppContent(
                firebaseUser = user,
                themeMode = themeMode,
                onToggleTheme = toggleTheme,
                onSetThemeMode = onSetThemeMode,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedAppContent(
    firebaseUser: FirebaseUser,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit
) {
    val viewModel: MainViewModel = viewModel(
        key = firebaseUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(TradingRepository(db), firebaseUser)
            }
        }
    )

    val profileUi by viewModel.profileState.collectAsStateWithLifecycle()
    val myTradesUi by viewModel.myTradesState.collectAsStateWithLifecycle()
    val myDisciplineUi by viewModel.myDisciplineState.collectAsStateWithLifecycle()
    val myStats by viewModel.myStats.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val adminUsersUi by viewModel.adminUsersState.collectAsStateWithLifecycle()
    val adminInvitesUi by viewModel.adminInvitesState.collectAsStateWithLifecycle()
    val adminAllTradesUi by viewModel.adminAllTradesState.collectAsStateWithLifecycle()
    val adminAllDisciplineUi by viewModel.adminAllDisciplineState.collectAsStateWithLifecycle()
    val studentSummaries by viewModel.studentPerformanceSummaries.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val pState = profileUi) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Loading Trading with Bangali Babu...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = pState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.bootstrapProfile() }) {
                            Text("Retry")
                        }
                        Button(onClick = onSignOut) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
        is UiState.Success -> {
            val profile = pState.data
            if (profile.isSuspendedUser && !profile.isAdminUser) {
                SuspendedAccountScreen(profile = profile, onSignOut = onSignOut)
                return
            }

            MainWorkspaceScaffold(
                profile = profile,
                myTrades = (myTradesUi as? UiState.Success)?.data.orEmpty(),
                myDiscipline = (myDisciplineUi as? UiState.Success)?.data.orEmpty(),
                myStats = myStats,
                allUsers = (adminUsersUi as? UiState.Success)?.data.orEmpty(),
                allInvites = (adminInvitesUi as? UiState.Success)?.data.orEmpty(),
                allAdminTrades = (adminAllTradesUi as? UiState.Success)?.data.orEmpty(),
                allAdminDiscipline = (adminAllDisciplineUi as? UiState.Success)?.data.orEmpty(),
                studentSummaries = studentSummaries,
                themeMode = themeMode,
                isBusy = isBusy,
                snackbarHostState = snackbarHostState,
                onToggleTheme = onToggleTheme,
                onSetThemeMode = onSetThemeMode,
                onSignOut = onSignOut,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun MainWorkspaceScaffold(
    profile: UserProfile,
    myTrades: List<TradeEntry>,
    myDiscipline: List<com.example.data.model.DisciplineEntry>,
    myStats: com.example.data.analytics.DashboardStats,
    allUsers: List<UserProfile>,
    allInvites: List<com.example.data.model.StudentInvite>,
    allAdminTrades: List<TradeEntry>,
    allAdminDiscipline: List<com.example.data.model.DisciplineEntry>,
    studentSummaries: List<com.example.data.analytics.StudentPerformanceSummary>,
    themeMode: ThemeMode,
    isBusy: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleTheme: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    viewModel: MainViewModel
) {
    val colors = LocalTradingColors.current
    val tabs = remember(profile.isAdminUser) {
        if (profile.isAdminUser) {
            listOf(AppTab.DASHBOARD, AppTab.JOURNAL, AppTab.STUDENTS, AppTab.DISCIPLINE, AppTab.REPORTS)
        } else {
            listOf(AppTab.DASHBOARD, AppTab.JOURNAL, AppTab.DISCIPLINE, AppTab.REPORTS)
        }
    }
    var selectedTab by remember(profile.isAdminUser) { mutableStateOf(AppTab.DASHBOARD) }
    var showTradeEditor by remember { mutableStateOf(false) }
    var editingTrade by remember { mutableStateOf<TradeEntry?>(null) }

    if (showTradeEditor) {
        TradeEntryScreen(
            existingTrade = editingTrade,
            ownerName = profile.name,
            isBusy = isBusy,
            onDismiss = {
                showTradeEditor = false
                editingTrade = null
            },
            onSave = { date, millis, inst, dir, tf, entry, exit, sl, tp, lot, risk, pnl, res, strat, entryR, exitR, mistakes, lessons, notes, shot ->
                viewModel.saveTrade(
                    existingTradeId = editingTrade?.id,
                    ownerUid = editingTrade?.userId ?: profile.userId,
                    ownerName = profile.name,
                    tradeDate = date,
                    tradeTimestampMillis = millis,
                    instrument = inst,
                    direction = dir,
                    timeframe = tf,
                    entryPrice = entry,
                    exitPrice = exit,
                    stopLoss = sl,
                    takeProfit = tp,
                    lotSize = lot,
                    riskAmount = risk,
                    profitLoss = pnl,
                    result = res,
                    strategy = strat,
                    entryReason = entryR,
                    exitReason = exitR,
                    mistakes = mistakes,
                    lessonsLearned = lessons,
                    notes = notes,
                    chartScreenshotBase64 = shot,
                    onDone = {
                        showTradeEditor = false
                        editingTrade = null
                    }
                )
            }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                AppTopHeader(
                    profile = profile,
                    themeMode = themeMode,
                    onToggleTheme = onToggleTheme,
                    onSignOut = onSignOut
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.gold,
                                    selectedTextColor = colors.gold,
                                    indicatorColor = colors.gold.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colors.gold,
                                    selectedTextColor = colors.gold,
                                    indicatorColor = colors.gold.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 1100.dp)
                            .fillMaxHeight()
                    ) {
                        when (selectedTab) {
                            AppTab.DASHBOARD -> DashboardScreen(
                                profile = profile,
                                stats = myStats,
                                onAddTradeClick = {
                                    editingTrade = null
                                    showTradeEditor = true
                                }
                            )
                            AppTab.JOURNAL -> JournalScreen(
                                trades = myTrades,
                                onAddTradeClick = {
                                    editingTrade = null
                                    showTradeEditor = true
                                },
                                onEditTradeClick = { trade ->
                                    editingTrade = trade
                                    showTradeEditor = true
                                },
                                onDeleteTradeClick = { id -> viewModel.deleteTrade(id) }
                            )
                            AppTab.STUDENTS -> AdminStudentsScreen(
                                summaries = studentSummaries,
                                invites = allInvites,
                                allTrades = allAdminTrades,
                                allDiscipline = allAdminDiscipline,
                                isBusy = isBusy,
                                onUpdateStudentAccount = { sUid, name, status, cap, goal, maxLoss, maxTrades, notes ->
                                    viewModel.adminUpdateStudentAccount(
                                        sUid, name, status, cap, goal, maxLoss, maxTrades, notes
                                    )
                                },
                                onSaveStudentInvite = { existingId, name, email, status, cap, goal, maxLoss, maxTrades, notes ->
                                    viewModel.adminSaveStudentInvite(
                                        existingId, name, email, status, cap, goal, maxLoss, maxTrades, notes
                                    )
                                },
                                onDeleteInvite = { id -> viewModel.adminDeleteStudentInvite(id) },
                                onNotice = { msg -> viewModel.emitNotice(msg) }
                            )
                            AppTab.DISCIPLINE -> DisciplineScreen(
                                profile = profile,
                                stats = myStats,
                                entries = myDiscipline,
                                isBusy = isBusy,
                                onSaveEntry = { existingId, type, period, mood, rules, sl, over, notes, mistakes, plan, score ->
                                    viewModel.saveDisciplineEntry(
                                        existingEntryId = existingId,
                                        ownerUid = profile.userId,
                                        ownerName = profile.name,
                                        entryType = type,
                                        periodLabel = period,
                                        moodTag = mood,
                                        followedRules = rules,
                                        respectedStopLoss = sl,
                                        overtraded = over,
                                        psychologyNotes = notes,
                                        mistakesSummary = mistakes,
                                        improvementsPlan = plan,
                                        disciplineScore = score
                                    )
                                },
                                onDeleteEntry = { id -> viewModel.deleteDisciplineEntry(id) },
                                onUpdateTargets = { name, cap, goal, maxLoss, maxTrades ->
                                    viewModel.updateMyRiskTargets(name, cap, goal, maxLoss, maxTrades)
                                }
                            )
                            AppTab.REPORTS -> ReportsScreen(
                                currentUser = profile,
                                myTrades = myTrades,
                                myDiscipline = myDiscipline,
                                allStudents = allUsers,
                                allInvites = allInvites,
                                allAdminTrades = allAdminTrades,
                                allAdminDiscipline = allAdminDiscipline,
                                themeMode = themeMode,
                                isBusy = isBusy,
                                onSetThemeMode = onSetThemeMode,
                                onUpdateTargets = { name, cap, goal, maxLoss, maxTrades ->
                                    viewModel.updateMyRiskTargets(name, cap, goal, maxLoss, maxTrades)
                                },
                                onNotice = { msg -> viewModel.emitNotice(msg) }
                            )
                        }
                    }
                }
            }
        }
    }
}
