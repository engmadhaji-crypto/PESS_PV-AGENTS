package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.Customer
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.InstallmentPlan
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FirestoreInstallmentRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createCustomerWithOptionalPlan_authenticatedOwner_persistsAndObservesRecords() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val dataSource = FirestoreInstallmentDataSource(firestore)

        val now = System.currentTimeMillis()
        val customerId = UUID.randomUUID().toString()
        val planId = UUID.randomUUID().toString()
        val instId = UUID.randomUUID().toString()

        val customer = Customer(
            id = customerId,
            fullName = "أحمد محمود",
            phone = "0501112233",
            nationalId = "1010101010",
            address = "الرياض",
            notes = "",
            createdAt = now,
            updatedAt = now
        )
        val plan = InstallmentPlan(
            id = planId,
            customerId = customerId,
            itemName = "هاتف ذكي",
            totalCashPrice = 2000.0,
            downPayment = 500.0,
            interestRatePercent = 0.0,
            financedTotal = 1500.0,
            installmentCount = 1,
            intervalMonths = 1,
            startDateMillis = now + 86_400_000L,
            status = PlanStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        val installment = InstallmentPayment(
            id = instId,
            planId = planId,
            customerId = customerId,
            installmentNumber = 1,
            dueDateMillis = now + 86_400_000L,
            amountDue = 1500.0,
            amountPaid = 0.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.CASH,
            updatedAt = now
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.createCustomerWithOptionalPlan(
                userId = uid,
                customer = customer,
                plan = plan,
                installments = listOf(installment)
            )
        }

        val fetchedCustomers = withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.getCustomersOnce(uid)
        }
        assertTrue(fetchedCustomers.any { it.id == customerId && it.fullName == "أحمد محمود" })

        val observedInstallments = withTimeout(FLOW_TIMEOUT_MS) {
            dataSource.observeInstallments(uid).first { list -> list.any { it.id == instId } }
        }
        assertEquals(1500.0, observedInstallments.first { it.id == instId }.amountDue, 0.01)
    }

    @Test
    fun recordInstallmentPayment_authenticatedOwner_updatesToPaidAndEnforcesTerminalLock() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val dataSource = FirestoreInstallmentDataSource(firestore)

        val now = System.currentTimeMillis()
        val customerId = UUID.randomUUID().toString()
        val planId = UUID.randomUUID().toString()
        val instId = UUID.randomUUID().toString()

        val customer = Customer(
            id = customerId,
            fullName = "سارة خالد",
            phone = "0552223344",
            createdAt = now,
            updatedAt = now
        )
        val plan = InstallmentPlan(
            id = planId,
            customerId = customerId,
            itemName = "جهاز لوحي",
            totalCashPrice = 1000.0,
            downPayment = 0.0,
            interestRatePercent = 0.0,
            financedTotal = 1000.0,
            installmentCount = 1,
            intervalMonths = 1,
            startDateMillis = now,
            status = PlanStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        val installment = InstallmentPayment(
            id = instId,
            planId = planId,
            customerId = customerId,
            installmentNumber = 1,
            dueDateMillis = now + 86_400_000L,
            amountDue = 1000.0,
            amountPaid = 0.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.CASH,
            updatedAt = now
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.createCustomerWithOptionalPlan(uid, customer, plan, listOf(installment))
        }

        val paidInstallment = installment.copy(
            amountPaid = 1000.0,
            paidDateMillis = now + 1000L,
            status = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            receiptNumber = "INV-01",
            updatedAt = now + 1000L
        )
        val completedPlan = plan.copy(status = PlanStatus.COMPLETED, updatedAt = now + 1000L)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.recordInstallmentPayment(uid, paidInstallment, completedPlan, updatePlanStatus = true)
        }

        // Terminal state lock: attempting to mutate a PAID installment again must be rejected
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                dataSource.recordInstallmentPayment(
                    uid,
                    paidInstallment.copy(amountPaid = 500.0, status = PaymentStatus.PARTIAL),
                    null,
                    updatePlanStatus = false
                )
            }
            fail("Expected PERMISSION_DENIED when mutating a PAID installment")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    @Test
    fun crossUserAccess_bobCannotReadAliceCustomers_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceSource = FirestoreInstallmentDataSource(firestore)

        val now = System.currentTimeMillis()
        val customerId = UUID.randomUUID().toString()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSource.createCustomerWithOptionalPlan(
                userId = aliceUid,
                customer = Customer(
                    id = customerId,
                    fullName = "عميل سري",
                    phone = "0509998877",
                    createdAt = now,
                    updatedAt = now
                ),
                plan = null,
                installments = emptyList()
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobSource = FirestoreInstallmentDataSource(firestore)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobSource.getCustomerById(aliceUid, customerId)
            }
            fail("Expected PERMISSION_DENIED on cross-user read")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    @Test
    fun observeCustomers_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val dataSource = FirestoreInstallmentDataSource(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                dataSource.observeCustomers("any_user_id").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
