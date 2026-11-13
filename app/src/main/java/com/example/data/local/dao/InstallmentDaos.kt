package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.InstallmentPlanEntity
import com.example.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(customers: List<CustomerEntity>)

    @Query("UPDATE customers SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, status: String)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface InstallmentPlanDao {
    @Query("SELECT * FROM installment_plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plans WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeByCustomerId(customerId: String): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plans WHERE id = :planId LIMIT 1")
    fun observeById(planId: String): Flow<InstallmentPlanEntity?>

    @Query("SELECT * FROM installment_plans WHERE id = :planId LIMIT 1")
    suspend fun getByIdOnce(planId: String): InstallmentPlanEntity?

    @Query("SELECT * FROM installment_plans ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<InstallmentPlanEntity>

    @Query("SELECT * FROM installment_plans WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<InstallmentPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: InstallmentPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(plans: List<InstallmentPlanEntity>)

    @Update
    suspend fun update(plan: InstallmentPlanEntity)

    @Query("UPDATE installment_plans SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, status: String)
}

@Dao
interface InstallmentPaymentDao {
    @Query("SELECT * FROM installments ORDER BY dueDateMillis ASC")
    fun observeAll(): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY installmentNumber ASC")
    fun observeByPlanId(planId: String): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installments WHERE customerId = :customerId ORDER BY dueDateMillis ASC")
    fun observeByCustomerId(customerId: String): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installments WHERE id = :paymentId LIMIT 1")
    suspend fun getByIdOnce(paymentId: String): InstallmentPaymentEntity?

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY installmentNumber ASC")
    suspend fun getByPlanIdOnce(planId: String): List<InstallmentPaymentEntity>

    @Query("SELECT * FROM installments ORDER BY dueDateMillis ASC")
    suspend fun getAllOnce(): List<InstallmentPaymentEntity>

    @Query("SELECT * FROM installments WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<InstallmentPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: InstallmentPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(payments: List<InstallmentPaymentEntity>)

    @Query("UPDATE installments SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, status: String)

    @Transaction
    suspend fun insertPlanWithPayments(
        plan: InstallmentPlanEntity,
        payments: List<InstallmentPaymentEntity>,
        planDao: InstallmentPlanDao
    ) {
        planDao.upsert(plan)
        upsertAll(payments)
    }
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun observe(): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun getOnce(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)
}
