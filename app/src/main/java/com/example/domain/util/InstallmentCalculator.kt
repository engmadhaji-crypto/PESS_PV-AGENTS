package com.example.domain.util

import com.example.domain.model.InstallmentPayment
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanStatus
import com.example.domain.model.SyncStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

object InstallmentCalculator {

    private val arabicLocale = Locale("ar", "SA")
    private val latinCurrencyFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun calculateFinancedTotal(
        totalCashPrice: Double,
        downPayment: Double,
        interestRatePercent: Double
    ): Double {
        val principal = (totalCashPrice - downPayment).coerceAtLeast(0.0)
        val interest = principal * (interestRatePercent.coerceAtLeast(0.0) / 100.0)
        return roundToTwoDecimals(principal + interest)
    }

    fun generateSchedule(
        planId: String,
        customerId: String,
        financedTotal: Double,
        installmentCount: Int,
        startDateMillis: Long,
        intervalMonths: Int = 1,
        nowMillis: Long = System.currentTimeMillis()
    ): List<InstallmentPayment> {
        val count = installmentCount.coerceAtLeast(1)
        val totalCents = (financedTotal * 100.0).roundToLong()
        val baseCents = totalCents / count
        val remainderCents = totalCents % count

        return (1..count).map { index ->
            val installmentCents = if (index == count) baseCents + remainderCents else baseCents
            val amountDue = installmentCents / 100.0
            val dueDate = addMonths(startDateMillis, (index - 1) * intervalMonths.coerceAtLeast(1))
            val initialStatus = if (dueDate < startOfDay(nowMillis)) {
                PaymentStatus.OVERDUE
            } else {
                PaymentStatus.PENDING
            }
            InstallmentPayment(
                id = UUID.randomUUID().toString(),
                planId = planId,
                customerId = customerId,
                installmentNumber = index,
                dueDateMillis = dueDate,
                amountDue = amountDue,
                amountPaid = 0.0,
                paidDateMillis = null,
                status = initialStatus,
                updatedAt = nowMillis,
                syncStatus = SyncStatus.PENDING_UPLOAD
            )
        }
    }

    fun resolvePlanStatus(payments: List<InstallmentPayment>, nowMillis: Long = System.currentTimeMillis()): PlanStatus {
        if (payments.isEmpty()) return PlanStatus.ACTIVE
        val allPaid = payments.all { it.status == PaymentStatus.PAID || it.remainingAmount <= 0.01 }
        if (allPaid) return PlanStatus.COMPLETED
        val dayStart = startOfDay(nowMillis)
        val hasOverdue = payments.any {
            it.status != PaymentStatus.PAID && it.remainingAmount > 0.01 && it.dueDateMillis < dayStart
        }
        return if (hasOverdue) PlanStatus.OVERDUE else PlanStatus.ACTIVE
    }

    fun resolvePaymentStatus(
        amountDue: Double,
        amountPaid: Double,
        dueDateMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): PaymentStatus {
        return when {
            amountPaid >= amountDue - 0.01 -> PaymentStatus.PAID
            dueDateMillis < startOfDay(nowMillis) -> PaymentStatus.OVERDUE
            amountPaid > 0.01 -> PaymentStatus.PARTIAL
            else -> PaymentStatus.PENDING
        }
    }

    fun addMonths(baseMillis: Long, monthsToAdd: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = baseMillis
            add(Calendar.MONTH, monthsToAdd)
        }
        return cal.timeInMillis
    }

    fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDaysAhead(millis: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDay(millis)
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun roundToTwoDecimals(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    fun formatCurrency(amount: Double, currency: String = "ر.س"): String {
        return "${latinCurrencyFormat.format(amount)} $currency"
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "-"
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "لم تتم بعد"
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }
}
