package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CustomerWithSummary
import com.example.domain.util.InstallmentCalculator
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SyncDotBadge
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.viewmodel.CustomerFilter
import com.example.ui.viewmodel.CustomersUiState

@Composable
fun CustomersScreen(
    uiState: CustomersUiState,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (CustomerFilter) -> Unit,
    onCustomerClick: (String) -> Unit,
    onAddCustomerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCustomerClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("fab_add_customer")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_add_customer)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.customers_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${uiState.totalCount} عميل",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text(stringResource(R.string.search_customers_hint)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChanged("") },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_customers_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomerFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.selectedFilter == filter,
                                onClick = { onFilterSelected(filter) },
                                label = { Text(filter.labelAr) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("filter_chip_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.customers.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Rounded.Group,
                                title = stringResource(R.string.empty_customers_title),
                                subtitle = stringResource(R.string.empty_customers_subtitle),
                                actionLabel = stringResource(R.string.action_add_customer),
                                onAction = onAddCustomerClick
                            )
                        }
                    } else {
                        items(uiState.customers, key = { it.customer.id }) { summary ->
                            CustomerSummaryCard(
                                summary = summary,
                                onClick = { onCustomerClick(summary.customer.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerSummaryCard(
    summary: CustomerWithSummary,
    onClick: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val initial = summary.customer.fullName.firstOrNull()?.toString() ?: "ع"

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_card_${summary.customer.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Column {
                        Text(
                            text = summary.customer.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = summary.customer.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (summary.overdueCount > 0) {
                        Surface(
                            color = extended.overdueContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "${summary.overdueCount} متأخر",
                                style = MaterialTheme.typography.labelSmall,
                                color = extended.overdue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SyncDotBadge(syncStatus = summary.customer.syncStatus)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "المتبقي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = InstallmentCalculator.formatCurrency(summary.totalRemaining),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (summary.totalRemaining > 0.01) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            extended.paid
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "العقود النشطة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.activePlansCount}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "أقرب استحقاق",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.nextDueDateMillis?.let { InstallmentCalculator.formatDate(it) } ?: "مكتمل",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
