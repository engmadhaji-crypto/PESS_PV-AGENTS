package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DueAlertItem
import com.example.domain.model.PaymentMethod
import com.example.domain.util.InstallmentCalculator
import com.example.ui.components.ConnectivityBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.PortfolioProgressBar
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.components.SyncStatusPill
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.viewmodel.DashboardUiState
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onAddCustomerClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenCustomerDetail: (String) -> Unit,
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
    var paymentToCollect by remember { mutableStateOf<DueAlertItem?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.dashboard_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SyncStatusPill(
                        isOnline = uiState.isOnline,
                        pendingCount = uiState.stats.pendingSyncCount,
                        onClick = onOpenSyncSettings
                    )
                }
            }

            item {
                ConnectivityBanner(
                    isOnline = uiState.isOnline,
                    pendingCount = uiState.stats.pendingSyncCount,
                    onSyncClick = onSyncNowClick
                )
            }

            // Hero Ledger Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(extended.heroStart, extended.heroEnd)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = extended.onHero
                                )
                                Text(
                                    text = stringResource(R.string.metric_total_portfolio),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extended.onHero.copy(alpha = 0.9f)
                                )
                            }
                            Surface(
                                color = extended.onHero.copy(alpha = 0.16f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${(uiState.stats.collectionRate * 100f).roundToInt()}% محصّل",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extended.onHero,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = InstallmentCalculator.formatCurrency(uiState.stats.totalPortfolio),
                            style = MaterialTheme.typography.displaySmall,
                            color = extended.onHero
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        PortfolioProgressBar(progress = uiState.stats.collectionRate)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.metric_total_collected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extended.onHero.copy(alpha = 0.78f)
                                )
                                Text(
                                    text = InstallmentCalculator.formatCurrency(uiState.stats.totalCollected),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extended.onHero,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.metric_total_remaining),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extended.onHero.copy(alpha = 0.78f)
                                )
                                Text(
                                    text = InstallmentCalculator.formatCurrency(uiState.stats.totalRemaining),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extended.onHero,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onAddCustomerClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_add_customer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_add_customer))
                            }
                            FilledTonalButton(
                                onClick = onSyncNowClick,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = extended.onHero.copy(alpha = 0.16f),
                                    contentColor = extended.onHero
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_sync_now))
                            }
                        }
                    }
                }
            }

            // KPI Metric Cards Grid (2x2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiMiniCard(
                            title = stringResource(R.string.metric_overdue_count),
                            value = uiState.stats.overdueCount.toString(),
                            icon = Icons.Rounded.WarningAmber,
                            isAlert = uiState.stats.overdueCount > 0,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMiniCard(
                            title = "تستحق خلال 7 أيام",
                            value = uiState.stats.dueSoonCount.toString(),
                            icon = Icons.Rounded.Schedule,
                            isAlert = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiMiniCard(
                            title = stringResource(R.string.metric_active_plans),
                            value = uiState.stats.activePlansCount.toString(),
                            icon = Icons.Rounded.Description,
                            isAlert = false,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMiniCard(
                            title = "إجمالي العملاء",
                            value = uiState.stats.customerCount.toString(),
                            icon = Icons.Rounded.Group,
                            isAlert = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Due & Overdue Alerts Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_due_alerts),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (uiState.dueAlerts.isNotEmpty()) {
                        Text(
                            text = "${uiState.dueAlerts.size} قسط",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (uiState.dueAlerts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.CheckCircle,
                        title = stringResource(R.string.empty_alerts_title),
                        subtitle = stringResource(R.string.empty_alerts_subtitle)
                    )
                }
            } else {
                items(uiState.dueAlerts, key = { it.payment.id }) { alert ->
                    DueAlertCard(
                        alert = alert,
                        onOpenCustomer = { onOpenCustomerDetail(alert.payment.customerId) },
                        onCollectClick = { paymentToCollect = alert }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    paymentToCollect?.let { alert ->
        RecordPaymentDialog(
            payment = alert.payment,
            customerName = alert.customerName,
            onDismiss = { paymentToCollect = null },
            onConfirm = { amount, method, receipt, notes ->
                onRecordPayment(alert.payment.id, amount, method, receipt, notes)
                paymentToCollect = null
            }
        )
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val iconBg = if (isAlert) extended.overdueContainer else MaterialTheme.colorScheme.primaryContainer
    val iconFg = if (isAlert) extended.overdue else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isAlert) extended.overdue else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DueAlertCard(
    alert: DueAlertItem,
    onOpenCustomer: () -> Unit,
    onCollectClick: () -> Unit
) {
    Surface(
        onClick = onOpenCustomer,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("due_alert_card_${alert.payment.id}")
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
                        text = alert.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PaymentStatusBadge(status = alert.payment.status)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${alert.itemName} • قسط #${alert.payment.installmentNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "الاستحقاق: ${InstallmentCalculator.formatDate(alert.payment.dueDateMillis)} • المتبقي: ${
                        InstallmentCalculator.formatCurrency(alert.payment.remainingAmount)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onCollectClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("collect_alert_button_${alert.payment.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Payments,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_collect_now))
            }
        }
    }
}
