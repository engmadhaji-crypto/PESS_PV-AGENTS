package com.example.ui.viewmodel

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiStreamState
import com.example.data.ai.GeminiInstallmentAssistant
import com.example.data.ai.ReminderTone
import com.example.data.local.backup.DatabaseBackupManager
import com.example.data.pdf.PdfInvoiceGenerator
import com.example.data.sync.AuthTokenResult
import com.example.data.sync.GoogleAuthManager
import com.example.data.sync.GoogleSignInResult
import com.example.domain.model.Customer
import com.example.domain.model.CustomerWithSummary
import com.example.domain.model.DashboardStats
import com.example.domain.model.DatabaseBackupPreview
import com.example.domain.model.DueAlertItem
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PlanWithPayments
import com.example.domain.model.SyncMetadata
import com.example.domain.model.SyncState
import com.example.domain.repository.InstallmentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CustomerFilter(val labelAr: String) {
    ALL("الكل"),
    OVERDUE("متأخرون"),
    ACTIVE("نشطون"),
    COMPLETED("مكتمل")
}

enum class PendingSyncAction {
    PUSH,
    PULL
}

data class DashboardUiState(
    val stats: DashboardStats = DashboardStats(),
    val dueAlerts: List<DueAlertItem> = emptyList(),
    val isOnline: Boolean = true,
    val syncState: SyncState = SyncState.Idle
)

data class CustomersUiState(
    val customers: List<CustomerWithSummary> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: CustomerFilter = CustomerFilter.ALL,
    val totalCount: Int = 0
)

data class SyncUiState(
    val isOnline: Boolean = true,
    val syncState: SyncState = SyncState.Idle,
    val metadata: SyncMetadata = SyncMetadata(),
    val pendingCount: Int = 0
)

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data class OpenPdfUri(val uri: Uri, val customerName: String) : UiEvent
    data class LaunchAuthorizationConsent(
        val pendingIntent: PendingIntent,
        val pendingAction: PendingSyncAction
    ) : UiEvent
}

