package com.example.data.sync

import com.example.data.network.NetworkMonitor
import com.example.domain.model.Customer
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.InstallmentPlan
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanStatus
import com.example.domain.model.SyncStatus
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.random.Random

data class SyncPushReport(
    val spreadsheetId: String,
    val spreadsheetTitle: String,
    val totalRowsSynced: Int
)

data class SyncPullPayload(
    val spreadsheetId: String,
    val customers: List<Customer>,
    val plans: List<InstallmentPlan>,
    val payments: List<InstallmentPayment>
)

class OfflineException(message: String = "لا يوجد اتصال بالإنترنت حالياً.") : IOException(message)

class AuthExpiredException(message: String = "انتهت صلاحية جلسة Google، يرجى إعادة الربط.") :
    IOException(message)

class GoogleSheetsSyncManager(
    private val networkMonitor: NetworkMonitor,
    private val appName: String = "نظام الأقساط"
) {

    companion object {
        const val DEFAULT_SPREADSHEET_TITLE = "نظام الأقساط - سجل البيانات"
        const val SHEET_CUSTOMERS = "العملاء"
        const val SHEET_PLANS = "خطط_الأقساط"
        const val SHEET_PAYMENTS = "المدفوعات"

        private const val MAX_RETRIES = 4
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 16_000L
    }

    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private fun buildRequestInitializer(accessToken: String): HttpRequestInitializer {
        return HttpRequestInitializer { request ->
            request.connectTimeout = 20_000
            request.readTimeout = 25_000
            request.headers.authorization = "Bearer $accessToken"
        }
    }

    private fun createSheetsService(accessToken: String): Sheets {
        return Sheets.Builder(transport, jsonFactory, buildRequestInitializer(accessToken))
            .setApplicationName(appName)
            .build()
    }

    private fun createDriveService(accessToken: String): Drive {
        return Drive.Builder(transport, jsonFactory, buildRequestInitializer(accessToken))
            .setApplicationName(appName)
            .build()
    }

    suspend fun pushAllToSheets(
        accessToken: String,
        existingSpreadsheetId: String,
        spreadsheetTitle: String,
        customers: List<Customer>,
        plans: List<InstallmentPlan>,
        payments: List<InstallmentPayment>
    ): SyncPushReport = withContext(Dispatchers.IO) {
        if (!networkMonitor.isCurrentlyConnected()) {
            throw OfflineException()
        }
        if (accessToken.isBlank()) {
            throw AuthExpiredException()
        }

        val sheets = createSheetsService(accessToken)
        val drive = createDriveService(accessToken)
        val targetTitle = spreadsheetTitle.ifBlank { DEFAULT_SPREADSHEET_TITLE }

        val spreadsheetId = ensureSpreadsheet(
            sheets = sheets,
            drive = drive,
            existingId = existingSpreadsheetId,
            title = targetTitle
        )

        val customerRows = buildCustomerRows(customers)
        val planRows = buildPlanRows(plans)
        val paymentRows = buildPaymentRows(payments)

        executeWithExponentialBackoff {
            sheets.spreadsheets().values()
                .clear(spreadsheetId, "$SHEET_CUSTOMERS!A:Z", ClearValuesRequest())
                .execute()
            sheets.spreadsheets().values()
                .update(
                    spreadsheetId,
                    "$SHEET_CUSTOMERS!A1",
                    ValueRange().setValues(customerRows)
                )
                .setValueInputOption("USER_ENTERED")
                .execute()
        }

        executeWithExponentialBackoff {
            sheets.spreadsheets().values()
                .clear(spreadsheetId, "$SHEET_PLANS!A:Z", ClearValuesRequest())
                .execute()
            sheets.spreadsheets().values()
                .update(
                    spreadsheetId,
                    "$SHEET_PLANS!A1",
                    ValueRange().setValues(planRows)
                )
                .setValueInputOption("USER_ENTERED")
                .execute()
        }

        executeWithExponentialBackoff {
            sheets.spreadsheets().values()
                .clear(spreadsheetId, "$SHEET_PAYMENTS!A:Z", ClearValuesRequest())
                .execute()
            sheets.spreadsheets().values()
                .update(
                    spreadsheetId,
                    "$SHEET_PAYMENTS!A1",
                    ValueRange().setValues(paymentRows)
                )
                .setValueInputOption("USER_ENTERED")
                .execute()
        }

        SyncPushReport(
            spreadsheetId = spreadsheetId,
            spreadsheetTitle = targetTitle,
            totalRowsSynced = customers.size + plans.size + payments.size
        )
    }

    suspend fun pullAllFromSheets(
        accessToken: String,
        existingSpreadsheetId: String,
        spreadsheetTitle: String
    ): SyncPullPayload = withContext(Dispatchers.IO) {
        if (!networkMonitor.isCurrentlyConnected()) {
            throw OfflineException()
        }
        if (accessToken.isBlank()) {
            throw AuthExpiredException()
        }

        val sheets = createSheetsService(accessToken)
        val drive = createDriveService(accessToken)
        val targetTitle = spreadsheetTitle.ifBlank { DEFAULT_SPREADSHEET_TITLE }

        val spreadsheetId = ensureSpreadsheet(
            sheets = sheets,
            drive = drive,
            existingId = existingSpreadsheetId,
            title = targetTitle
        )

        val batchResponse = executeWithExponentialBackoff {
            sheets.spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(
                    listOf(
                        "$SHEET_CUSTOMERS!A2:I",
                        "$SHEET_PLANS!A2:N",
                        "$SHEET_PAYMENTS!A2:M"
                    )
                )
                .execute()
        }

        val valueRanges = batchResponse.valueRanges.orEmpty()
        val customerValues = valueRanges.getOrNull(0)?.getValues().orEmpty()
        val planValues = valueRanges.getOrNull(1)?.getValues().orEmpty()
        val paymentValues = valueRanges.getOrNull(2)?.getValues().orEmpty()

        SyncPullPayload(
            spreadsheetId = spreadsheetId,
            customers = parseCustomers(customerValues),
            plans = parsePlans(planValues),
            payments = parsePayments(paymentValues)
        )
    }

    private suspend fun ensureSpreadsheet(
        sheets: Sheets,
        drive: Drive,
        existingId: String,
        title: String
    ): String {
        if (existingId.isNotBlank()) {
            val verified = runCatching {
                executeWithExponentialBackoff {
                    sheets.spreadsheets().get(existingId).setFields("spreadsheetId").execute()
                }
            }
            if (verified.isSuccess) {
                return existingId
            }
            val err = verified.exceptionOrNull()
            if (err is GoogleJsonResponseException && err.statusCode == 401) {
                throw AuthExpiredException()
            }
        }

        val foundViaDrive = executeWithExponentialBackoff {
            val escapedTitle = title.replace("'", "\\'")
            val query =
                "mimeType='application/vnd.google-apps.spreadsheet' and name='$escapedTitle' and trashed=false"
            drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute()
                .files
                ?.firstOrNull()
                ?.id
        }

        if (!foundViaDrive.isNullOrBlank()) {
            return foundViaDrive
        }

        val created = executeWithExponentialBackoff {
            val spreadsheet = Spreadsheet()
                .setProperties(SpreadsheetProperties().setTitle(title))
                .setSheets(
                    listOf(
                        Sheet().setProperties(
                            SheetProperties().setTitle(SHEET_CUSTOMERS).setIndex(0)
                        ),
                        Sheet().setProperties(
                            SheetProperties().setTitle(SHEET_PLANS).setIndex(1)
                        ),
                        Sheet().setProperties(
                            SheetProperties().setTitle(SHEET_PAYMENTS).setIndex(2)
                        )
                    )
                )
            sheets.spreadsheets().create(spreadsheet)
                .setFields("spreadsheetId")
                .execute()
        }

        return created.spreadsheetId
    }

    /**
     * Executes a Google API call with exponential backoff and random jitter to handle
     * HTTP 429 rate limits, 5xx transient server errors, and temporary IO timeouts.
     */
    suspend fun <T> executeWithExponentialBackoff(
        maxRetries: Int = MAX_RETRIES,
        initialDelayMs: Long = INITIAL_BACKOFF_MS,
        block: () -> T
    ): T {
        var currentDelay = initialDelayMs
        var attempt = 0

        while (true) {
            if (!networkMonitor.isCurrentlyConnected()) {
                throw OfflineException()
            }
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 401) {
                    throw AuthExpiredException()
                }
                val isRateLimit = e.statusCode == 429 ||
                    (e.statusCode == 403 && isRateLimitReason(e))
                val isServerTransient = e.statusCode in 500..599
                if ((!isRateLimit && !isServerTransient) || attempt >= maxRetries) {
                    throw e
                }
            } catch (e: SocketTimeoutException) {
                if (attempt >= maxRetries) throw e
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw e
            }

            attempt++
            val jitter = Random.nextLong(0L, (currentDelay / 4).coerceAtLeast(100L))
            delay(min(currentDelay + jitter, MAX_BACKOFF_MS))
            currentDelay = min(currentDelay * 2, MAX_BACKOFF_MS)
        }
    }

    private fun isRateLimitReason(e: GoogleJsonResponseException): Boolean {
        val reason = e.details?.errors?.firstOrNull()?.reason.orEmpty()
        return reason == "rateLimitExceeded" || reason == "userRateLimitExceeded"
    }

    private fun buildCustomerRows(customers: List<Customer>): List<List<Any>> {
        val header = listOf(
            "id", "fullName", "phone", "nationalId", "address",
            "notes", "createdAt", "updatedAt", "syncStatus"
        )
        val rows = customers.map {
            listOf<Any>(
                it.id, it.fullName, it.phone, it.nationalId, it.address,
                it.notes, it.createdAt.toString(), it.updatedAt.toString(), SyncStatus.SYNCED.name
            )
        }
        return listOf(header) + rows
    }

    private fun buildPlanRows(plans: List<InstallmentPlan>): List<List<Any>> {
        val header = listOf(
            "id", "customerId", "itemName", "totalCashPrice", "downPayment",
            "interestRatePercent", "financedTotal", "installmentCount", "intervalMonths",
            "startDateMillis", "status", "notes", "createdAt", "updatedAt"
        )
        val rows = plans.map {
            listOf<Any>(
                it.id, it.customerId, it.itemName, it.totalCashPrice, it.downPayment,
                it.interestRatePercent, it.financedTotal, it.installmentCount, it.intervalMonths,
                it.startDateMillis.toString(), it.status.name, it.notes,
                it.createdAt.toString(), it.updatedAt.toString()
            )
        }
        return listOf(header) + rows
    }

    private fun buildPaymentRows(payments: List<InstallmentPayment>): List<List<Any>> {
        val header = listOf(
            "id", "planId", "customerId", "installmentNumber", "dueDateMillis",
            "amountDue", "amountPaid", "paidDateMillis", "status", "paymentMethod",
            "receiptNumber", "notes", "updatedAt"
        )
        val rows = payments.map {
            listOf<Any>(
                it.id, it.planId, it.customerId, it.installmentNumber,
                it.dueDateMillis.toString(), it.amountDue, it.amountPaid,
                it.paidDateMillis?.toString().orEmpty(), it.status.name,
                it.paymentMethod.name, it.receiptNumber, it.notes, it.updatedAt.toString()
            )
        }
        return listOf(header) + rows
    }

    private fun parseCustomers(rows: List<List<Any>>): List<Customer> {
        return rows.mapNotNull { row ->
            val id = row.cellString(0)
            val name = row.cellString(1)
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            Customer(
                id = id,
                fullName = name,
                phone = row.cellString(2),
                nationalId = row.cellString(3),
                address = row.cellString(4),
                notes = row.cellString(5),
                createdAt = row.cellLong(6, System.currentTimeMillis()),
                updatedAt = row.cellLong(7, System.currentTimeMillis()),
                syncStatus = SyncStatus.SYNCED
            )
        }
    }

    private fun parsePlans(rows: List<List<Any>>): List<InstallmentPlan> {
        return rows.mapNotNull { row ->
            val id = row.cellString(0)
            val customerId = row.cellString(1)
            val itemName = row.cellString(2)
            if (id.isBlank() || customerId.isBlank() || itemName.isBlank()) return@mapNotNull null
            InstallmentPlan(
                id = id,
                customerId = customerId,
                itemName = itemName,
                totalCashPrice = row.cellDouble(3),
                downPayment = row.cellDouble(4),
                interestRatePercent = row.cellDouble(5),
                financedTotal = row.cellDouble(6),
                installmentCount = row.cellInt(7, 1),
                intervalMonths = row.cellInt(8, 1),
                startDateMillis = row.cellLong(9, System.currentTimeMillis()),
                status = runCatching { PlanStatus.valueOf(row.cellString(10)) }
                    .getOrDefault(PlanStatus.ACTIVE),
                notes = row.cellString(11),
                createdAt = row.cellLong(12, System.currentTimeMillis()),
                updatedAt = row.cellLong(13, System.currentTimeMillis()),
                syncStatus = SyncStatus.SYNCED
            )
        }
    }

    private fun parsePayments(rows: List<List<Any>>): List<InstallmentPayment> {
        return rows.mapNotNull { row ->
            val id = row.cellString(0)
            val planId = row.cellString(1)
            val customerId = row.cellString(2)
            if (id.isBlank() || planId.isBlank() || customerId.isBlank()) return@mapNotNull null
            val paidMillisStr = row.cellString(7)
            InstallmentPayment(
                id = id,
                planId = planId,
                customerId = customerId,
                installmentNumber = row.cellInt(3, 1),
                dueDateMillis = row.cellLong(4, System.currentTimeMillis()),
                amountDue = row.cellDouble(5),
                amountPaid = row.cellDouble(6),
                paidDateMillis = paidMillisStr.toLongOrNull(),
                status = runCatching { PaymentStatus.valueOf(row.cellString(8)) }
                    .getOrDefault(PaymentStatus.PENDING),
                paymentMethod = runCatching { PaymentMethod.valueOf(row.cellString(9)) }
                    .getOrDefault(PaymentMethod.CASH),
                receiptNumber = row.cellString(10),
                notes = row.cellString(11),
                updatedAt = row.cellLong(12, System.currentTimeMillis()),
                syncStatus = SyncStatus.SYNCED
            )
        }
    }

    private fun List<Any>.cellString(index: Int): String =
        getOrNull(index)?.toString()?.trim().orEmpty()

    private fun List<Any>.cellDouble(index: Int): Double =
        cellString(index).replace(",", "").toDoubleOrNull() ?: 0.0

    private fun List<Any>.cellInt(index: Int, default: Int = 0): Int =
        cellString(index).toDoubleOrNull()?.toInt() ?: default

    private fun List<Any>.cellLong(index: Int, default: Long = 0L): Long =
        cellString(index).toLongOrNull() ?: default
}
