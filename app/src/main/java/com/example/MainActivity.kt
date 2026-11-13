package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.firebase.FirestoreInstallmentDataSource
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.InstallmentRepositoryImpl
import com.example.data.sync.authStateFlow
import com.example.ui.components.AddCustomerDialog
import com.example.ui.navigation.AiAssistantRoute
import com.example.ui.navigation.CustomerDetailRoute
import com.example.ui.navigation.CustomersRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.PlanDetailRoute
import com.example.ui.navigation.SyncSettingsRoute
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PlanDetailScreen
import com.example.ui.screens.SyncSettingsScreen
import com.example.ui.theme.InstallmentManagerTheme
import com.example.ui.viewmodel.InstallmentViewModel
import com.example.ui.viewmodel.PendingSyncAction
import com.example.ui.viewmodel.UiEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent before any UI or Firebase calls.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val app = application as InstallmentApp

        setContent {
            InstallmentManagerTheme {
                val auth = remember { Firebase.auth }
                val currentUser by auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = auth.currentUser)

                val user = currentUser
                if (user == null) {
                    AuthScreen(
                        activity = this@MainActivity,
                        authManager = app.googleAuthManager
                    )
                } else {
                    key(user.uid) {
                        val viewModel: InstallmentViewModel = viewModel(
                            key = user.uid,
                            factory = viewModelFactory {
                                initializer {
                                    val application = checkNotNull(this[APPLICATION_KEY]) as InstallmentApp
                                    val databaseId = application.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(databaseId)
                                    val repo = InstallmentRepositoryImpl(
                                        database = application.database,
                                        networkMonitor = application.networkMonitor,
                                        sheetsSyncManager = application.sheetsSyncManager,
                                        firestoreDataSource = FirestoreInstallmentDataSource(db)
                                    )
                                    InstallmentViewModel(
                                        repository = repo,
                                        authManager = application.googleAuthManager,
                                        pdfGenerator = application.pdfInvoiceGenerator,
                                        aiAssistant = application.geminiAssistant,
                                        currentUserId = user.uid,
                                        currentUserEmail = user.email
                                    )
                                }
                            }
                        )
                        InstallmentMainApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

private data class TopLevelDestination<T : Any>(
    val route: T,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun InstallmentMainApp(viewModel: InstallmentViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var pendingConsentAction by remember { mutableStateOf(PendingSyncAction.PUSH) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (activity != null && result.resultCode == Activity.RESULT_OK) {
            viewModel.handleConsentActivityResult(activity, result.data, pendingConsentAction)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.LaunchAuthorizationConsent -> {
                    pendingConsentAction = event.pendingAction
                    val request = IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                    consentLauncher.launch(request)
                }
                is UiEvent.OpenPdfUri -> {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(event.uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        putExtra(Intent.EXTRA_SUBJECT, "كشف حساب - ${event.customerName}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "تصدير أو مشاركة كشف الحساب (PDF)").apply {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(viewIntent))
                    }
                    runCatching { context.startActivity(chooser) }
                        .onFailure {
                            snackbarHostState.showSnackbar("تم حفظ ملف PDF بنجاح")
                        }
                }
            }
        }
    }

    val destinations = remember {
        listOf(
            TopLevelDestination(
                route = DashboardRoute,
                labelRes = R.string.nav_dashboard,
                selectedIcon = Icons.Rounded.Dashboard,
                unselectedIcon = Icons.Outlined.Dashboard,
                testTag = "nav_dashboard"
            ),
            TopLevelDestination(
                route = CustomersRoute,
                labelRes = R.string.nav_customers,
                selectedIcon = Icons.Rounded.Group,
                unselectedIcon = Icons.Outlined.Group,
                testTag = "nav_customers"
            ),
            TopLevelDestination(
                route = AiAssistantRoute,
                labelRes = R.string.nav_ai,
                selectedIcon = Icons.Rounded.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                testTag = "nav_ai"
            ),
            TopLevelDestination(
                route = SyncSettingsRoute,
                labelRes = R.string.nav_sync,
                selectedIcon = Icons.Rounded.CloudSync,
                unselectedIcon = Icons.Outlined.CloudSync,
                testTag = "nav_sync"
            )
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevel = currentDestination == null || destinations.any { dest ->
        currentDestination.hasRoute(dest.route::class)
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevel && !useNavRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
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
                if (isTopLevel && useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute
                    ) {
                        composable<DashboardRoute> {
                            val state by viewModel.dashboardUiState.collectAsStateWithLifecycle()
                            DashboardScreen(
                                uiState = state,
                                onAddCustomerClick = { showAddCustomerDialog = true },
                                onSyncNowClick = viewModel::syncWithFirestoreNow,
                                onOpenSyncSettings = { navigateTopLevel(SyncSettingsRoute) },
                                onOpenCustomerDetail = { customerId ->
                                    navController.navigate(CustomerDetailRoute(customerId))
                                },
                                onRecordPayment = viewModel::recordPayment
                            )
                        }

                        composable<CustomersRoute> {
                            val state by viewModel.customersUiState.collectAsStateWithLifecycle()
                            CustomersScreen(
                                uiState = state,
                                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                                onFilterSelected = viewModel::onFilterSelected,
                                onCustomerClick = { customerId ->
                                    navController.navigate(CustomerDetailRoute(customerId))
                                },
                                onAddCustomerClick = { showAddCustomerDialog = true }
                            )
                        }

                        composable<AiAssistantRoute> {
                            val aiState by viewModel.aiState.collectAsStateWithLifecycle()
                            val dashState by viewModel.dashboardUiState.collectAsStateWithLifecycle()
                            AiAssistantScreen(
                                aiState = aiState,
                                dueAlerts = dashState.dueAlerts,
                                onAnalyzePortfolio = viewModel::analyzePortfolioWithAi,
                                onDraftReminder = viewModel::draftReminderWithAi,
                                onAskQuestion = viewModel::askAiQuestion,
                                onResetAiState = viewModel::resetAiState
                            )
                        }

                        composable<CustomerDetailRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<CustomerDetailRoute>()
                            val customer by viewModel.observeCustomer(route.customerId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val plans by viewModel.observeCustomerPlans(route.customerId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            CustomerDetailScreen(
                                customer = customer,
                                plans = plans,
                                onBack = { navController.popBackStack() },
                                onExportPdf = { viewModel.exportCustomerStatementPdf(route.customerId) },
                                onCreatePlan = { itemName, total, down, rate, count, interval, notes ->
                                    viewModel.createPlan(
                                        customerId = route.customerId,
                                        itemName = itemName,
                                        totalCashPrice = total,
                                        downPayment = down,
                                        interestRatePercent = rate,
                                        installmentCount = count,
                                        intervalMonths = interval,
                                        startDateMillis = System.currentTimeMillis(),
                                        notes = notes
                                    )
                                },
                                onOpenPlanDetail = { planId ->
                                    navController.navigate(PlanDetailRoute(planId))
                                },
                                onDeleteCustomer = {
                                    viewModel.deleteCustomer(route.customerId)
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable<PlanDetailRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<PlanDetailRoute>()
                            val bundle by viewModel.observePlan(route.planId)
                                .collectAsStateWithLifecycle(initialValue = null)

                            PlanDetailScreen(
                                bundle = bundle,
                                onBack = { navController.popBackStack() },
                                onRecordPayment = viewModel::recordPayment
                            )
                        }

                        composable<SyncSettingsRoute> {
                            val state by viewModel.syncUiState.collectAsStateWithLifecycle()
                            val pendingBackup by viewModel.pendingBackupImport.collectAsStateWithLifecycle()
                            val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
                            SyncSettingsScreen(
                                uiState = state,
                                pendingBackupImport = pendingBackup,
                                isBackupBusy = isBackupBusy,
                                onSignInGoogle = {
                                    activity?.let { viewModel.signInWithGoogle(it) }
                                },
                                onSignOutGoogle = viewModel::signOutGoogle,
                                onSyncFirestore = viewModel::syncWithFirestoreNow,
                                onPushSync = {
                                    activity?.let { viewModel.triggerSync(it, PendingSyncAction.PUSH) }
                                },
                                onPullSync = {
                                    activity?.let { viewModel.triggerSync(it, PendingSyncAction.PULL) }
                                },
                                onUpdateSpreadsheetId = viewModel::updateSpreadsheetId,
                                onExportDatabaseUri = { uri ->
                                    viewModel.exportDatabaseToUri(context, uri)
                                },
                                onImportDatabaseUri = { uri ->
                                    viewModel.prepareImportDatabaseFromUri(context, uri)
                                },
                                onConfirmImportDatabase = viewModel::confirmImportDatabase,
                                onDismissImportDatabase = viewModel::dismissImportBackupDialog
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { fullName, phone, nationalId, address, notes, itemName, total, down, rate, count ->
                viewModel.addCustomerWithOptionalPlan(
                    fullName = fullName,
                    phone = phone,
                    nationalId = nationalId,
                    address = address,
                    notes = notes,
                    itemName = itemName,
                    totalCashPrice = total,
                    downPayment = down,
                    interestRatePercent = rate,
                    installmentCount = count,
                    startDateMillis = System.currentTimeMillis()
                )
                showAddCustomerDialog = false
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
