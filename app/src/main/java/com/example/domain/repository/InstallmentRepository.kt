package com.example.domain.repository

import com.example.domain.model.Customer
import com.example.domain.model.CustomerWithSummary
import com.example.domain.model.DashboardStats
import com.example.domain.model.DatabaseBackupPreview
import com.example.domain.model.DatabaseBackupSummary
import com.example.domain.model.DueAlertItem
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PlanWithPayments
import com.example.domain.model.SyncMetadata
import com.example.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InstallmentRepository {
    val isOnline: StateFlow<Boolean>
    val syncState: StateFlow<SyncState>
    val syncMetadata: Flow<SyncMetadata>

    fun observeDashboardStats(): Flow<DashboardStats>
    fun observeDueAlerts(): Flow<List<DueAlertItem>>
    fun observeCustomersWithSummary(): Flow<List<CustomerWithSummary>>
    fun observeCustomerById(customerId: String): Flow<Customer?>
    fun observePlansForCustomer(customerId: String): Flow<List<PlanWithPayments>>
    fun observePlanWithPayments(planId: String): Flow<PlanWithPayments?>

    suspend fun setAuthenticatedUser(userId: String?, email: String?)

    suspend fun addCustomerWithOptionalPlan(
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
    ): Result<String>

    suspend fun createInstallmentPlan(
        customerId: String,
        itemName: String,
        totalCashPrice: Double,
        downPayment: Double,
        interestRatePercent: Double,
        installmentCount: Int,
        intervalMonths: Int,
        startDateMillis: Long,
        notes: String
    ): Result<String>

    suspend fun recordPayment(
        paymentId: String,
        paidAmount: Double,
        paymentMethod: PaymentMethod,
        receiptNumber: String,
        notes: String
    ): Result<Unit>

    suspend fun deleteCustomer(customerId: String): Result<Unit>

    suspend fun refreshOverdueStatuses(): Result<Unit>

    suspend fun syncWithFirestore(userId: String): Result<Int>
    fun observeFirestoreRealtime(userId: String): Flow<Result<Int>>

    suspend fun updateConnectedAccount(email: String)
    suspend fun updateSpreadsheetId(spreadsheetId: String)
    suspend fun syncWithGoogleSheets(accessToken: String): Result<Int>
    suspend fun pullFromGoogleSheets(accessToken: String): Result<Int>
    suspend fun exportDatabaseBackup(): Result<Pair<String, DatabaseBackupSummary>>
    suspend fun previewDatabaseBackup(rawJson: String): Result<DatabaseBackupPreview>
    suspend fun importDatabaseBackup(rawJson: String, replaceExisting: Boolean): Result<DatabaseBackupSummary>
    fun markOfflineQueued(pendingCount: Int)
    fun resetSyncState()
}
