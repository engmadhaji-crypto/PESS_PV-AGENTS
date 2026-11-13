package com.example.data.local.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.InstallmentPlanEntity
import com.example.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CustomerBackupDto(
    val id: String,
    val fullName: String,
    val phone: String,
    val nationalId: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class PlanBackupDto(
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
    val status: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class InstallmentBackupDto(
    val id: String,
    val planId: String,
    val customerId: String,
    val installmentNumber: Int,
    val dueDateMillis: Long,
    val amountDue: Double,
    val amountPaid: Double,
    val paidDateMillis: Long? = null,
    val status: String,
    val paymentMethod: String = "CASH",
    val receiptNumber: String = "",
    val notes: String = "",
    val updatedAt: Long
)

@Serializable
data class DatabaseBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtMillis: Long,
    val appName: String = "نظام الأقساط",
    val customers: List<CustomerBackupDto> = emptyList(),
    val plans: List<PlanBackupDto> = emptyList(),
    val installments: List<InstallmentBackupDto> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

class DatabaseBackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serializeBackup(
        customers: List<CustomerEntity>,
        plans: List<InstallmentPlanEntity>,
        installments: List<InstallmentPaymentEntity>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val payload = DatabaseBackupPayload(
            schemaVersion = DatabaseBackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtMillis = exportedAtMillis,
            customers = customers.map { it.toBackupDto() },
            plans = plans.map { it.toBackupDto() },
            installments = installments.map { it.toBackupDto() }
        )
        return json.encodeToString(DatabaseBackupPayload.serializer(), payload)
    }

    fun parseAndValidate(rawJson: String): DatabaseBackupPayload {
        val trimmed = rawJson.trim()
        require(trimmed.isNotEmpty()) { "ملف النسخة الاحتياطية فارغ" }

        val payload = try {
            json.decodeFromString(DatabaseBackupPayload.serializer(), trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("تنسيق ملف النسخة الاحتياطية غير صالح", e)
        }

        require(payload.schemaVersion in 1..DatabaseBackupPayload.CURRENT_SCHEMA_VERSION) {
            "إصدار النسخة الاحتياطية (${payload.schemaVersion}) غير مدعوم"
        }

        val customerIds = payload.customers.map { it.id }.toSet()
        require(customerIds.size == payload.customers.size) { "توجد معرّفات عملاء مكررة في الملف" }
        require(payload.customers.all { it.id.isNotBlank() && it.fullName.isNotBlank() }) {
            "توجد بيانات عملاء ناقصة في الملف"
        }

        val planIds = payload.plans.map { it.id }.toSet()
        require(planIds.size == payload.plans.size) { "توجد معرّفات عقود مكررة في الملف" }
        require(payload.plans.all { it.id.isNotBlank() && it.customerId in customerIds }) {
            "توجد عقود تقسيط غير مرتبطة بعميل صالح"
        }

        val installmentIds = payload.installments.map { it.id }.toSet()
        require(installmentIds.size == payload.installments.size) { "توجد معرّفات أقساط مكررة في الملف" }
        require(
            payload.installments.all {
                it.id.isNotBlank() && it.customerId in customerIds && it.planId in planIds
            }
        ) {
            "توجد أقساط غير مرتبطة بعميل أو عقد صالح"
        }

        return payload
    }

    suspend fun writeToUri(context: Context, uri: Uri, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val outputStream = resolver.openOutputStream(uri, "wt")
                    ?: resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("تعذّر فتح مسار الحفظ المحدد")
                outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
        }

    suspend fun readFromUri(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("تعذّر قراءة الملف المحدد")
                inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        }
}

fun CustomerEntity.toBackupDto(): CustomerBackupDto = CustomerBackupDto(
    id = id,
    fullName = fullName,
    phone = phone,
    nationalId = nationalId,
    address = address,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CustomerBackupDto.toEntity(syncStatus: String = SyncStatus.PENDING_UPLOAD.name): CustomerEntity =
    CustomerEntity(
        id = id,
        fullName = fullName.trim(),
        phone = phone.trim(),
        nationalId = nationalId.trim(),
        address = address.trim(),
        notes = notes.trim(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
    )

fun InstallmentPlanEntity.toBackupDto(): PlanBackupDto = PlanBackupDto(
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
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PlanBackupDto.toEntity(syncStatus: String = SyncStatus.PENDING_UPLOAD.name): InstallmentPlanEntity =
    InstallmentPlanEntity(
        id = id,
        customerId = customerId,
        itemName = itemName.trim(),
        totalCashPrice = totalCashPrice,
        downPayment = downPayment,
        interestRatePercent = interestRatePercent,
        financedTotal = financedTotal,
        installmentCount = installmentCount,
        intervalMonths = intervalMonths,
        startDateMillis = startDateMillis,
        status = status,
        notes = notes.trim(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
    )

fun InstallmentPaymentEntity.toBackupDto(): InstallmentBackupDto = InstallmentBackupDto(
    id = id,
    planId = planId,
    customerId = customerId,
    installmentNumber = installmentNumber,
    dueDateMillis = dueDateMillis,
    amountDue = amountDue,
    amountPaid = amountPaid,
    paidDateMillis = paidDateMillis,
    status = status,
    paymentMethod = paymentMethod,
    receiptNumber = receiptNumber,
    notes = notes,
    updatedAt = updatedAt
)

fun InstallmentBackupDto.toEntity(syncStatus: String = SyncStatus.PENDING_UPLOAD.name): InstallmentPaymentEntity =
    InstallmentPaymentEntity(
        id = id,
        planId = planId,
        customerId = customerId,
        installmentNumber = installmentNumber,
        dueDateMillis = dueDateMillis,
        amountDue = amountDue,
        amountPaid = amountPaid,
        paidDateMillis = paidDateMillis,
        status = status,
        paymentMethod = paymentMethod,
        receiptNumber = receiptNumber.trim(),
        notes = notes.trim(),
        updatedAt = updatedAt,
        syncStatus = syncStatus
    )
