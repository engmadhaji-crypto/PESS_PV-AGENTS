package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.model.Customer
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.InstallmentPlan
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class FirestoreUserSnapshot(
    val customers: List<Customer>,
    val plans: List<InstallmentPlan>,
    val installments: List<InstallmentPayment>
)

class FirestoreInstallmentDataSource(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun customersCollection(userId: String) =
        db.collection("users").document(userId).collection("customers")

    private fun plansCollection(userId: String) =
        db.collection("users").document(userId).collection("plans")

    private fun installmentsCollection(userId: String) =
        db.collection("users").document(userId).collection("installments")

    fun observeCustomers(userId: String): Flow<List<Customer>> {
        val path = "users/$userId/customers"
        return customersCollection(userId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(
                    CustomerFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomain() }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePlans(userId: String): Flow<List<InstallmentPlan>> {
        val path = "users/$userId/plans"
        return plansCollection(userId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(
                    InstallmentPlanFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomain() }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeInstallments(userId: String): Flow<List<InstallmentPayment>> {
        val path = "users/$userId/installments"
        return installmentsCollection(userId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(
                    InstallmentFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomain() }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun getCustomersOnce(userId: String): List<Customer> {
        val path = "users/$userId/customers"
        return try {
            customersCollection(userId).get().await()
                .toObjects(
                    CustomerFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomain() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun getCustomerById(userId: String, customerId: String): Customer? {
        val docRef = customersCollection(userId).document(customerId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) null
            else snap.toObject(
                CustomerFirestoreDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun getPlansOnce(userId: String): List<InstallmentPlan> {
        val path = "users/$userId/plans"
        return try {
            plansCollection(userId).get().await()
                .toObjects(
                    InstallmentPlanFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomain() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun getInstallmentsOnce(userId: String): List<InstallmentPayment> {
        val path = "users/$userId/installments"
        return try {
            installmentsCollection(userId).get().await()
                .toObjects(
                    InstallmentFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomain() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun createCustomerWithOptionalPlan(
        userId: String,
        customer: Customer,
        plan: InstallmentPlan?,
        installments: List<InstallmentPayment>
    ) {
        val customerRef = customersCollection(userId).document(customer.id)
        try {
            val batch = db.batch()
            batch.set(customerRef, customer.toFirestoreCreateMap(userId))
            if (plan != null) {
                val planRef = plansCollection(userId).document(plan.id)
                batch.set(planRef, plan.toFirestoreCreateMap(userId))
                installments.forEach { payment ->
                    val instRef = installmentsCollection(userId).document(payment.id)
                    batch.set(instRef, payment.toFirestoreCreateMap(userId))
                }
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, customerRef.path)
            throw e
        }
    }

    suspend fun updateCustomer(userId: String, customer: Customer) {
        val customerRef = customersCollection(userId).document(customer.id)
        try {
            customerRef.update(customer.toFirestoreUpdateMap()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, customerRef.path)
            throw e
        }
    }

    suspend fun createPlanWithInstallments(
        userId: String,
        plan: InstallmentPlan,
        installments: List<InstallmentPayment>
    ) {
        val planRef = plansCollection(userId).document(plan.id)
        try {
            val batch = db.batch()
            batch.set(planRef, plan.toFirestoreCreateMap(userId))
            installments.forEach { payment ->
                val instRef = installmentsCollection(userId).document(payment.id)
                batch.set(instRef, payment.toFirestoreCreateMap(userId))
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, planRef.path)
            throw e
        }
    }

    suspend fun recordInstallmentPayment(
        userId: String,
        updatedInstallment: InstallmentPayment,
        updatedPlan: InstallmentPlan?,
        updatePlanStatus: Boolean = true
    ) {
        val instRef = installmentsCollection(userId).document(updatedInstallment.id)
        try {
            val batch = db.batch()
            batch.update(instRef, updatedInstallment.toFirestorePaymentUpdateMap())
            if (updatedPlan != null) {
                val planRef = plansCollection(userId).document(updatedPlan.id)
                batch.update(planRef, updatedPlan.toFirestoreUpdateMap(includeStatus = updatePlanStatus))
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, instRef.path)
            throw e
        }
    }

    suspend fun deleteCustomerCascade(
        userId: String,
        customerId: String,
        planIds: List<String>,
        installmentIds: List<String>
    ) {
        val customerRef = customersCollection(userId).document(customerId)
        try {
            val batch = db.batch()
            installmentIds.forEach { id ->
                batch.delete(installmentsCollection(userId).document(id))
            }
            planIds.forEach { id ->
                batch.delete(plansCollection(userId).document(id))
            }
            batch.delete(customerRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, customerRef.path)
            throw e
        }
    }

    suspend fun pushPendingToFirestore(
        userId: String,
        customers: List<Customer>,
        plans: List<InstallmentPlan>,
        installments: List<InstallmentPayment>
    ): Int {
        if (customers.isEmpty() && plans.isEmpty() && installments.isEmpty()) return 0

        val existingCustomerIds = getCustomersOnce(userId).map { it.id }.toSet()
        val existingPlanIds = getPlansOnce(userId).map { it.id }.toSet()
        val existingInstallmentDocs = installmentsCollection(userId).get().await()
            .toObjects(
                InstallmentFirestoreDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).associateBy { it.id }

        val operations = mutableListOf<suspend () -> Unit>()
        var syncedCount = 0

        val batch = db.batch()
        var batchOps = 0

        customers.forEach { customer ->
            val ref = customersCollection(userId).document(customer.id)
            if (customer.id in existingCustomerIds) {
                batch.update(ref, customer.toFirestoreUpdateMap())
            } else {
                batch.set(ref, customer.toFirestoreCreateMap(userId))
            }
            batchOps++
            syncedCount++
        }

        plans.forEach { plan ->
            val ref = plansCollection(userId).document(plan.id)
            if (plan.id in existingPlanIds) {
                val existingPlan = getPlansOnce(userId).firstOrNull { it.id == plan.id }
                val canUpdateStatus = existingPlan?.status?.name != "COMPLETED"
                batch.update(ref, plan.toFirestoreUpdateMap(includeStatus = canUpdateStatus))
            } else {
                batch.set(ref, plan.toFirestoreCreateMap(userId))
            }
            batchOps++
            syncedCount++
        }

        installments.forEach { payment ->
            val ref = installmentsCollection(userId).document(payment.id)
            val existingInst = existingInstallmentDocs[payment.id]
            if (existingInst == null) {
                batch.set(ref, payment.toFirestoreCreateMap(userId))
                batchOps++
                syncedCount++
            } else if (existingInst.status != "PAID") {
                batch.update(ref, payment.toFirestorePaymentUpdateMap())
                batchOps++
                syncedCount++
            }
        }

        if (batchOps > 0) {
            try {
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "users/$userId")
                throw e
            }
        }
        return syncedCount
    }
}
