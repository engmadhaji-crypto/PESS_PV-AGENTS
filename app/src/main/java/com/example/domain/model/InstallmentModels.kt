package com.example.domain.model

enum class SyncStatus(val labelAr: String) {
    SYNCED("متزامن"),
    PENDING_UPLOAD("بانتظار المزامنة"),
    PENDING_DELETE("معلّق للحذف")
}

enum class PlanStatus(val labelAr: String) {
    ACTIVE("نشط"),
    COMPLETED("مكتمل"),
    OVERDUE("متأخر")
}

enum class PaymentStatus(val labelAr: String) {
    PENDING("قادم"),
    PAID("مدفوع"),
    PARTIAL("جزئي"),
    OVERDUE("متأخر")
}

enum class PaymentMethod(val labelAr: String) {
    CASH("نقدي"),
    BANK_TRANSFER("تحويل بنكي"),
    CARD("بطاقة")
}

data class Customer(
    val id: String,
    val fullName: String,
    val phone: String,
    val nationalId: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD
)

data class InstallmentPlan(
    val id: String,
    val customerId: String,
    val itemName: String,
    val totalCashPrice: Double,
    val downPayment: Double,
    val interestRatePercent: Double,
    val financedTotal: Double,
    val installmentCount: Int,
    val intervalMonths: Int = 1,
    val startDateMillis: Long,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD
)

data class InstallmentPayment(
    val id: String,
    val planId: String,
    val customerId: String,
    val installmentNumber: Int,
    val dueDateMillis: Long,
    val amountDue: Double,
    val amountPaid: Double = 0.0,
    val paidDateMillis: Long? = null,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val receiptNumber: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD
) {
    val remainingAmount: Double
        get() = (amountDue - amountPaid).coerceAtLeast(0.0)
}

data class CustomerWithSummary(
    val customer: Customer,
    val activePlansCount: Int,
    val totalFinanced: Double,
    val totalPaid: Double,
    val totalRemaining: Double,
    val overdueCount: Int,
    val nextDueDateMillis: Long?
)

data class PlanWithPayments(
    val plan: InstallmentPlan,
    val customer: Customer?,
    val payments: List<InstallmentPayment>
) {
    val totalPaid: Double
        get() = plan.downPayment + payments.sumOf { it.amountPaid }

    val totalRemaining: Double
        get() = payments.sumOf { it.remainingAmount }

    val paidInstallmentsCount: Int
        get() = payments.count { it.status == PaymentStatus.PAID }

    val overdueInstallmentsCount: Int
        get() = payments.count { it.status == PaymentStatus.OVERDUE }
}

data class DueAlertItem(
    val payment: InstallmentPayment,
    val customerName: String,
    val customerPhone: String,
    val itemName: String,
    val isOverdue: Boolean
)

data class DashboardStats(
    val totalPortfolio: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalRemaining: Double = 0.0,
    val collectionRate: Float = 0f,
    val activePlansCount: Int = 0,
    val completedPlansCount: Int = 0,
    val overdueCount: Int = 0,
    val dueSoonCount: Int = 0,
    val customerCount: Int = 0,
    val pendingSyncCount: Int = 0
)

data class SyncMetadata(
    val accountEmail: String = "",
    val spreadsheetId: String = "",
    val spreadsheetTitle: String = "نظام الأقساط - سجل البيانات",
    val lastSyncTimestamp: Long = 0L,
    val lastSyncMessage: String = "لم تتم المزامنة بعد"
)

sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val statusMessage: String) : SyncState
    data class Success(val syncedRecords: Int, val timestamp: Long) : SyncState
    data class OfflineQueued(val pendingCount: Int) : SyncState
    data class AuthRequired(val reason: String) : SyncState
    data class Error(val message: String) : SyncState
}
