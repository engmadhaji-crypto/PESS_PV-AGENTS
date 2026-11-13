package com.example.data.local.mapper

import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.InstallmentPlanEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.domain.model.Customer
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.InstallmentPlan
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanStatus
import com.example.domain.model.SyncMetadata
import com.example.domain.model.SyncStatus

fun CustomerEntity.toDomain(): Customer = Customer(
    id = id,
    fullName = fullName,
    phone = phone,
    nationalId = nationalId,
    address = address,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = enumValueOrDefault(syncStatus, SyncStatus.PENDING_UPLOAD)
)

fun Customer.toEntity(): CustomerEntity = CustomerEntity(
    id = id,
    fullName = fullName,
    phone = phone,
    nationalId = nationalId,
    address = address,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus.name
)

fun InstallmentPlanEntity.toDomain(): InstallmentPlan = InstallmentPlan(
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
    status = enumValueOrDefault(status, PlanStatus.ACTIVE),
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = enumValueOrDefault(syncStatus, SyncStatus.PENDING_UPLOAD)
)

fun InstallmentPlan.toEntity(): InstallmentPlanEntity = InstallmentPlanEntity(
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
    status = status.name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus.name
)

fun InstallmentPaymentEntity.toDomain(): InstallmentPayment = InstallmentPayment(
    id = id,
    planId = planId,
    customerId = customerId,
    installmentNumber = installmentNumber,
    dueDateMillis = dueDateMillis,
    amountDue = amountDue,
    amountPaid = amountPaid,
    paidDateMillis = paidDateMillis,
    status = enumValueOrDefault(status, PaymentStatus.PENDING),
    paymentMethod = enumValueOrDefault(paymentMethod, PaymentMethod.CASH),
    receiptNumber = receiptNumber,
    notes = notes,
    updatedAt = updatedAt,
    syncStatus = enumValueOrDefault(syncStatus, SyncStatus.PENDING_UPLOAD)
)

fun InstallmentPayment.toEntity(): InstallmentPaymentEntity = InstallmentPaymentEntity(
    id = id,
    planId = planId,
    customerId = customerId,
    installmentNumber = installmentNumber,
    dueDateMillis = dueDateMillis,
    amountDue = amountDue,
    amountPaid = amountPaid,
    paidDateMillis = paidDateMillis,
    status = status.name,
    paymentMethod = paymentMethod.name,
    receiptNumber = receiptNumber,
    notes = notes,
    updatedAt = updatedAt,
    syncStatus = syncStatus.name
)

fun SyncMetadataEntity.toDomain(): SyncMetadata = SyncMetadata(
    accountEmail = accountEmail,
    spreadsheetId = spreadsheetId,
    spreadsheetTitle = spreadsheetTitle,
    lastSyncTimestamp = lastSyncTimestamp,
    lastSyncMessage = lastSyncMessage
)

fun SyncMetadata.toEntity(): SyncMetadataEntity = SyncMetadataEntity(
    id = 1,
    accountEmail = accountEmail,
    spreadsheetId = spreadsheetId,
    spreadsheetTitle = spreadsheetTitle,
    lastSyncTimestamp = lastSyncTimestamp,
    lastSyncMessage = lastSyncMessage
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T {
    return runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
