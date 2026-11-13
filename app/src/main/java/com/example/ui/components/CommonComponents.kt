package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanStatus
import com.example.domain.model.SyncStatus
import com.example.ui.theme.LocalExtendedColors

@Composable
fun PaymentStatusBadge(
    status: PaymentStatus,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val (bg, fg) = when (status) {
        PaymentStatus.PAID -> extended.paidContainer to extended.paid
        PaymentStatus.PARTIAL -> extended.partialContainer to extended.partial
        PaymentStatus.OVERDUE -> extended.overdueContainer to extended.overdue
        PaymentStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = status.labelAr,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PlanStatusBadge(
    status: PlanStatus,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val (bg, fg) = when (status) {
        PlanStatus.COMPLETED -> extended.paidContainer to extended.paid
        PlanStatus.OVERDUE -> extended.overdueContainer to extended.overdue
        PlanStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = status.labelAr,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SyncDotBadge(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val color = when (syncStatus) {
        SyncStatus.SYNCED -> extended.paid
        SyncStatus.PENDING_UPLOAD -> extended.partial
        SyncStatus.PENDING_DELETE -> extended.overdue
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = syncStatus.labelAr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConnectivityBanner(
    isOnline: Boolean,
    pendingCount: Int,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = !isOnline || pendingCount > 0, modifier = modifier) {
        val bg = if (!isOnline) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val fg = if (!isOnline) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Surface(
            color = bg,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isOnline) Icons.Rounded.CloudQueue else Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (!isOnline) {
                            "وضع عدم الاتصال — التغييرات محفوظة محلياً ($pendingCount معلّقة)"
                        } else {
                            "يوجد $pendingCount سجل بانتظار المزامنة السحابية"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = fg,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (isOnline) {
                    TextButton(
                        onClick = onSyncClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_sync_button")
                    ) {
                        Text(
                            text = "مزامنة",
                            style = MaterialTheme.typography.labelMedium,
                            color = fg,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val fillColor = MaterialTheme.colorScheme.secondaryContainer
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = radius
        )
        if (clamped > 0f) {
            drawRoundRect(
                color = fillColor,
                size = Size(width = size.width * clamped, height = size.height),
                cornerRadius = radius
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("empty_state_action_button")
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SyncStatusPill(
    isOnline: Boolean,
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val (icon, label, tint) = when {
        !isOnline -> Triple(Icons.Rounded.CloudOff, "محلي", MaterialTheme.colorScheme.error)
        pendingCount > 0 -> Triple(Icons.Rounded.CloudQueue, "$pendingCount معلّق", extended.partial)
        else -> Triple(Icons.Rounded.CloudDone, "متزامن", extended.paid)
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("sync_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
