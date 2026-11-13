package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Customer
import com.example.domain.model.PlanWithPayments
import com.example.domain.util.InstallmentCalculator
import com.example.ui.components.CreatePlanDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PlanStatusBadge
import com.example.ui.components.SyncDotBadge
import com.example.ui.theme.LocalExtendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customer: Customer?,
    plans: List<PlanWithPayments>,
    onBack: () -> Unit,
    onExportPdf: () -> Unit,
    onCreatePlan: (
        itemName: String,
        totalCashPrice: Double,
        downPayment: Double,
        interestRatePercent: Double,
        installmentCount: Int,
        intervalMonths: Int,
        notes: String
    ) -> Unit,
    onOpenPlanDetail: (String) -> Unit,
    onDeleteCustomer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current
    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.customer_detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    if (customer != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_customer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "حذف العميل",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
            if (customer == null) {
                EmptyStateCard(
                    icon = Icons.Rounded.Description,
                    title = "ملف العميل غير متوفر",
                    subtitle = "قد يكون تم حذف هذا العميل.",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                val grandTotal = plans.sumOf { it.plan.downPayment + it.plan.financedTotal }
                val grandPaid = plans.sumOf { it.totalPaid }
                val grandRemaining = plans.sumOf { it.totalRemaining }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = customer.fullName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SyncDotBadge(syncStatus = customer.syncStatus)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "الجوال: ${customer.phone}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (customer.nationalId.isNotBlank()) {
                                            Text(
                                                text = "رقم الهوية: ${customer.nationalId}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (customer.address.isNotBlank()) {
                                            Text(
                                                text = "العنوان: ${customer.address}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val dialIntent = Intent(
                                                Intent.ACTION_DIAL,
                                                Uri.parse("tel:${customer.phone}")
                                            )
                                            runCatching { context.startActivity(dialIntent) }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Call,
                                            contentDescription = "اتصال",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("اتصال")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("الإجمالي", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            InstallmentCalculator.formatCurrency(grandTotal),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("المحصّل", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            InstallmentCalculator.formatCurrency(grandPaid),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = extended.paid,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("المتبقي", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            InstallmentCalculator.formatCurrency(grandRemaining),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (grandRemaining > 0.01) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                extended.paid
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons (Export PDF + Add Installment Plan)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showCreatePlanDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("add_plan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.add_installment_plan))
                            }
                            FilledTonalButton(
                                onClick = onExportPdf,
                                enabled = plans.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.export_pdf_statement))
                            }
                        }
                    }

                    item {
                        Text(
                            text = "عقود التقسيط (${plans.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (plans.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Rounded.Description,
                                title = "لا توجد عقود تقسيط",
                                subtitle = "أضف عقد تقسيط جديد لهذا العميل لتوليد جدول الأقساط تلقائياً.",
                                actionLabel = stringResource(R.string.add_installment_plan),
                                onAction = { showCreatePlanDialog = true }
                            )
                        }
                    } else {
                        items(plans, key = { it.plan.id }) { bundle ->
                            val progress = if (bundle.plan.financedTotal > 0.0) {
                                (bundle.payments.sumOf { it.amountPaid } / bundle.plan.financedTotal)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                            Surface(
                                onClick = { onOpenPlanDetail(bundle.plan.id) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("plan_card_${bundle.plan.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = bundle.plan.itemName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        PlanStatusBadge(status = bundle.plan.status)
                                    }

                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "مسدّد: ${bundle.paidInstallmentsCount} / ${bundle.payments.size} قسط",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "المتبقي: ${InstallmentCalculator.formatCurrency(bundle.totalRemaining)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlanDialog) {
        CreatePlanDialog(
            onDismiss = { showCreatePlanDialog = false },
            onConfirm = { itemName, total, down, rate, count, interval, notes ->
                onCreatePlan(itemName, total, down, rate, count, interval, notes)
                showCreatePlanDialog = false
            }
        )
    }

    if (showDeleteConfirm && customer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف العميل") },
            text = {
                Text("سيتم حذف ملف ${customer.fullName} وجميع عقود الأقساط المرتبطة به من قاعدة البيانات المحلية.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCustomer()
                    },
                    modifier = Modifier.testTag("confirm_delete_customer_button")
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