class InstallmentViewModel(
    private val repository: InstallmentRepository,
    private val authManager: GoogleAuthManager,
    private val pdfGenerator: PdfInvoiceGenerator,
    private val aiAssistant: GeminiInstallmentAssistant,
    private val currentUserId: String,
    private val currentUserEmail: String?
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(CustomerFilter.ALL)

    private val _aiState = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val aiState: StateFlow<AiStreamState> = _aiState.asStateFlow()

    private val _pendingBackupImport = MutableStateFlow<DatabaseBackupPreview?>(null)
    val pendingBackupImport: StateFlow<DatabaseBackupPreview?> = _pendingBackupImport.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val backupManager = DatabaseBackupManager()

    private var aiJob: Job? = null

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        repository.observeDashboardStats(),
        repository.observeDueAlerts(),
        repository.isOnline,
        repository.syncState
    ) { stats, alerts, isOnline, syncState ->
        DashboardUiState(
            stats = stats,
            dueAlerts = alerts,
            isOnline = isOnline,
            syncState = syncState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    val customersUiState: StateFlow<CustomersUiState> = combine(
        repository.observeCustomersWithSummary(),
        searchQuery,
        selectedFilter
    ) { allCustomers, query, filter ->
        val filteredByStatus = when (filter) {
            CustomerFilter.ALL -> allCustomers
            CustomerFilter.OVERDUE -> allCustomers.filter { it.overdueCount > 0 }
            CustomerFilter.ACTIVE -> allCustomers.filter { it.totalRemaining > 0.01 }
            CustomerFilter.COMPLETED -> allCustomers.filter {
                it.totalFinanced > 0.0 && it.totalRemaining <= 0.01
            }
        }
        val cleanQuery = query.trim().lowercase()
        val finalCustomers = if (cleanQuery.isBlank()) {
            filteredByStatus
        } else {
            filteredByStatus.filter {
                it.customer.fullName.lowercase().contains(cleanQuery) ||
                    it.customer.phone.lowercase().contains(cleanQuery) ||
                    it.customer.nationalId.lowercase().contains(cleanQuery)
            }
        }
        CustomersUiState(
            customers = finalCustomers,
            searchQuery = query,
            selectedFilter = filter,
            totalCount = allCustomers.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CustomersUiState()
    )

    val syncUiState: StateFlow<SyncUiState> = combine(
        repository.isOnline,
        repository.syncState,
        repository.syncMetadata,
        repository.observeDashboardStats()
    ) { isOnline, syncState, metadata, stats ->
        SyncUiState(
            isOnline = isOnline,
            syncState = syncState,
            metadata = metadata,
            pendingCount = stats.pendingSyncCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncUiState()
    )

    init {
        viewModelScope.launch {
            repository.setAuthenticatedUser(currentUserId, currentUserEmail)
            repository.refreshOverdueStatuses()
            repository.syncWithFirestore(currentUserId)
        }
        viewModelScope.launch {
            repository.observeFirestoreRealtime(currentUserId).collect { result ->
                result.onFailure { err ->
                    Log.w(TAG, "Realtime Firestore observation error", err)
                }
            }
        }
    }

    fun observeCustomer(customerId: String): Flow<Customer?> =
        repository.observeCustomerById(customerId)

    fun observeCustomerPlans(customerId: String): Flow<List<PlanWithPayments>> =
        repository.observePlansForCustomer(customerId)

    fun observePlan(planId: String): Flow<PlanWithPayments?> =
        repository.observePlanWithPayments(planId)

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onFilterSelected(filter: CustomerFilter) {
        selectedFilter.value = filter
    }

    fun addCustomerWithOptionalPlan(
        fullName: String,
        phone: String,
        nationalId: String,
        address: String,
        notes: String,
        itemName: String?,
        totalCashPrice: Double?,
        downPayment: Double?,
        interestRatePercent: Double?,
        installmentCount: Int?,
        startDateMillis: Long?
    ) {
        viewModelScope.launch {
            val result = repository.addCustomerWithOptionalPlan(
                fullName = fullName,
                phone = phone,
                nationalId = nationalId,
                address = address,
                notes = notes,
                itemName = itemName,
                totalCashPrice = totalCashPrice,
                downPayment = downPayment,
                interestRatePercent = interestRatePercent,
                installmentCount = installmentCount,
                startDateMillis = startDateMillis
            )
            result.onSuccess {
                _events.emit(UiEvent.ShowMessage("تم حفظ بيانات العميل بنجاح"))
            }.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.message ?: "تعذّر حفظ العميل"))
            }
        }
    }

    fun createPlan(
        customerId: String,
        itemName: String,
        totalCashPrice: Double,
        downPayment: Double,
        interestRatePercent: Double,
        installmentCount: Int,
        intervalMonths: Int,
        startDateMillis: Long,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.createInstallmentPlan(
                customerId = customerId,
                itemName = itemName,
                totalCashPrice = totalCashPrice,
                downPayment = downPayment,
                interestRatePercent = interestRatePercent,
                installmentCount = installmentCount,
                intervalMonths = intervalMonths,
                startDateMillis = startDateMillis,
                notes = notes
            )
            result.onSuccess {
                _events.emit(UiEvent.ShowMessage("تم إنشاء خطة التقسيط بنجاح"))
            }.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.message ?: "تعذّر إنشاء الخطة"))
            }
        }
    }

    fun recordPayment(
        paymentId: String,
        paidAmount: Double,
        paymentMethod: PaymentMethod,
        receiptNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.recordPayment(
                paymentId = paymentId,
                paidAmount = paidAmount,
                paymentMethod = paymentMethod,
                receiptNumber = receiptNumber,
                notes = notes
            )
            result.onSuccess {
                _events.emit(UiEvent.ShowMessage("تم تسجيل الدفعة بنجاح"))
            }.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.message ?: "تعذّر تسجيل الدفعة"))
            }
        }
    }

    fun deleteCustomer(customerId: String) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId)
                .onSuccess { _events.emit(UiEvent.ShowMessage("تم حذف ملف العميل")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.message ?: "فشل الحذف")) }
        }
    }

    fun exportCustomerStatementPdf(customerId: String) {
        viewModelScope.launch {
            val customer = repository.observeCustomerById(customerId).first()
            if (customer == null) {
                _events.emit(UiEvent.ShowMessage("العميل غير موجود"))
                return@launch
            }
            val plans = repository.observePlansForCustomer(customerId).first()
            pdfGenerator.generateCustomerStatementPdf(customer, plans)
                .onSuccess { uri ->
                    _events.emit(UiEvent.OpenPdfUri(uri, customer.fullName))
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ShowMessage("تعذّر إنشاء ملف PDF: ${err.message}"))
                }
        }
    }

    fun analyzePortfolioWithAi() {
        val state = dashboardUiState.value
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            aiAssistant.analyzePortfolio(state.stats, state.dueAlerts).collect {
                _aiState.value = it
            }
        }
    }

    fun draftReminderWithAi(alert: DueAlertItem, tone: ReminderTone) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            aiAssistant.draftPaymentReminder(alert, tone).collect {
                _aiState.value = it
            }
        }
    }

    fun askAiQuestion(question: String) {
        val stats = dashboardUiState.value.stats
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            aiAssistant.askFinancialQuestion(question, stats).collect {
                _aiState.value = it
            }
        }
    }

    fun resetAiState() {
        aiJob?.cancel()
        _aiState.value = AiStreamState.Idle
    }

    fun syncWithFirestoreNow() {
        viewModelScope.launch {
            repository.syncWithFirestore(currentUserId)
                .onSuccess { count ->
                    _events.emit(UiEvent.ShowMessage("تمت مزامنة $count سجل مع Firebase Firestore"))
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ShowMessage(err.message ?: "تعذّرت مزامنة Firestore"))
                }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            when (val res = authManager.signInWithGoogle(activity)) {
                is GoogleSignInResult.Success -> {
                    repository.setAuthenticatedUser(res.uid, res.email)
                    _events.emit(UiEvent.ShowMessage("تم الربط بحساب ${res.email}"))
                }
                is GoogleSignInResult.Cancelled -> {
                    _events.emit(UiEvent.ShowMessage(res.reason))
                }
                is GoogleSignInResult.Failed -> {
                    _events.emit(UiEvent.ShowMessage(res.message))
                }
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            authManager.signOut()
            repository.setAuthenticatedUser(null, null)
            repository.resetSyncState()
        }
    }

    fun exportDatabaseToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val (jsonContent, summary) = repository.exportDatabaseBackup().getOrThrow()
                backupManager.writeToUri(context, uri, jsonContent).getOrThrow()
                _events.emit(
                    UiEvent.ShowMessage(
                        "تم تصدير قاعدة البيانات (${summary.customerCount} عميل، ${summary.planCount} عقد، ${summary.installmentCount} قسط)"
                    )
                )
            } catch (err: Exception) {
                _events.emit(UiEvent.ShowMessage(err.message ?: "تعذّر تصدير قاعدة البيانات"))
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun prepareImportDatabaseFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val rawJson = backupManager.readFromUri(context, uri).getOrThrow()
                val preview = repository.previewDatabaseBackup(rawJson).getOrThrow()
                _pendingBackupImport.value = preview
            } catch (err: Exception) {
                _events.emit(UiEvent.ShowMessage(err.message ?: "ملف النسخة الاحتياطية غير صالح"))
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun confirmImportDatabase(replaceExisting: Boolean) {
        val preview = _pendingBackupImport.value ?: return
        viewModelScope.launch {
            _isBackupBusy.value = true
            _pendingBackupImport.value = null
            try {
                val summary = repository.importDatabaseBackup(
                    rawJson = preview.rawJson,
                    replaceExisting = replaceExisting
                ).getOrThrow()
                _events.emit(
                    UiEvent.ShowMessage(
                        "تم استيراد قاعدة البيانات بنجاح (${summary.customerCount} عميل، ${summary.planCount} عقد، ${summary.installmentCount} قسط)"
                    )
                )
            } catch (err: Exception) {
                _events.emit(UiEvent.ShowMessage(err.message ?: "تعذّر استيراد قاعدة البيانات"))
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun dismissImportBackupDialog() {
        _pendingBackupImport.value = null
    }

    fun updateSpreadsheetId(spreadsheetId: String) {
        viewModelScope.launch {
            repository.updateSpreadsheetId(spreadsheetId)
            _events.emit(UiEvent.ShowMessage("تم تحديث معرّف جدول البيانات"))
        }
    }

    fun triggerSync(activity: Activity, action: PendingSyncAction) {
        viewModelScope.launch {
            if (!repository.isOnline.value) {
                val pending = dashboardUiState.value.stats.pendingSyncCount
                repository.markOfflineQueued(pending)
                _events.emit(
                    UiEvent.ShowMessage("لا يوجد اتصال بالإنترنت. التغييرات محفوظة محلياً للمزامنة لاحقاً.")
                )
                return@launch
            }

            when (val authResult = authManager.authorizeSheetsAndDrive(activity)) {
                is AuthTokenResult.Authorized -> {
                    executeSyncWithToken(authResult.accessToken, action)
                }
                is AuthTokenResult.NeedsConsent -> {
                    _events.emit(
                        UiEvent.LaunchAuthorizationConsent(
                            pendingIntent = authResult.pendingIntent,
                            pendingAction = action
                        )
                    )
                }
                is AuthTokenResult.Failed -> {
                    _events.emit(UiEvent.ShowMessage(authResult.message))
                }
            }
        }
    }

    fun handleConsentActivityResult(
        activity: Activity,
        data: Intent?,
        pendingAction: PendingSyncAction
    ) {
        viewModelScope.launch {
            when (val result = authManager.extractTokenFromConsentResult(activity, data)) {
                is AuthTokenResult.Authorized -> {
                    executeSyncWithToken(result.accessToken, pendingAction)
                }
                is AuthTokenResult.Failed -> {
                    _events.emit(UiEvent.ShowMessage(result.message))
                }
                is AuthTokenResult.NeedsConsent -> Unit
            }
        }
    }

    private suspend fun executeSyncWithToken(token: String, action: PendingSyncAction) {
        val result = when (action) {
            PendingSyncAction.PUSH -> repository.syncWithGoogleSheets(token)
            PendingSyncAction.PULL -> repository.pullFromGoogleSheets(token)
        }
        result.onSuccess { count ->
            val msg = if (action == PendingSyncAction.PUSH) {
                "تمت مزامنة $count سجل مع Google Sheets"
            } else {
                "تم استيراد $count سجل من Google Sheets"
            }
            _events.emit(UiEvent.ShowMessage(msg))
        }.onFailure { err ->
            if (err is com.example.data.sync.AuthExpiredException) {
                authManager.invalidateCachedToken()
            }
            _events.emit(UiEvent.ShowMessage(err.message ?: "تعذّرت المزامنة"))
        }
    }

    class Factory(
        private val repository: InstallmentRepository,
        private val authManager: GoogleAuthManager,
        private val pdfGenerator: PdfInvoiceGenerator,
        private val aiAssistant: GeminiInstallmentAssistant,
        private val currentUserId: String,
        private val currentUserEmail: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InstallmentViewModel(
                repository = repository,
                authManager = authManager,
                pdfGenerator = pdfGenerator,
                aiAssistant = aiAssistant,
                currentUserId = currentUserId,
                currentUserEmail = currentUserEmail
            ) as T
        }
    }

    private companion object {
        const val TAG = "InstallmentVM"
    }
}
