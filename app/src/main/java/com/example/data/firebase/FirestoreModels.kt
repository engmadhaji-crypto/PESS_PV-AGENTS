package com.example.data.firebase

import com.example.domain.model.Customer
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.InstallmentPlan
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanStatus
import com.example.domain.model.SyncStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CustomerFirestoreDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val phone: String = "",
    val nationalId: String = "",
    val address: String = "",
    val notes: String = "",
    val clientCreatedAtMillis: Long = 0L,
    val clientUpdatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InstallmentPlanFirestoreDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerId: String = "",
    val itemName: String = "",
    val totalCashPrice: Double = 0.0,
    val downPayment: Double = 0.0,
    val interestRatePercent: Double = 0.0,
    val financedTotal: Double = 0.0,
    val installmentCount: Int = 1,
    val intervalMonths: Int = 1,
    val startDateMillis: Long = 0L,
    val status: String = PlanStatus.ACTIVE.name,
    val notes: String = "",
    val clientCreatedAtMillis: Long = 0L,
    val clientUpdatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InstallmentFirestoreDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val planId: String = "",
    val customerId: String = "",
    val installmentNumber: Int = 1,
    val dueDateMillis: Long = 0L,
    val amountDue: Double = 0.0,
    val amountPaid: Double = 0.0,
    val paidDateMillis: Long? = null,
    val status: String = PaymentStatus.PENDING.name,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val receiptNumber: String = "",
    val notes: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Suppress("UNCHECKED_CAST")
fun Customer.toFirestoreCreateMap(userId: String): Map<String, Any> = mapOf(
    "userId" to userId,
    "fullName" to fullName.take(120),
    "phone" to phone.take(40),
    "nationalId" to nationalId.take(40),
    "address" to address.take(250),
    "notes" to notes.take(1000),
    "clientCreatedAtMillis" to createdAt.coerceAtLeast(0L),
    "clientUpdatedAtMillis" to updatedAt.coerceAtLeast(0L),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
).filterValues { it != null } as Map<String, Any>

@Suppress("UNCHECKED_CAST")
fun Customer.toFirestoreUpdateMap(): Map<String, Any> = mapOf(
    "fullName" to fullName.take(120),
    "phone" to phone.take(40),
    "nationalId" to nationalId.take(40),
    "address" to address.take(250),
    "notes" to notes.take(1000),
    "clientUpdatedAtMillis" to updatedAt.coerceAtLeast(0L),
    "updatedAt" to FieldValue.serverTimestamp()
).filterValues { it != null } as Map<String, Any>

@Suppress("UNCHECKED_CAST")
fun InstallmentPlan.toFirestoreCreateMap(userId: String): Map<String, Any> = mapOf(
    "userId" to userId,
    "customerId" to customerId,
    "itemName" to itemName.take(150),
    "totalCashPrice" to totalCashPrice,
    "downPayment" to downPayment,
    "interestRatePercent" to interestRatePercent,
    "financedTotal" to financedTotal,
    "installmentCount" to installmentCount,
    "intervalMonths" to intervalMonths,
    "startDateMillis" to startDateMillis.coerceAtLeast(0L),
    "status" to status.name,
    "notes" to notes.take(1000),
    "clientCreatedAtMillis" to createdAt.coerceAtLeast(0L),
    "clientUpdatedAtMillis" to updatedAt.coerceAtLeast(0L),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
).filterValues { it != null } as Map<String, Any>

@Suppress("UNCHECKED_CAST")
fun InstallmentPlan.toFirestoreUpdateMap(includeStatus: Boolean = true): Map<String, Any> = buildMap {
    if (includeStatus) {
        put("status", status.name)
    }
    put("notes", notes.take(1000))
    put("clientUpdatedAtMillis", updatedAt.coerceAtLeast(0L))
    put("updatedAt", FieldValue.serverTimestamp())
}.filterValues { it != null } as Map<String, Any>

