package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PlanWithInstallmentEntities(
    @Embedded val plan: InstallmentPlanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "planId"
    )
    val installments: List<InstallmentPaymentEntity>
)

data class CustomerWithInstallments(
    @Embedded val customer: CustomerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customerId"
    )
    val installments: List<InstallmentPaymentEntity>
)

data class CustomerWithPlansAndInstallments(
    @Embedded val customer: CustomerEntity,
    @Relation(
        entity = InstallmentPlanEntity::class,
        parentColumn = "id",
        entityColumn = "customerId"
    )
    val plans: List<PlanWithInstallmentEntities>,
    @Relation(
        parentColumn = "id",
        entityColumn = "customerId"
    )
    val installments: List<InstallmentPaymentEntity>
)
