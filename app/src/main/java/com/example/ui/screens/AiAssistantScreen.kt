package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.AiStreamState
import com.example.data.ai.ReminderTone
import com.example.domain.model.DueAlertItem
import com.example.domain.util.InstallmentCalculator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    aiState: AiStreamState,
    dueAlerts: List<DueAlertItem>,
    onAnalyzePortfolio: () -> Unit,
    onDraftReminder: (DueAlertItem, ReminderTone) -> Unit,
    onAskQuestion: (String) -> Unit,
    onResetAiState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var questionInput by remember { mutableStateOf("") }
    var selectedAlertId by remember(dueAlerts) {
        mutableStateOf(dueAlerts.firstOrNull()?.payment?.id)
    }
    var selectedTone by remember { mutableStateOf(ReminderTone.POLITE) }
    var copiedFeedback by remember { mutableStateOf(false) }

    val selectedAlert = remember(dueAlerts, selectedAlertId) {
        dueAlerts.firstOrNull { it.payment.id == selectedAlertId } ?: dueAlerts.firstOrNull()
    }
    val isBusy = aiState is AiStreamState.Loading || aiState is AiStreamState.Streaming

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.ai_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = stringResource(R.string.ai_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Portfolio Analysis Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "تحليل المحفظة وخطة التحصيل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "احصل على تقييم ذكي فوري لنسبة التحصيل، أولويات الأقساط المتأخرة، وتوصيات تقليل التعثر.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                copiedFeedback = false
                                onAnalyzePortfolio()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_analyze_portfolio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تحليل المحفظة الآن")
                        }
                    }
                }
            }

            // Reminder Message Drafter
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MarkChatUnread,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "صياغة رسالة تذكير للعميل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (dueAlerts.isEmpty()) {
                            Text(
                                text = "لا توجد أقساط متأخرة أو مستحقة قريباً لصياغة تذكير لها.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "اختر القسط:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                dueAlerts.take(6).forEach { alert ->
                                    val selected = selectedAlert?.payment?.id == alert.payment.id
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedAlertId = alert.payment.id },
                                        label = {
                                            Text(
                                                "${alert.customerName} (${InstallmentCalculator.formatCurrency(alert.payment.remainingAmount)})"
                                            )
                                        }
                                    )
                                }
                            }

                            Text(
                                text = "نبرة الرسالة:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReminderTone.entries.forEach { tone ->
                                    FilterChip(
                                        selected = selectedTone == tone,
                                        onClick = { selectedTone = tone },
                                        label = { Text(tone.labelAr) }
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    copiedFeedback = false
                                    selectedAlert?.let { onDraftReminder(it, selectedTone) }
                                },
                                enabled = !isBusy && selectedAlert != null,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_draft_reminder_button")
                            ) {
                                Text("توليد رسالة التذكير")
                            }
                        }
                    }
                }
            }

            // Custom Financial Question
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "اسأل المساعد الذكي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = questionInput,
                            onValueChange = { questionInput = it },
                            label = { Text("مثال: ما أفضل نسبة ربح لعقد تقسيط لمدة 12 شهراً؟") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_question_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    copiedFeedback = false
                                    val clean = questionInput.trim()
                                    if (clean.isNotEmpty()) {
                                        onAskQuestion(clean)
                                    }
                                },
                                enabled = !isBusy && questionInput.isNotBlank(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_ask_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إرسال")
                            }
                        }
                    }
                }
            }

            // Streaming / Output / Error Display
            if (aiState != AiStreamState.Idle) {
                item {
                    when (aiState) {
                        is AiStreamState.Loading -> {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = "جارٍ التحليل عبر Gemini...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is AiStreamState.Streaming,
                        is AiStreamState.Success -> {
                            val responseText = when (aiState) {
                                is AiStreamState.Streaming -> aiState.text
                                is AiStreamState.Success -> aiState.text
                                else -> ""
                            }
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "استجابة المساعد الذكي",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (aiState is AiStreamState.Streaming) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }

                                    SelectionContainer {
                                        Text(
                                            text = responseText,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    if (aiState is AiStreamState.Success) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(responseText))
                                                    copiedFeedback = true
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (copiedFeedback) "تم النسخ" else "نسخ النص")
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, responseText)
                                                    }
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent.createChooser(sendIntent, "مشاركة النص")
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Share,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("مشاركة")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is AiStreamState.Error -> {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "تعذّر إتمام الطلب الذكي",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = aiState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    OutlinedButton(
                                        onClick = onResetAiState,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("ai_retry_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إغلاق والمحاولة لاحقاً")
                                    }
                                }
                            }
                        }
                        AiStreamState.Idle -> Unit
                    }
                }
            }
        }
    }
}