@Suppress("UNCHECKED_CAST")
fun InstallmentPayment.toFirestoreCreateMap(userId: String): Map<String, Any> = mapOf(
    "userId" to userId,
    "planId" to planId,
    "customerId" to customerId,
    "installmentNumber" to installmentNumber,
    "dueDateMillis" to dueDateMillis.coerceAtLeast(0L),
    "amountDue" to amountDue,
    "amountPaid" to amountPaid,
    "paidDateMillis" to paidDateMillis?.coerceAtLeast(0L),
    "status" to status.name,
    "paymentMethod" to paymentMethod.name,
    "receiptNumber" to receiptNumber.take(80),
    "notes" to notes.take(1000),
    "clientUpdatedAtMillis" to updatedAt.coerceAtLeast(0L),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
).filterValues { it != null } as Map<String, Any>

@Suppress("UNCHECKED_CAST")
fun InstallmentPayment.toFirestorePaymentUpdateMap(): Map<String, Any> = mapOf(
    "amountPaid" to amountPaid,
    "paidDateMillis" to paidDateMillis?.coerceAtLeast(0L),
    "status" to status.name,
    "paymentMethod" to paymentMethod.name,
    "receiptNumber" to receiptNumber.take(80),
    "notes" to notes.take(1000),
    "clientUpdatedAtMillis" to updatedAt.coerceAtLeast(0L),
    "updatedAt" to FieldValue.serverTimestamp()
).filterValues { it != null } as Map<String, Any>

@Suppress("UNCHECKED_CAST")
fun InstallmentPayment.toFirestoreOverdueUpdateMap(): Map<String, Any> = mapOf(
    "status" to PaymentStatus.OVERDUE.name,
    "clientUpdatedAtMillis" to updatedAt.coerceAtLeast(0L),
    "updatedAt" to FieldValue.serverTimestamp()
).filterValues { it != null } as Map<String, Any>

fun CustomerFirestoreDoc.toDomain(): Customer {
    val safeUpdated = (updatedAt ?: createdAt)?.toDate()?.time ?: clientUpdatedAtMillis
    val safeCreated = createdAt?.toDate()?.time ?: clientCreatedAtMillis
    return Customer(
        id = id,
        fullName = fullName,
        phone = phone,
        nationalId = nationalId,
        address = address,
        notes = notes,
        createdAt = if (clientCreatedAtMillis > 0L) clientCreatedAtMillis else safeCreated,
        updatedAt = if (clientUpdatedAtMillis > 0L) clientUpdatedAtMillis else safeUpdated,
        syncStatus = SyncStatus.SYNCED
    )
}

fun InstallmentPlanFirestoreDoc.toDomain(): InstallmentPlan {
    val safeUpdated = (updatedAt ?: createdAt)?.toDate()?.time ?: clientUpdatedAtMillis
    val safeCreated = createdAt?.toDate()?.time ?: clientCreatedAtMillis
    return InstallmentPlan(
        id = id,
        customerId = customerId,
        itemName = itemName,
        totalCashPrice = totalCashPrice,
        downPayment = downPayment,
        interestRatePercent = interestRatePercent,
        financedTotal = financedTotal,
        installmentCount = installmentCount,
        intervalMonths = intervalMonths,
        startDateMillis = startDateMillis,
        status = runCatching { PlanStatus.valueOf(status) }.getOrDefault(PlanStatus.ACTIVE),
        notes = notes,
        createdAt = if (clientCreatedAtMillis > 0L) clientCreatedAtMillis else safeCreated,
        updatedAt = if (clientUpdatedAtMillis > 0L) clientUpdatedAtMillis else safeUpdated,
        syncStatus = SyncStatus.SYNCED
    )
}

fun InstallmentFirestoreDoc.toDomain(): InstallmentPayment {
    val safeUpdated = (updatedAt ?: createdAt)?.toDate()?.time ?: clientUpdatedAtMillis
    return InstallmentPayment(
        id = id,
        planId = planId,
        customerId = customerId,
        installmentNumber = installmentNumber,
        dueDateMillis = dueDateMillis,
        amountDue = amountDue,
        amountPaid = amountPaid,
        paidDateMillis = paidDateMillis,
        status = runCatching { PaymentStatus.valueOf(status) }.getOrDefault(PaymentStatus.PENDING),
        paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.CASH),
        receiptNumber = receiptNumber,
        notes = notes,
        updatedAt = if (clientUpdatedAtMillis > 0L) clientUpdatedAtMillis else safeUpdated,
        syncStatus = SyncStatus.SYNCED
    )
}
