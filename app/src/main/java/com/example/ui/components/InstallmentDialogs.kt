package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.InstallmentPayment
import com.example.domain.model.PaymentMethod
import com.example.domain.util.InstallmentCalculator

@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        fullName: String,
        phone: String,
        nationalId: String,
        address: String,
        notes: String,
        itemName: String?,
        totalCashPrice: Double?,
        downPayment: Double?,
        interestRate: Double?,
        installmentCount: Int?
    ) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var includePlan by remember { mutableStateOf(true) }
    var itemName by remember { mutableStateOf("") }
    var totalCashText by remember { mutableStateOf("") }
    var downPaymentText by remember { mutableStateOf("0") }
    var interestRateText by remember { mutableStateOf("0") }
    var installmentCountText by remember { mutableStateOf("12") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val totalPrice = totalCashText.toDoubleOrNull() ?: 0.0
    val down = downPaymentText.toDoubleOrNull() ?: 0.0
    val rate = interestRateText.toDoubleOrNull() ?: 0.0
    val count = installmentCountText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val previewTotal = InstallmentCalculator.calculateFinancedTotal(totalPrice, down, rate)
    val previewMonthly = if (count > 0) previewTotal / count else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة عميل جديد",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; errorMessage = null },
                    label = { Text("الاسم الكامل *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_customer_name")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMessage = null },
                    label = { Text("رقم الجوال *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_customer_phone")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nationalId,
                        onValueChange = { nationalId = it },
                        label = { Text("رقم الهوية") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "إنشاء عقد تقسيط مبدئي",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Switch(
                        checked = includePlan,
                        onCheckedChange = { includePlan = it },
                        modifier = Modifier.testTag("toggle_include_plan")
                    )
                }

                AnimatedVisibility(visible = includePlan) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it; errorMessage = null },
                            label = { Text("السلعة أو وصف العقد *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_plan_item")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = totalCashText,
                                onValueChange = { totalCashText = it; errorMessage = null },
                                label = { Text("السعر الإجمالي *") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_plan_total")
                            )
                            OutlinedTextField(
                                value = downPaymentText,
                                onValueChange = { downPaymentText = it },
                                label = { Text("الدفعة المقدمة") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = interestRateText,
                                onValueChange = { interestRateText = it },
                                label = { Text("نسبة الربح %") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = installmentCountText,
                                onValueChange = { installmentCountText = it },
                                label = { Text("عدد الأقساط") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (totalPrice > 0.0) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "المبلغ المقسّط: ${InstallmentCalculator.formatCurrency(previewTotal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "القسط الشهري التقريبي: ${InstallmentCalculator.formatCurrency(previewMonthly)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isBlank()) {
                        errorMessage = "يرجى إدخال اسم العميل"
                        return@Button
                    }
                    if (phone.isBlank()) {
                        errorMessage = "يرجى إدخال رقم الجوال"
                        return@Button
                    }
                    if (includePlan && (itemName.isBlank() || totalPrice <= 0.0)) {
                        errorMessage = "يرجى إدخال اسم السلعة والسعر الإجمالي للعقد"
                        return@Button
                    }
                    onConfirm(
                        fullName,
                        phone,
                        nationalId,
                        address,
                        notes,
                        if (includePlan) itemName else null,
                        if (includePlan) totalPrice else null,
                        if (includePlan) down else null,
                        if (includePlan) rate else null,
                        if (includePlan) count else null
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_add_customer_button")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun CreatePlanDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        itemName: String,
        totalCashPrice: Double,
        downPayment: Double,
        interestRatePercent: Double,
        installmentCount: Int,
        intervalMonths: Int,
        notes: String
    ) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var totalPriceText by remember { mutableStateOf("") }
    var downPaymentText by remember { mutableStateOf("0") }
    var rateText by remember { mutableStateOf("0") }
    var countText by remember { mutableStateOf("12") }
    var intervalText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val totalPrice = totalPriceText.toDoubleOrNull() ?: 0.0
    val down = downPaymentText.toDoubleOrNull() ?: 0.0
    val rate = rateText.toDoubleOrNull() ?: 0.0
    val count = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val financed = InstallmentCalculator.calculateFinancedTotal(totalPrice, down, rate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عقد تقسيط جديد", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it; errorMessage = null },
                    label = { Text("السلعة أو الخدمة *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_new_plan_item")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalPriceText,
                        onValueChange = { totalPriceText = it; errorMessage = null },
                        label = { Text("السعر الإجمالي *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = downPaymentText,
                        onValueChange = { downPaymentText = it },
                        label = { Text("الدفعة المقدمة") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("الربح %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it },
                        label = { Text("عدد الأقساط") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it },
                        label = { Text("كل (شهر)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات العقد") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (totalPrice > 0.0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "صافي التقسيط: ${InstallmentCalculator.formatCurrency(financed)} (${InstallmentCalculator.formatCurrency(financed / count)} / قسط)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (itemName.isBlank() || totalPrice <= 0.0) {
                        errorMessage = "يرجى إدخال وصف السلعة وسعر صحيح"
                        return@Button
                    }
                    onConfirm(
                        itemName,
                        totalPrice,
                        down,
                        rate,
                        count,
                        intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        notes
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_create_plan_button")
            ) {
                Text("إنشاء الجدول")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun RecordPaymentDialog(
    payment: InstallmentPayment,
    customerName: String,
    onDismiss: () -> Unit,
    onConfirm: (
        paidAmount: Double,
        method: PaymentMethod,
        receiptNumber: String,
        notes: String
    ) -> Unit
) {
    var amountText by remember(payment.id) {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", payment.remainingAmount))
    }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var receiptNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تحصيل القسط رقم ${payment.installmentNumber}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "العميل: $customerName\nالمبلغ المتبقي للقسط: ${
                        InstallmentCalculator.formatCurrency(payment.remainingAmount)
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMessage = null },
                    label = { Text("المبلغ المدفوع") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_payment_amount")
                )
                Text("طريقة الدفع", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method.labelAr, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = receiptNumber,
                    onValueChange = { receiptNumber = it },
                    label = { Text("رقم السند / الإيصال (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        errorMessage = "يرجى إدخال مبلغ صحيح"
                        return@Button
                    }
                    onConfirm(amount, selectedMethod, receiptNumber, notes)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_record_payment_button")
            ) {
                Text("تأكيد التحصيل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
