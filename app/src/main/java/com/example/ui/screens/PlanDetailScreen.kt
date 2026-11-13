package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanWithPayments
import com.example.domain.util.InstallmentCalculator
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.PlanStatusBadge
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.theme.LocalExtendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    bundle: PlanWithPayments?,
    onBack: () -> Unit,
    onRecordPayment: (
        paymentId: String,
        paidAmount: Double,
        method: PaymentMethod,
        receiptNumber: String,
        notes: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    var selectedPayment by remember { mutableStateOf<InstallmentPayment?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("plan_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (bundle == null) {
                EmptyStateCard(
                    icon = Icons.Rounded.Description,
                    title = "العقد غير موجود",
                    subtitle = "تعذّر العثور على تفاصيل خطة التقسيط.",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bundle.plan.itemName,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    PlanStatusBadge(status = bundle.plan.status)
                                }
                                bundle.customer?.let { c ->
                                    Text(
                                        text = "العميل: ${c.fullName} (${c.phone})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("الدفعة المقدمة", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            InstallmentCalculator.formatCurrency(bundle.plan.downPayment),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("المحصّل الكلي", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            InstallmentCalculator.formatCurrency(bundle.totalPaid),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = extended.paid,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("المتبقي", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            InstallmentCalculator.formatCurrency(bundle.totalRemaining),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "جدول الأقساط (${bundle.payments.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(bundle.payments, key = { it.id }) { payment ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("installment_row_${payment.installmentNumber}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "قسط #${payment.installmentNumber}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        PaymentStatusBadge(status = payment.status)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "الاستحقاق: ${InstallmentCalculator.formatDate(payment.dueDateMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "المستحق: ${InstallmentCalculator.formatCurrency(payment.amountDue)} • المدفوع: ${
                                            InstallmentCalculator.formatCurrency(payment.amountPaid)
                                        }",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (payment.paidDateMillis != null) {
                                        Text(
                                            text = "تاريخ السداد: ${InstallmentCalculator.formatDate(payment.paidDateMillis)} (${payment.paymentMethod.labelAr})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extended.paid
                                        )
                                    }
                                }

                                if (payment.status == PaymentStatus.PAID || payment.remainingAmount <= 0.01) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "مدفوع بالكامل",
                                        tint = extended.paid,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    FilledTonalButton(
                                        onClick = { selectedPayment = payment },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("record_payment_btn_${payment.installmentNumber}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Payments,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.record_payment))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPayment?.let { payment ->
        RecordPaymentDialog(
            payment = payment,
            customerName = bundle?.customer?.fullName.orEmpty(),
            onDismiss = { selectedPayment = null },
            onConfirm = { amount, method, receipt, notes ->
                onRecordPayment(payment.id, amount, method, receipt, notes)
                selectedPayment = null
            }
        )
    }
}
