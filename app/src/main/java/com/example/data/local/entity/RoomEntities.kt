package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "fullName") val fullName: String,
    @ColumnInfo(name = "phone") val phone: String,
    @ColumnInfo(name = "nationalId") val nationalId: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "syncStatus") val syncStatus: String
)

@Entity(
    tableName = "installment_plans",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerId"])]
)
data class InstallmentPlanEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val itemName: String,
    val totalCashPrice: Double,
    val downPayment: Double,
    val interestRatePercent: Double,
    val financedTotal: Double,
    val installmentCount: Int,
    val intervalMonths: Int,
    val startDateMillis: Long,
    val status: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
)

@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = InstallmentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["planId"]), Index(value = ["customerId"])]
)
data class InstallmentPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "planId") val planId: String,
    @ColumnInfo(name = "customerId") val customerId: String,
    @ColumnInfo(name = "installmentNumber") val installmentNumber: Int,
    @ColumnInfo(name = "dueDateMillis") val dueDateMillis: Long,
    @ColumnInfo(name = "amountDue") val amountDue: Double,
    @ColumnInfo(name = "amountPaid") val amountPaid: Double,
    @ColumnInfo(name = "paidDateMillis") val paidDateMillis: Long?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "paymentMethod") val paymentMethod: String,
    @ColumnInfo(name = "receiptNumber") val receiptNumber: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "syncStatus") val syncStatus: String
)

typealias InstallmentEntity = InstallmentPaymentEntity

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val accountEmail: String,
    val spreadsheetId: String,
    val spreadsheetTitle: String,
    val lastSyncTimestamp: Long,
    val lastSyncMessage: String
)
