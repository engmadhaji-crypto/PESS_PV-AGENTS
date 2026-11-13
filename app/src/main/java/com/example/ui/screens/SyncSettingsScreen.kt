package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.domain.model.DatabaseBackupPreview
import com.example.domain.model.SyncState
import com.example.domain.util.InstallmentCalculator
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.viewmodel.SyncUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncSettingsScreen(
    uiState: SyncUiState,
    pendingBackupImport: DatabaseBackupPreview?,
    isBackupBusy: Boolean,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSyncFirestore: () -> Unit,
    onPushSync: () -> Unit,
    onPullSync: () -> Unit,
    onUpdateSpreadsheetId: (String) -> Unit,
    onExportDatabaseUri: (Uri) -> Unit,
    onImportDatabaseUri: (Uri) -> Unit,
    onConfirmImportDatabase: (Boolean) -> Unit,
    onDismissImportDatabase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current
    var spreadsheetInput by remember(uiState.metadata.spreadsheetId) {
        mutableStateOf(uiState.metadata.spreadsheetId)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportDatabaseUri(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportDatabaseUri(uri)
        }
    }

    val isSyncing = uiState.syncState is SyncState.Syncing

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
                Column {
                    Text(
                        text = stringResource(R.string.sync_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.sync_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Network Connectivity Card
            item {
                val bg = if (uiState.isOnline) {
                    extended.paidContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
                val fg = if (uiState.isOnline) {
                    extended.paid
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
                Surface(
                    color = bg,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isOnline) Icons.Rounded.Wifi else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = fg
                        )
                        Column {
                            Text(
                                text = if (uiState.isOnline) {
                                    stringResource(R.string.status_online)
                                } else {
                                    stringResource(R.string.status_offline)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = fg,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "السجلات المعلّقة للمزامنة: ${uiState.pendingCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = fg
                            )
                        }
                    }
                }
            }

            // Google Account Card
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "حساب Google المرتبط",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = uiState.metadata.accountEmail.ifBlank {
                                        "متصل بـ Firebase Auth"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onSignInGoogle,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("google_signin_button")
                            ) {
                                Text("تبديل الحساب")
                            }
                            OutlinedButton(
                                onClick = onSignOutGoogle,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("google_signout_button")
                            ) {
                                Text(stringResource(R.string.sign_out))
                            }
                        }
                    }
                }
            }

            // Firebase Firestore Cloud Sync Card
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
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Firebase Firestore السحابي",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "تتم مزامنة بيانات العملاء والعقود والأقساط تلقائياً مع حسابك في Firestore فور توفر الشبكة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onSyncFirestore,
                            enabled = !isSyncing,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("firestore_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مزامنة فورية مع Firestore")
                        }
                    }
                }
            }

            // Local Database Export / Import Card (Room JSON Backup via SAF)
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
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.backup_section_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = stringResource(R.string.backup_section_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
                                        .format(Date())
                                    exportLauncher.launch("installments_backup_$stamp.json")
                                },
                                enabled = !isBackupBusy && !isSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_database_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.export_database))
                            }
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf("application/json", "text/plain", "*/*")
                                    )
                                },
                                enabled = !isBackupBusy && !isSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("import_database_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.import_database))
                            }
                        }
                    }
                }
            }

            // Spreadsheet Configuration Card
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
                                imageVector = Icons.Rounded.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "جدول بيانات Google Sheets",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        OutlinedTextField(
                            value = spreadsheetInput,
                            onValueChange = { spreadsheetInput = it },
                            label = { Text("معرّف الجدول (اتركه فارغاً للإنشاء التلقائي)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_spreadsheet_id")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onUpdateSpreadsheetId(spreadsheetInput) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_spreadsheet_id_button")
                            ) {
                                Text("حفظ المعرّف")
                            }
                            if (uiState.metadata.spreadsheetId.isNotBlank()) {
                                FilledTonalButton(
                                    onClick = {
                                        val url = "https://docs.google.com/spreadsheets/d/${uiState.metadata.spreadsheetId}"
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فتح الجدول")
                                }
                            }
                        }
                    }
                }
            }

            // Sync Execution & Diagnostics Card
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "عمليات مزامنة Google Sheets",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "آخر مزامنة: ${
                                InstallmentCalculator.formatDateTime(uiState.metadata.lastSyncTimestamp)
                            }\n${uiState.metadata.lastSyncMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        when (val state = uiState.syncState) {
                            is SyncState.Syncing -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(state.statusMessage, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is SyncState.Success -> {
                                Text(
                                    text = "اكتملت المزامنة بنجاح (${state.syncedRecords} سجل).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extended.paid
                                )
                            }
                            is SyncState.OfflineQueued -> {
                                Text(
                                    text = "العمل دون اتصال: ${state.pendingCount} سجل بانتظار عودة الشبكة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extended.partial
                                )
                            }
                            is SyncState.AuthRequired -> {
                                Text(
                                    text = state.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            is SyncState.Error -> {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            SyncState.Idle -> Unit
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onPushSync,
                                enabled = !isSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("push_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("رفع إلى Sheets")
                            }
                            FilledTonalButton(
                                onClick = onPullSync,
                                enabled = !isSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("pull_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استيراد")
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "يدعم التطبيق إعادة المحاولة التلقائية (Exponential Backoff) عند بطء الشبكة أو حدود الطلبات، مع تخزين كافة البيانات محلياً في قاعدة بيانات Room.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (pendingBackupImport != null) {
        AlertDialog(
            onDismissRequest = onDismissImportDatabase,
            title = { Text(stringResource(R.string.import_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "تم فحص ملف النسخة الاحتياطية بنجاح (تاريخ التصدير: ${
                            InstallmentCalculator.formatDateTime(pendingBackupImport.exportedAtMillis)
                        }).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• العملاء: ${pendingBackupImport.customerCount}\n" +
                            "• العقود: ${pendingBackupImport.planCount}\n" +
                            "• الأقساط: ${pendingBackupImport.installmentCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "اختر طريقة الاستيراد المناسبة:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onConfirmImportDatabase(true) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("import_replace_button")
                    ) {
                        Text(
                            text = stringResource(R.string.import_mode_replace),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { onConfirmImportDatabase(false) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("import_merge_button")
                    ) {
                        Text(stringResource(R.string.import_mode_merge))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissImportDatabase,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("import_cancel_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
