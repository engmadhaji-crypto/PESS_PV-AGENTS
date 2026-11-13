package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.InstallmentPlanEntity
import com.example.data.network.NetworkMonitor
import com.example.data.pdf.PdfInvoiceGenerator
import com.example.data.repository.InstallmentRepositoryImpl
import com.example.data.sync.GoogleSheetsSyncManager
import com.example.data.sync.OfflineException
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanStatus
import com.example.domain.model.SyncState
import com.example.domain.util.InstallmentCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [InstallmentSystemTest.TestShadowPdfDocument::class])
class InstallmentSystemTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `installment calculator splits cents accurately without rounding drift`() {
        val financed = InstallmentCalculator.calculateFinancedTotal(
            totalCashPrice = 1000.0,
            downPayment = 0.0,
            interestRatePercent = 0.0
        )
        val schedule = InstallmentCalculator.generateSchedule(
            planId = "p1",
            customerId = "c1",
            financedTotal = financed,
            installmentCount = 3,
            startDateMillis = System.currentTimeMillis() + 86_400_000L
        )
        assertEquals(3, schedule.size)
        val sum = schedule.sumOf { it.amountDue }
        assertEquals(1000.0, sum, 0.001)
    }

    @Test
    fun `repository creates customer plan payments and completes plan upon full collection`() = runTest {
        val fakeMonitor = FakeNetworkMonitor(context, online = false)
        val syncManager = GoogleSheetsSyncManager(fakeMonitor)
        val repo = InstallmentRepositoryImpl(database, fakeMonitor, syncManager)

        val customerId = repo.addCustomerWithOptionalPlan(
            fullName = "أحمد محمد",
            phone = "0501234567",
            nationalId = "1010101010",
            address = "الرياض",
            notes = "",
            itemName = "جهاز لابتوب",
            totalCashPrice = 3000.0,
            downPayment = 1000.0,
            interestRatePercent = 0.0,
            installmentCount = 2,
            startDateMillis = System.currentTimeMillis() + 86_400_000L
        ).getOrThrow()

        val plans = repo.observePlansForCustomer(customerId).first()
        assertEquals(1, plans.size)
        val bundle = plans.first()
        assertEquals(2, bundle.payments.size)
        assertEquals(PlanStatus.ACTIVE, bundle.plan.status)

        bundle.payments.forEach { payment ->
            repo.recordPayment(
                paymentId = payment.id,
                paidAmount = payment.amountDue,
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                receiptNumber = "R-100",
                notes = "سداد كامل"
            ).getOrThrow()
        }

        val updatedPlans = repo.observePlansForCustomer(customerId).first()
        assertEquals(PlanStatus.COMPLETED, updatedPlans.first().plan.status)
        assertTrue(updatedPlans.first().payments.all { it.status == PaymentStatus.PAID })
    }

    @Test
    fun `offline sync queues changes gracefully without crashing`() = runTest {
        val fakeMonitor = FakeNetworkMonitor(context, online = false)
        val syncManager = GoogleSheetsSyncManager(fakeMonitor)
        val repo = InstallmentRepositoryImpl(database, fakeMonitor, syncManager)

        repo.addCustomerWithOptionalPlan(
            fullName = "خالد علي",
            phone = "0559876543",
            nationalId = "",
            address = "",
            notes = "",
            itemName = null,
            totalCashPrice = null,
            downPayment = null,
            interestRatePercent = null,
            installmentCount = null,
            startDateMillis = null
        ).getOrThrow()

        val syncResult = repo.syncWithGoogleSheets("dummy-token")
        assertTrue(syncResult.isFailure)
        assertTrue(syncResult.exceptionOrNull() is OfflineException)
        assertTrue(repo.syncState.value is SyncState.OfflineQueued)
    }

    @Test
    fun `exponential backoff retries transient IO failures and succeeds`() = runTest {
        val fakeMonitor = FakeNetworkMonitor(context, online = true)
        val syncManager = GoogleSheetsSyncManager(fakeMonitor)
        var attempts = 0

        val result = syncManager.executeWithExponentialBackoff(
            maxRetries = 3,
            initialDelayMs = 10L
        ) {
            attempts++
            if (attempts < 3) {
                throw IOException("Transient error")
            }
            "OK"
        }

        assertEquals("OK", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `pdf invoice generator creates valid non-empty pdf uri`() = runTest {
        val fakeMonitor = FakeNetworkMonitor(context, online = false)
        val syncManager = GoogleSheetsSyncManager(fakeMonitor)
        val repo = InstallmentRepositoryImpl(database, fakeMonitor, syncManager)

        val customerId = repo.addCustomerWithOptionalPlan(
            fullName = "سارة عبدالله",
            phone = "0541112233",
            nationalId = "1020304050",
            address = "جدة",
            notes = "",
            itemName = "هاتف ذكي",
            totalCashPrice = 2400.0,
            downPayment = 400.0,
            interestRatePercent = 10.0,
            installmentCount = 4,
            startDateMillis = System.currentTimeMillis() + 86_400_000L
        ).getOrThrow()

        val customer = repo.observeCustomerById(customerId).first()!!
        val plans = repo.observePlansForCustomer(customerId).first()
        val generator = PdfInvoiceGenerator(context)
        val uriResult = generator.generateCustomerStatementPdf(customer, plans)

        assertTrue(
            "PDF generation failed: ${uriResult.exceptionOrNull()?.stackTraceToString()}",
            uriResult.isSuccess
        )
        assertTrue(uriResult.getOrNull().toString().contains("fileprovider"))
    }

    @Test
    fun `installmentDao inserts and queries customers with linked installments relationally`() = runTest {
        val dao = database.installmentDao()
        val now = System.currentTimeMillis()
        val customer = CustomerEntity(
            id = "cust-rel-1",
            fullName = "فهد السبيعي",
            phone = "0550001122",
            nationalId = "1099887766",
            address = "الدمام",
            notes = "",
            createdAt = now,
            updatedAt = now,
            syncStatus = "SYNCED"
        )
        val plan = InstallmentPlanEntity(
            id = "plan-rel-1",
            customerId = customer.id,
            itemName = "شاشة ذكية",
            totalCashPrice = 2000.0,
            downPayment = 200.0,
            interestRatePercent = 0.0,
            financedTotal = 1800.0,
            installmentCount = 2,
            intervalMonths = 1,
            startDateMillis = now,
            status = "ACTIVE",
            notes = "",
            createdAt = now,
            updatedAt = now,
            syncStatus = "SYNCED"
        )
        val overdue = InstallmentEntity(
            id = "inst-rel-1",
            planId = plan.id,
            customerId = customer.id,
            installmentNumber = 1,
            dueDateMillis = now - 86_400_000L * 5,
            amountDue = 900.0,
            amountPaid = 0.0,
            paidDateMillis = null,
            status = "OVERDUE",
            paymentMethod = "CASH",
            receiptNumber = "",
            notes = "",
            updatedAt = now,
            syncStatus = "SYNCED"
        )
        val upcoming = InstallmentEntity(
            id = "inst-rel-2",
            planId = plan.id,
            customerId = customer.id,
            installmentNumber = 2,
            dueDateMillis = now + 86_400_000L * 10,
            amountDue = 900.0,
            amountPaid = 0.0,
            paidDateMillis = null,
            status = "PENDING",
            paymentMethod = "CASH",
            receiptNumber = "",
            notes = "",
            updatedAt = now,
            syncStatus = "SYNCED"
        )

        dao.insertCustomerWithPlanAndInstallments(customer, plan, listOf(overdue, upcoming))

        val searchResults = dao.searchCustomers("فهد").first()
        assertEquals(1, searchResults.size)
        assertEquals("0550001122", searchResults.first().phone)

        val overdueList = dao.observeOverdueInstallments(now).first()
        assertEquals(1, overdueList.size)
        assertEquals("inst-rel-1", overdueList.first().id)

        val relation = dao.observeCustomerWithInstallments(customer.id).first()!!
        assertEquals("فهد السبيعي", relation.customer.fullName)
        assertEquals(2, relation.installments.size)

        val fullHierarchy = dao.observeCustomersWithPlansAndInstallments().first()
        assertEquals(1, fullHierarchy.size)
        assertEquals(1, fullHierarchy.first().plans.size)
        assertEquals(2, fullHierarchy.first().plans.first().installments.size)
    }

    @Test
    fun `database backup exports and imports customers plans and installments in merge and replace modes`() = runTest {
        val fakeMonitor = FakeNetworkMonitor(context, online = false)
        val syncManager = GoogleSheetsSyncManager(fakeMonitor)
        val repo = InstallmentRepositoryImpl(database, fakeMonitor, syncManager)

        repo.addCustomerWithOptionalPlan(
            fullName = "نورة القحطاني",
            phone = "0509998877",
            nationalId = "1088776655",
            address = "الخبر",
            notes = "",
            itemName = "ثلاجة",
            totalCashPrice = 3000.0,
            downPayment = 600.0,
            interestRatePercent = 0.0,
            installmentCount = 3,
            startDateMillis = System.currentTimeMillis() + 86_400_000L
        ).getOrThrow()

        val (backupJson, exportSummary) = repo.exportDatabaseBackup().getOrThrow()
        assertEquals(1, exportSummary.customerCount)
        assertEquals(1, exportSummary.planCount)
        assertEquals(3, exportSummary.installmentCount)

        // Add another customer locally after backup
        val extraCustomerId = repo.addCustomerWithOptionalPlan(
            fullName = "عمر الشهري",
            phone = "0551110000",
            nationalId = "",
            address = "",
            notes = "",
            itemName = null,
            totalCashPrice = null,
            downPayment = null,
            interestRatePercent = null,
            installmentCount = null,
            startDateMillis = null
        ).getOrThrow()
        assertEquals(2, repo.observeCustomersWithSummary().first().size)

        val preview = repo.previewDatabaseBackup(backupJson).getOrThrow()
        assertEquals(1, preview.customerCount)
        assertEquals(3, preview.installmentCount)

        // Merge import preserves extraCustomerId
        repo.importDatabaseBackup(backupJson, replaceExisting = false).getOrThrow()
        assertEquals(2, repo.observeCustomersWithSummary().first().size)

        // Replace import restores exact snapshot from backup
        val importSummary = repo.importDatabaseBackup(backupJson, replaceExisting = true).getOrThrow()
        assertEquals(1, importSummary.customerCount)
        val customersAfterReplace = repo.observeCustomersWithSummary().first()
        assertEquals(1, customersAfterReplace.size)
        assertTrue(customersAfterReplace.none { it.customer.id == extraCustomerId })

        // Invalid backup JSON is rejected safely
        val invalidResult = repo.previewDatabaseBackup("{invalid-json")
        assertTrue(invalidResult.isFailure)
    }

    private class FakeNetworkMonitor(
        context: Context,
        private val online: Boolean
    ) : NetworkMonitor(context) {
        private val state = MutableStateFlow(online)
        override val isOnline: StateFlow<Boolean> = state
        override fun isCurrentlyConnected(): Boolean = online
    }

    @Implements(PdfDocument::class)
    class TestShadowPdfDocument {
        @RealObject
        private lateinit var realPdfDocument: PdfDocument
        private val pages = mutableListOf<PdfDocument.PageInfo>()

        @Implementation
        protected fun __constructor__() {
            // Skip native libpdfium allocation in Robolectric JVM
        }

        @Implementation
        protected fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
            pages.add(pageInfo)
            val bitmap = Bitmap.createBitmap(
                pageInfo.pageWidth,
                pageInfo.pageHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(
                Canvas::class.java,
                PdfDocument.PageInfo::class.java
            )
            ctor.isAccessible = true
            return ctor.newInstance(canvas, pageInfo)
        }

        @Implementation
        protected fun finishPage(page: PdfDocument.Page) {
            // No-op in JVM test shadow
        }

        @Implementation
        protected fun writeTo(out: OutputStream) {
            out.write("%PDF-1.4\n%Test\n".toByteArray())
        }

        @Implementation
        protected fun close() {
            // No-op in JVM test shadow
        }
    }
}
