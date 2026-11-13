package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.CustomerWithInstallments
import com.example.data.local.entity.CustomerWithPlansAndInstallments
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.InstallmentPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    // --- عمليات الإضافة والتحديث (Insert / Upsert) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: InstallmentPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: InstallmentPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(installments: List<InstallmentPaymentEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateInstallment(installment: InstallmentPaymentEntity)

    @Transaction
    suspend fun insertCustomerWithPlanAndInstallments(
        customer: CustomerEntity,
        plan: InstallmentPlanEntity,
        installments: List<InstallmentPaymentEntity>
    ) {
        insertCustomer(customer)
        insertPlan(plan)
        insertInstallments(installments)
    }

    // --- استعلامات العملاء (Customers Queries) ---

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    fun observeCustomerById(customerId: String): Flow<CustomerEntity?>

    @Query(
        """
        SELECT * FROM customers
        WHERE fullName LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
           OR nationalId LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    // --- استعلامات الأقساط (Installments Queries) ---

    @Query("SELECT * FROM installments ORDER BY dueDateMillis ASC")
    fun observeAllInstallments(): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installments WHERE customerId = :customerId ORDER BY dueDateMillis ASC")
    fun observeInstallmentsByCustomer(customerId: String): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY installmentNumber ASC")
    fun observeInstallmentsByPlan(planId: String): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installments WHERE status = :status ORDER BY dueDateMillis ASC")
    fun observeInstallmentsByStatus(status: String): Flow<List<InstallmentPaymentEntity>>

    @Query(
        """
        SELECT * FROM installments
        WHERE status != 'PAID' AND dueDateMillis < :dayStartMillis
        ORDER BY dueDateMillis ASC
        """
    )
    fun observeOverdueInstallments(dayStartMillis: Long): Flow<List<InstallmentPaymentEntity>>

    @Query(
        """
        SELECT * FROM installments
        WHERE status != 'PAID' AND dueDateMillis BETWEEN :fromMillis AND :toMillis
        ORDER BY dueDateMillis ASC
        """
    )
    fun observeUpcomingInstallments(
        fromMillis: Long,
        toMillis: Long
    ): Flow<List<InstallmentPaymentEntity>>

    // --- الاستعلامات العلائقية بين العملاء والأقساط (@Relation) ---

    @Transaction
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeCustomersWithInstallments(): Flow<List<CustomerWithInstallments>>

    @Transaction
    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    fun observeCustomerWithInstallments(customerId: String): Flow<CustomerWithInstallments?>

    @Transaction
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeCustomersWithPlansAndInstallments(): Flow<List<CustomerWithPlansAndInstallments>>

    @Transaction
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    suspend fun getCustomersWithInstallmentsOnce(): List<CustomerWithInstallments>

    // --- الحذف ---

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteCustomerById(customerId: String)

    @Query("DELETE FROM installments WHERE id = :installmentId")
    suspend fun deleteInstallmentById(installmentId: String)
}
