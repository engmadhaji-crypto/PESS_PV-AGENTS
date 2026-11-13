package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.firebase.FirestoreInstallmentDataSource
import com.example.data.local.AppDatabase
import com.example.data.local.backup.DatabaseBackupManager
import com.example.data.local.backup.toEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.data.network.NetworkMonitor
import com.example.data.sync.AuthExpiredException
import com.example.data.sync.GoogleSheetsSyncManager
import com.example.data.sync.OfflineException
import com.example.domain.model.Customer
import com.example.domain.model.CustomerWithSummary
import com.example.domain.model.DashboardStats
import com.example.domain.model.DatabaseBackupPreview
import com.example.domain.model.DatabaseBackupSummary
import com.example.domain.model.DueAlertItem
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.InstallmentPlan
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanWithPayments
import com.example.domain.model.SyncMetadata
import com.example.domain.model.SyncState
import com.example.domain.model.SyncStatus
import com.example.domain.repository.InstallmentRepository
import com.example.domain.util.InstallmentCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class InstallmentRepositoryImpl(
    private val database: AppDatabase,
    private val networkMonitor: NetworkMonitor,
    private val sheetsSyncManager: GoogleSheetsSyncManager,
    private val firestoreDataSource: FirestoreInstallmentDataSource? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val backupManager: DatabaseBackupManager = DatabaseBackupManager()
) : InstallmentRepository {

    private val installmentDao = database.installmentDao()
    private val customerDao = database.customerDao()
    private val planDao = database.installmentPlanDao()
    private val paymentDao = database.installmentPaymentDao()
    private val syncMetadataDao = database.syncMetadataDao()

    @Volatile
    private var activeUserId: String? = null

    override val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override val syncMetadata: Flow<SyncMetadata> = syncMetadataDao.observe()
        .map { it?.toDomain() ?: SyncMetadata() }
        .flowOn(ioDispatcher)

    override suspend fun setAuthenticatedUser(userId: String?, email: String?) = withContext(ioDispatcher) {
        val previous = activeUserId
        if (previous != null && userId != null && previous != userId) {
            database.withTransaction {
                val existing = customerDao.getAllOnce()
                existing.forEach { customerDao.deleteById(it.id) }
            }
        }
        activeUserId = userId
        if (!email.isNullOrBlank()) {
            updateConnectedAccount(email)
        } else if (userId == null) {
            updateConnectedAccount("")
        }
    }

    override fun observeDashboardStats(): Flow<DashboardStats> {
        return combine(
            customerDao.observeAll(),
            planDao.observeAll(),
            paymentDao.observeAll()
        ) { customerEntities, planEntities, paymentEntities ->
            val customers = customerEntities.map { it.toDomain() }
            val plans = planEntities.map { it.toDomain() }
            val payments = paymentEntities.map { it.toDomain() }

            val totalDownPayments = plans.sumOf { it.downPayment }
            val totalFinanced = plans.sumOf { it.financedTotal }
            val totalPortfolio = totalDownPayments + totalFinanced
            val totalInstallmentsCollected = payments.sumOf { it.amountPaid }
            val totalCollected = totalDownPayments + totalInstallmentsCollected
            val totalRemaining = payments.sumOf { it.remainingAmount }
            val rate = if (totalPortfolio > 0.0) {
                (totalCollected / totalPortfolio).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            val now = System.currentTimeMillis()
            val dayStart = InstallmentCalculator.startOfDay(now)
            val soonEnd = InstallmentCalculator.endOfDaysAhead(now, 7)

            val overdueCount = payments.count {
                it.status != PaymentStatus.PAID && it.remainingAmount > 0.01 && it.dueDateMillis < dayStart
            }
            val dueSoonCount = payments.count {
                it.status != PaymentStatus.PAID &&
                    it.remainingAmount > 0.01 &&
                    it.dueDateMillis in dayStart..soonEnd
            }

            val activePlans = plans.count { it.status.name != "COMPLETED" }
            val completedPlans = plans.count { it.status.name == "COMPLETED" }
            val pendingSync = customers.count { it.syncStatus != SyncStatus.SYNCED } +
                plans.count { it.syncStatus != SyncStatus.SYNCED } +
                payments.count { it.syncStatus != SyncStatus.SYNCED }

            DashboardStats(
                totalPortfolio = totalPortfolio,
                totalCollected = totalCollected,
                totalRemaining = totalRemaining,
                collectionRate = rate,
                activePlansCount = activePlans,
                completedPlansCount = completedPlans,
                overdueCount = overdueCount,
                dueSoonCount = dueSoonCount,
                customerCount = customers.size,
                pendingSyncCount = pendingSync
            )
        }.flowOn(ioDispatcher)
    }

    override fun observeDueAlerts(): Flow<List<DueAlertItem>> {
        return combine(
            customerDao.observeAll(),
            planDao.observeAll(),
            paymentDao.observeAll()
        ) { customerEntities, planEntities, paymentEntities ->
            val customerMap = customerEntities.associate { it.id to it.toDomain() }
            val planMap = planEntities.associate { it.id to it.toDomain() }
            val now = System.currentTimeMillis()
            val dayStart = InstallmentCalculator.startOfDay(now)
            val soonEnd = InstallmentCalculator.endOfDaysAhead(now, 14)

            paymentEntities
                .map { it.toDomain() }
                .filter { payment ->
                    payment.status != PaymentStatus.PAID &&
                        payment.remainingAmount > 0.01 &&
                        payment.dueDateMillis <= soonEnd
                }
                .sortedBy { it.dueDateMillis }
                .mapNotNull { payment ->
                    val customer = customerMap[payment.customerId] ?: return@mapNotNull null
                    val plan = planMap[payment.planId] ?: return@mapNotNull null
                    DueAlertItem(
                        payment = payment,
                        customerName = customer.fullName,
                        customerPhone = customer.phone,
                        itemName = plan.itemName,
                        isOverdue = payment.dueDateMillis < dayStart
                    )
                }
        }.flowOn(ioDispatcher)
    }

    override fun observeCustomersWithSummary(): Flow<List<CustomerWithSummary>> {
        return combine(
            customerDao.observeAll(),
            planDao.observeAll(),
            paymentDao.observeAll()
        ) { customerEntities, planEntities, paymentEntities ->
            val plansByCustomer = planEntities.map { it.toDomain() }.groupBy { it.customerId }
            val paymentsByCustomer = paymentEntities.map { it.toDomain() }.groupBy { it.customerId }
            val dayStart = InstallmentCalculator.startOfDay(System.currentTimeMillis())

            customerEntities.map { entity ->
                val customer = entity.toDomain()
                val cPlans = plansByCustomer[customer.id].orEmpty()
                val cPayments = paymentsByCustomer[customer.id].orEmpty()

                val totalFinanced = cPlans.sumOf { it.downPayment + it.financedTotal }
                val totalPaid = cPlans.sumOf { it.downPayment } + cPayments.sumOf { it.amountPaid }
                val totalRemaining = cPayments.sumOf { it.remainingAmount }
                val overdueCount = cPayments.count {
                    it.status != PaymentStatus.PAID &&
                        it.remainingAmount > 0.01 &&
                        it.dueDateMillis < dayStart
                }
                val nextDue = cPayments
                    .filter { it.status != PaymentStatus.PAID && it.remainingAmount > 0.01 }
                    .minByOrNull { it.dueDateMillis }
                    ?.dueDateMillis

                CustomerWithSummary(
                    customer = customer,
                    activePlansCount = cPlans.count { it.status.name != "COMPLETED" },
                    totalFinanced = totalFinanced,
                    totalPaid = totalPaid,
                    totalRemaining = totalRemaining,
                    overdueCount = overdueCount,
                    nextDueDateMillis = nextDue
                )
            }
        }.flowOn(ioDispatcher)
    }

    override fun observeCustomerById(customerId: String): Flow<Customer?> {
        return customerDao.observeById(customerId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)
    }

    override fun observePlansForCustomer(customerId: String): Flow<List<PlanWithPayments>> {
        return combine(
            customerDao.observeById(customerId),
            planDao.observeByCustomerId(customerId),
            paymentDao.observeByCustomerId(customerId)
        ) { customerEntity, planEntities, paymentEntities ->
            val customer = customerEntity?.toDomain()
            val paymentsByPlan = paymentEntities.map { it.toDomain() }.groupBy { it.planId }
            planEntities.map { planEntity ->
                val plan = planEntity.toDomain()
                PlanWithPayments(
                    plan = plan,
                    customer = customer,
                    payments = paymentsByPlan[plan.id].orEmpty().sortedBy { it.installmentNumber }
                )
            }
        }.flowOn(ioDispatcher)
    }

    override fun observePlanWithPayments(planId: String): Flow<PlanWithPayments?> {
        return combine(
            planDao.observeById(planId),
            paymentDao.observeByPlanId(planId),
            customerDao.observeAll()
        ) { planEntity, paymentEntities, customers ->
            val plan = planEntity?.toDomain() ?: return@combine null
            val customer = customers.firstOrNull { it.id == plan.customerId }?.toDomain()
            PlanWithPayments(
                plan = plan,
                customer = customer,
                payments = paymentEntities.map { it.toDomain() }.sortedBy { it.installmentNumber }
            )
        }.flowOn(ioDispatcher)
    }

    override suspend fun addCustomerWithOptionalPlan(
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
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val cleanName = fullName.trim()
            val cleanPhone = phone.trim()
            require(cleanName.isNotEmpty()) { "يرجى إدخال اسم العميل" }
            require(cleanPhone.isNotEmpty()) { "يرجى إدخال رقم الهاتف" }

            val now = System.currentTimeMillis()
            val customerId = UUID.randomUUID().toString()
            val customer = Customer(
                id = customerId,
                fullName = cleanName,
                phone = cleanPhone,
                nationalId = nationalId.trim(),
                address = address.trim(),
                notes = notes.trim(),
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING_UPLOAD
            )

            var createdPlan: InstallmentPlan? = null
            var createdPayments: List<InstallmentPayment> = emptyList()

            database.withTransaction {
                customerDao.upsert(customer.toEntity())

                if (!itemName.isNullOrBlank() && totalCashPrice != null && totalCashPrice > 0.0) {
                    val safeDown = (downPayment ?: 0.0).coerceIn(0.0, totalCashPrice)
                    val safeRate = (interestRatePercent ?: 0.0).coerceAtLeast(0.0)
                    val safeCount = (installmentCount ?: 12).coerceIn(1, 120)
                    val safeStart = startDateMillis ?: now
                    val financedTotal = InstallmentCalculator.calculateFinancedTotal(
                        totalCashPrice = totalCashPrice,
                        downPayment = safeDown,
                        interestRatePercent = safeRate
                    )
                    val planId = UUID.randomUUID().toString()
                    val payments = InstallmentCalculator.generateSchedule(
                        planId = planId,
                        customerId = customerId,
                        financedTotal = financedTotal,
                        installmentCount = safeCount,
                        startDateMillis = safeStart,
                        intervalMonths = 1,
                        nowMillis = now
                    )
                    val plan = InstallmentPlan(
                        id = planId,
                        customerId = customerId,
                        itemName = itemName.trim(),
                        totalCashPrice = totalCashPrice,
                        downPayment = safeDown,
                        interestRatePercent = safeRate,
                        financedTotal = financedTotal,
                        installmentCount = safeCount,
                        intervalMonths = 1,
                        startDateMillis = safeStart,
                        status = InstallmentCalculator.resolvePlanStatus(payments, now),
                        notes = "",
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_UPLOAD
                    )
                    planDao.upsert(plan.toEntity())
                    paymentDao.upsertAll(payments.map { it.toEntity() })
                    createdPlan = plan
                    createdPayments = payments
                }
            }

            val uid = activeUserId
            val ds = firestoreDataSource
            if (uid != null && ds != null && networkMonitor.isCurrentlyConnected()) {
                runCatching {
                    ds.createCustomerWithOptionalPlan(
                        userId = uid,
                        customer = customer,
                        plan = createdPlan,
                        installments = createdPayments
                    )
                    database.withTransaction {
                        customerDao.updateSyncStatus(listOf(customer.id), SyncStatus.SYNCED.name)
                        createdPlan?.let { planDao.updateSyncStatus(listOf(it.id), SyncStatus.SYNCED.name) }
                        if (createdPayments.isNotEmpty()) {
                            paymentDao.updateSyncStatus(createdPayments.map { it.id }, SyncStatus.SYNCED.name)
                        }
                    }
                }
            }
            customerId
        }
    }

    override suspend fun createInstallmentPlan(
        customerId: String,
        itemName: String,
        totalCashPrice: Double,
        downPayment: Double,
        interestRatePercent: Double,
        installmentCount: Int,
        intervalMonths: Int,
        startDateMillis: Long,
        notes: String
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val cleanItem = itemName.trim()
            require(cleanItem.isNotEmpty()) { "يرجى إدخال اسم السلعة أو العقد" }
            require(totalCashPrice > 0.0) { "يرجى إدخال سعر إجمالي صحيح" }

            val now = System.currentTimeMillis()
            val safeDown = downPayment.coerceIn(0.0, totalCashPrice)
            val safeRate = interestRatePercent.coerceAtLeast(0.0)
            val safeCount = installmentCount.coerceIn(1, 120)
            val safeInterval = intervalMonths.coerceIn(1, 12)
            val financedTotal = InstallmentCalculator.calculateFinancedTotal(
                totalCashPrice = totalCashPrice,
                downPayment = safeDown,
                interestRatePercent = safeRate
            )

            val planId = UUID.randomUUID().toString()
            val payments = InstallmentCalculator.generateSchedule(
                planId = planId,
                customerId = customerId,
                financedTotal = financedTotal,
                installmentCount = safeCount,
                startDateMillis = startDateMillis,
                intervalMonths = safeInterval,
                nowMillis = now
            )
            val plan = InstallmentPlan(
                id = planId,
                customerId = customerId,
                itemName = cleanItem,
                totalCashPrice = totalCashPrice,
                downPayment = safeDown,
                interestRatePercent = safeRate,
                financedTotal = financedTotal,
                installmentCount = safeCount,
                intervalMonths = safeInterval,
                startDateMillis = startDateMillis,
                status = InstallmentCalculator.resolvePlanStatus(payments, now),
                notes = notes.trim(),
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING_UPLOAD
            )

            database.withTransaction {
                planDao.upsert(plan.toEntity())
                paymentDao.upsertAll(payments.map { it.toEntity() })
            }

            val uid = activeUserId
            val ds = firestoreDataSource
            if (uid != null && ds != null && networkMonitor.isCurrentlyConnected()) {
                runCatching {
                    ds.createPlanWithInstallments(uid, plan, payments)
                    database.withTransaction {
                        planDao.updateSyncStatus(listOf(plan.id), SyncStatus.SYNCED.name)
                        paymentDao.updateSyncStatus(payments.map { it.id }, SyncStatus.SYNCED.name)
                    }
                }
            }
            planId
        }
    }

    override suspend fun recordPayment(
        paymentId: String,
        paidAmount: Double,
        paymentMethod: PaymentMethod,
        receiptNumber: String,
        notes: String
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(paidAmount > 0.0) { "يجب أن يكون مبلغ الدفعة أكبر من صفر" }

            var savedPayment: InstallmentPayment? = null
            var savedPlan: InstallmentPlan? = null
            var previousPlanCompleted = false

            database.withTransaction {
                val existingEntity = paymentDao.getByIdOnce(paymentId)
                    ?: throw IllegalArgumentException("القسط غير موجود")
                val existing = existingEntity.toDomain()
                val now = System.currentTimeMillis()

                val newPaidTotal = InstallmentCalculator.roundToTwoDecimals(
                    (existing.amountPaid + paidAmount).coerceAtMost(existing.amountDue)
                )
                val newStatus = InstallmentCalculator.resolvePaymentStatus(
                    amountDue = existing.amountDue,
                    amountPaid = newPaidTotal,
                    dueDateMillis = existing.dueDateMillis,
                    nowMillis = now
                )
                val updatedPayment = existing.copy(
                    amountPaid = newPaidTotal,
                    paidDateMillis = now,
                    status = newStatus,
                    paymentMethod = paymentMethod,
                    receiptNumber = receiptNumber.trim(),
                    notes = notes.trim(),
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_UPLOAD
                )
                paymentDao.upsert(updatedPayment.toEntity())
                savedPayment = updatedPayment

                val planPayments = paymentDao.getByPlanIdOnce(existing.planId).map { it.toDomain() }
                val planEntity = planDao.getByIdOnce(existing.planId)
                if (planEntity != null) {
                    val plan = planEntity.toDomain()
                    previousPlanCompleted = plan.status.name == "COMPLETED"
                    val newPlanStatus = InstallmentCalculator.resolvePlanStatus(planPayments, now)
                    val updatedPlan = plan.copy(
                        status = newPlanStatus,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_UPLOAD
                    )
                    planDao.update(updatedPlan.toEntity())
                    savedPlan = updatedPlan
                }
            }

            val uid = activeUserId
            val ds = firestoreDataSource
            val paymentToSync = savedPayment
            if (uid != null && ds != null && paymentToSync != null && networkMonitor.isCurrentlyConnected()) {
                runCatching {
                    ds.recordInstallmentPayment(
                        userId = uid,
                        updatedInstallment = paymentToSync,
                        updatedPlan = savedPlan,
                        updatePlanStatus = !previousPlanCompleted
                    )
                    database.withTransaction {
                        paymentDao.updateSyncStatus(listOf(paymentToSync.id), SyncStatus.SYNCED.name)
                        savedPlan?.let { planDao.updateSyncStatus(listOf(it.id), SyncStatus.SYNCED.name) }
                    }
                }
            }
        }
    }

    override suspend fun deleteCustomer(customerId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val planIds = planDao.getAllOnce().filter { it.customerId == customerId }.map { it.id }
            val installmentIds = paymentDao.getAllOnce().filter { it.customerId == customerId }.map { it.id }

            customerDao.deleteById(customerId)

            val uid = activeUserId
            val ds = firestoreDataSource
            if (uid != null && ds != null && networkMonitor.isCurrentlyConnected()) {
                runCatching {
                    ds.deleteCustomerCascade(uid, customerId, planIds, installmentIds)
                }
            }
        }
    }

    override suspend fun refreshOverdueStatuses(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val now = System.currentTimeMillis()
            val dayStart = InstallmentCalculator.startOfDay(now)
            val allPayments = paymentDao.getAllOnce().map { it.toDomain() }
            val updatedPayments = allPayments.mapNotNull { payment ->
                val computed = InstallmentCalculator.resolvePaymentStatus(
                    amountDue = payment.amountDue,
                    amountPaid = payment.amountPaid,
                    dueDateMillis = payment.dueDateMillis,
                    nowMillis = now
                )
                if (computed != payment.status && payment.dueDateMillis < dayStart) {
                    payment.copy(status = computed).toEntity()
                } else {
                    null
                }
            }
            if (updatedPayments.isNotEmpty()) {
                paymentDao.upsertAll(updatedPayments)
            }
        }
    }

    override suspend fun syncWithFirestore(userId: String): Result<Int> = withContext(ioDispatcher) {
        val ds = firestoreDataSource ?: return@withContext Result.success(0)
        if (!networkMonitor.isCurrentlyConnected()) {
            val pending = countPendingLocalChanges()
            _syncState.value = SyncState.OfflineQueued(pending)
            return@withContext Result.failure(OfflineException())
        }

        _syncState.value = SyncState.Syncing("جارٍ المزامنة مع Firebase Firestore...")
        try {
            val pendingCustomers = customerDao.getPendingSync().map { it.toDomain() }
            val pendingPlans = planDao.getPendingSync().map { it.toDomain() }
            val pendingPayments = paymentDao.getPendingSync().map { it.toDomain() }

            val pushed = ds.pushPendingToFirestore(
                userId = userId,
                customers = pendingCustomers,
                plans = pendingPlans,
                installments = pendingPayments
            )

            val remoteCustomers = ds.getCustomersOnce(userId)
            val remotePlans = ds.getPlansOnce(userId)
            val remotePayments = ds.getInstallmentsOnce(userId)

            val now = System.currentTimeMillis()
            database.withTransaction {
                if (pendingCustomers.isNotEmpty()) {
                    customerDao.updateSyncStatus(pendingCustomers.map { it.id }, SyncStatus.SYNCED.name)
                }
                if (pendingPlans.isNotEmpty()) {
                    planDao.updateSyncStatus(pendingPlans.map { it.id }, SyncStatus.SYNCED.name)
                }
                if (pendingPayments.isNotEmpty()) {
                    paymentDao.updateSyncStatus(pendingPayments.map { it.id }, SyncStatus.SYNCED.name)
                }
                mergeRemoteIntoRoom(remoteCustomers, remotePlans, remotePayments)
            }

            val total = pushed + remoteCustomers.size + remotePlans.size + remotePayments.size
            _syncState.value = SyncState.Success(total, now)
            Result.success(total)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "تعذّرت المزامنة مع Firebase Firestore"
            _syncState.value = SyncState.Error(msg)
            Result.failure(e)
        }
    }

    override fun observeFirestoreRealtime(userId: String): Flow<Result<Int>> {
        val ds = firestoreDataSource ?: return flowOf(Result.success(0))
        return combine(
            ds.observeCustomers(userId),
            ds.observePlans(userId),
            ds.observeInstallments(userId)
        ) { remoteCustomers, remotePlans, remotePayments ->
            database.withTransaction {
                mergeRemoteIntoRoom(remoteCustomers, remotePlans, remotePayments)
            }
            val total = remoteCustomers.size + remotePlans.size + remotePayments.size
            Result.success(total)
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(Result.failure(rootError))
        }.flowOn(ioDispatcher)
    }

    private suspend fun mergeRemoteIntoRoom(
        remoteCustomers: List<Customer>,
        remotePlans: List<InstallmentPlan>,
        remotePayments: List<InstallmentPayment>
    ) {
        val localCustomers = customerDao.getAllOnce().associateBy { it.id }
        val customersToSave = remoteCustomers.filter { remote ->
            val local = localCustomers[remote.id]
            local == null || (local.syncStatus == SyncStatus.SYNCED.name && remote.updatedAt >= local.updatedAt)
        }
        if (customersToSave.isNotEmpty()) {
            customerDao.upsertAll(customersToSave.map { it.toEntity() })
        }

        val localPlans = planDao.getAllOnce().associateBy { it.id }
        val plansToSave = remotePlans.filter { remote ->
            val local = localPlans[remote.id]
            local == null || (local.syncStatus == SyncStatus.SYNCED.name && remote.updatedAt >= local.updatedAt)
        }
        if (plansToSave.isNotEmpty()) {
            planDao.upsertAll(plansToSave.map { it.toEntity() })
        }

        val localPayments = paymentDao.getAllOnce().associateBy { it.id }
        val paymentsToSave = remotePayments.filter { remote ->
            val local = localPayments[remote.id]
            local == null || (local.syncStatus == SyncStatus.SYNCED.name && remote.updatedAt >= local.updatedAt)
        }
        if (paymentsToSave.isNotEmpty()) {
            paymentDao.upsertAll(paymentsToSave.map { it.toEntity() })
        }
    }

    override suspend fun updateConnectedAccount(email: String) = withContext(ioDispatcher) {
        val current = syncMetadataDao.getOnce() ?: SyncMetadataEntity(
            id = 1,
            accountEmail = "",
            spreadsheetId = "",
            spreadsheetTitle = GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE,
            lastSyncTimestamp = 0L,
            lastSyncMessage = "لم تتم المزامنة بعد"
        )
        syncMetadataDao.upsert(current.copy(accountEmail = email))
    }

    override suspend fun updateSpreadsheetId(spreadsheetId: String) = withContext(ioDispatcher) {
        val current = syncMetadataDao.getOnce() ?: SyncMetadataEntity(
            id = 1,
            accountEmail = "",
            spreadsheetId = "",
            spreadsheetTitle = GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE,
            lastSyncTimestamp = 0L,
            lastSyncMessage = "لم تتم المزامنة بعد"
        )
        syncMetadataDao.upsert(current.copy(spreadsheetId = spreadsheetId.trim()))
    }

    override suspend fun syncWithGoogleSheets(accessToken: String): Result<Int> = withContext(ioDispatcher) {
        if (!networkMonitor.isCurrentlyConnected()) {
            val pending = countPendingLocalChanges()
            _syncState.value = SyncState.OfflineQueued(pending)
            return@withContext Result.failure(OfflineException())
        }

        _syncState.value = SyncState.Syncing("جارٍ الاتصال بـ Google Sheets...")
        try {
            val customers = customerDao.getAllOnce().map { it.toDomain() }
            val plans = planDao.getAllOnce().map { it.toDomain() }
            val payments = paymentDao.getAllOnce().map { it.toDomain() }
            val metadata = syncMetadataDao.getOnce()?.toDomain() ?: SyncMetadata()

            _syncState.value = SyncState.Syncing("جارٍ رفع ${customers.size + plans.size + payments.size} سجل...")

            val report = sheetsSyncManager.pushAllToSheets(
                accessToken = accessToken,
                existingSpreadsheetId = metadata.spreadsheetId,
                spreadsheetTitle = metadata.spreadsheetTitle,
                customers = customers,
                plans = plans,
                payments = payments
            )

            val now = System.currentTimeMillis()
            database.withTransaction {
                if (customers.isNotEmpty()) {
                    customerDao.updateSyncStatus(customers.map { it.id }, SyncStatus.SYNCED.name)
                }
                if (plans.isNotEmpty()) {
                    planDao.updateSyncStatus(plans.map { it.id }, SyncStatus.SYNCED.name)
                }
                if (payments.isNotEmpty()) {
                    paymentDao.updateSyncStatus(payments.map { it.id }, SyncStatus.SYNCED.name)
                }
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        id = 1,
                        accountEmail = metadata.accountEmail,
                        spreadsheetId = report.spreadsheetId,
                        spreadsheetTitle = report.spreadsheetTitle,
                        lastSyncTimestamp = now,
                        lastSyncMessage = "تمت مزامنة ${report.totalRowsSynced} سجل بنجاح"
                    )
                )
            }

            _syncState.value = SyncState.Success(report.totalRowsSynced, now)
            Result.success(report.totalRowsSynced)
        } catch (e: OfflineException) {
            val pending = countPendingLocalChanges()
            _syncState.value = SyncState.OfflineQueued(pending)
            Result.failure(e)
        } catch (e: AuthExpiredException) {
            _syncState.value = SyncState.AuthRequired(e.message ?: "يرجى إعادة تفويض حساب Google")
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "تعذّرت المزامنة مع Google Sheets"
            _syncState.value = SyncState.Error(msg)
            Result.failure(e)
        }
    }

    override suspend fun pullFromGoogleSheets(accessToken: String): Result<Int> = withContext(ioDispatcher) {
        if (!networkMonitor.isCurrentlyConnected()) {
            val pending = countPendingLocalChanges()
            _syncState.value = SyncState.OfflineQueued(pending)
            return@withContext Result.failure(OfflineException())
        }

        _syncState.value = SyncState.Syncing("جارٍ الاستيراد من Google Sheets...")
        try {
            val metadata = syncMetadataDao.getOnce()?.toDomain() ?: SyncMetadata()
            val payload = sheetsSyncManager.pullAllFromSheets(
                accessToken = accessToken,
                existingSpreadsheetId = metadata.spreadsheetId,
                spreadsheetTitle = metadata.spreadsheetTitle
            )

            val now = System.currentTimeMillis()
            val totalImported = payload.customers.size + payload.plans.size + payload.payments.size

            database.withTransaction {
                if (payload.customers.isNotEmpty()) {
                    customerDao.upsertAll(payload.customers.map { it.toEntity() })
                }
                if (payload.plans.isNotEmpty()) {
                    planDao.upsertAll(payload.plans.map { it.toEntity() })
                }
                if (payload.payments.isNotEmpty()) {
                    paymentDao.upsertAll(payload.payments.map { it.toEntity() })
                }
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        id = 1,
                        accountEmail = metadata.accountEmail,
                        spreadsheetId = payload.spreadsheetId,
                        spreadsheetTitle = metadata.spreadsheetTitle,
                        lastSyncTimestamp = now,
                        lastSyncMessage = "تم استيراد $totalImported سجل من السحابة"
                    )
                )
            }

            _syncState.value = SyncState.Success(totalImported, now)
            Result.success(totalImported)
        } catch (e: OfflineException) {
            val pending = countPendingLocalChanges()
            _syncState.value = SyncState.OfflineQueued(pending)
            Result.failure(e)
        } catch (e: AuthExpiredException) {
            _syncState.value = SyncState.AuthRequired(e.message ?: "يرجى إعادة تفويض حساب Google")
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "تعذّر الاستيراد من Google Sheets"
            _syncState.value = SyncState.Error(msg)
            Result.failure(e)
        }
    }

    override suspend fun exportDatabaseBackup(): Result<Pair<String, DatabaseBackupSummary>> =
        withContext(ioDispatcher) {
            runCatching {
                val customers = customerDao.getAllOnce()
                val plans = planDao.getAllOnce()
                val installments = paymentDao.getAllOnce()
                val now = System.currentTimeMillis()
                val jsonContent = backupManager.serializeBackup(
                    customers = customers,
                    plans = plans,
                    installments = installments,
                    exportedAtMillis = now
                )
                val summary = DatabaseBackupSummary(
                    customerCount = customers.size,
                    planCount = plans.size,
                    installmentCount = installments.size,
                    exportedAtMillis = now
                )
                jsonContent to summary
            }
        }

    override suspend fun previewDatabaseBackup(rawJson: String): Result<DatabaseBackupPreview> =
        withContext(ioDispatcher) {
            runCatching {
                val payload = backupManager.parseAndValidate(rawJson)
                DatabaseBackupPreview(
                    rawJson = rawJson,
                    schemaVersion = payload.schemaVersion,
                    exportedAtMillis = payload.exportedAtMillis,
                    customerCount = payload.customers.size,
                    planCount = payload.plans.size,
                    installmentCount = payload.installments.size
                )
            }
        }

    override suspend fun importDatabaseBackup(
        rawJson: String,
        replaceExisting: Boolean
    ): Result<DatabaseBackupSummary> = withContext(ioDispatcher) {
        runCatching {
            val payload = backupManager.parseAndValidate(rawJson)
            val customerEntities = payload.customers.map {
                it.toEntity(syncStatus = SyncStatus.PENDING_UPLOAD.name)
            }
            val planEntities = payload.plans.map {
                it.toEntity(syncStatus = SyncStatus.PENDING_UPLOAD.name)
            }
            val installmentEntities = payload.installments.map {
                it.toEntity(syncStatus = SyncStatus.PENDING_UPLOAD.name)
            }

            val deletedCustomersMap = mutableMapOf<String, Pair<List<String>, List<String>>>()

            database.withTransaction {
                if (replaceExisting) {
                    val existingCustomers = customerDao.getAllOnce()
                    val existingPlans = planDao.getAllOnce()
                    val existingInstallments = paymentDao.getAllOnce()
                    val importedCustomerIds = customerEntities.map { it.id }.toSet()
                    val importedPlanIds = planEntities.map { it.id }.toSet()
                    val importedInstallmentIds = installmentEntities.map { it.id }.toSet()

                    val plansByCustomer = existingPlans
                        .filterNot { it.id in importedPlanIds }
                        .groupBy { it.customerId }
                    val installmentsByCustomer = existingInstallments
                        .filterNot { it.id in importedInstallmentIds }
                        .groupBy { it.customerId }

                    existingCustomers
                        .map { it.id }
                        .filterNot { it in importedCustomerIds }
                        .forEach { oldCid ->
                            deletedCustomersMap[oldCid] =
                                (plansByCustomer[oldCid].orEmpty().map { it.id }) to
                                    (installmentsByCustomer[oldCid].orEmpty().map { it.id })
                        }

                    existingCustomers.forEach { installmentDao.deleteCustomerById(it.id) }
                }

                if (customerEntities.isNotEmpty()) {
                    installmentDao.insertCustomers(customerEntities)
                }
                planEntities.forEach { installmentDao.insertPlan(it) }
                if (installmentEntities.isNotEmpty()) {
                    installmentDao.insertInstallments(installmentEntities)
                }
            }

            val uid = activeUserId
            val ds = firestoreDataSource
            if (uid != null && ds != null && networkMonitor.isCurrentlyConnected()) {
                runCatching {
                    if (replaceExisting && deletedCustomersMap.isNotEmpty()) {
                        deletedCustomersMap.forEach { (oldCustomerId, children) ->
                            ds.deleteCustomerCascade(uid, oldCustomerId, children.first, children.second)
                        }
                    }
                    ds.pushPendingToFirestore(
                        userId = uid,
                        customers = customerEntities.map { it.toDomain() },
                        plans = planEntities.map { it.toDomain() },
                        installments = installmentEntities.map { it.toDomain() }
                    )
                    database.withTransaction {
                        if (customerEntities.isNotEmpty()) {
                            customerDao.updateSyncStatus(customerEntities.map { it.id }, SyncStatus.SYNCED.name)
                        }
                        if (planEntities.isNotEmpty()) {
                            planDao.updateSyncStatus(planEntities.map { it.id }, SyncStatus.SYNCED.name)
                        }
                        if (installmentEntities.isNotEmpty()) {
                            paymentDao.updateSyncStatus(installmentEntities.map { it.id }, SyncStatus.SYNCED.name)
                        }
                    }
                }
            }

            val total = customerEntities.size + planEntities.size + installmentEntities.size
            val now = System.currentTimeMillis()
            val currentMeta = syncMetadataDao.getOnce() ?: SyncMetadataEntity(
                id = 1,
                accountEmail = "",
                spreadsheetId = "",
                spreadsheetTitle = GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE,
                lastSyncTimestamp = now,
                lastSyncMessage = ""
            )
            syncMetadataDao.upsert(
                currentMeta.copy(
                    lastSyncTimestamp = now,
                    lastSyncMessage = "تم استيراد $total سجل من نسخة احتياطية"
                )
            )
            _syncState.value = SyncState.Success(total, now)

            DatabaseBackupSummary(
                customerCount = customerEntities.size,
                planCount = planEntities.size,
                installmentCount = installmentEntities.size,
                exportedAtMillis = payload.exportedAtMillis
            )
        }
    }

    override fun markOfflineQueued(pendingCount: Int) {
        _syncState.value = SyncState.OfflineQueued(pendingCount)
    }

    override fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    private suspend fun countPendingLocalChanges(): Int {
        return customerDao.getPendingSync().size +
            planDao.getPendingSync().size +
            paymentDao.getPendingSync().size
    }
}
