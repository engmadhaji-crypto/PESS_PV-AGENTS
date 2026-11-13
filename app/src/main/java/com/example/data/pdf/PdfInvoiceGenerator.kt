package com.example.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.domain.model.Customer
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PlanWithPayments
import com.example.domain.util.InstallmentCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfInvoiceGenerator(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 36f
        private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)
    }

    suspend fun generateCustomerStatementPdf(
        customer: Customer,
        plansWithPayments: List<PlanWithPayments>
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            try {
                var pageNumber = 1
                var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                var page = document.startPage(pageInfo)
                var canvas = page.canvas
                var y = MARGIN

            val headerBgPaint = Paint().apply {
                color = Color.parseColor("#0B3B36")
                style = Paint.Style.FILL
            }
            val accentPaint = Paint().apply {
                color = Color.parseColor("#B87A14")
                style = Paint.Style.FILL
            }
            val cardBgPaint = Paint().apply {
                color = Color.parseColor("#F4F7F6")
                style = Paint.Style.FILL
            }
            val tableHeaderPaint = Paint().apply {
                color = Color.parseColor("#0B5D4B")
                style = Paint.Style.FILL
            }
            val zebraPaint = Paint().apply {
                color = Color.parseColor("#F9FBFA")
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#D5E0DC")
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D4ECE5")
                textSize = 10.5f
            }
            val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0B3B36")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1D2624")
                textSize = 10f
            }
            val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1D2624")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tableHeaderTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            // Header Banner
            canvas.drawRoundRect(
                RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 72f),
                10f,
                10f,
                headerBgPaint
            )
            canvas.drawRect(MARGIN, y + 66f, PAGE_WIDTH - MARGIN, y + 72f, accentPaint)
            drawTextInBox(
                canvas,
                "كشف حساب عميل - نظام الأقساط",
                titlePaint,
                MARGIN + 16f,
                y + 14f,
                CONTENT_WIDTH - 32f,
                Layout.Alignment.ALIGN_OPPOSITE
            )
            drawTextInBox(
                canvas,
                "تاريخ الإصدار: ${InstallmentCalculator.formatDateTime(System.currentTimeMillis())}",
                subtitlePaint,
                MARGIN + 16f,
                y + 42f,
                CONTENT_WIDTH - 32f,
                Layout.Alignment.ALIGN_OPPOSITE
            )
            y += 88f

            // Customer Information Card
            canvas.drawRoundRect(
                RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 76f),
                8f,
                8f,
                cardBgPaint
            )
            canvas.drawRoundRect(
                RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 76f),
                8f,
                8f,
                borderPaint
            )
            drawTextInBox(
                canvas,
                "العميل: ${customer.fullName}",
                boldBodyPaint,
                MARGIN + 14f,
                y + 12f,
                CONTENT_WIDTH - 28f,
                Layout.Alignment.ALIGN_OPPOSITE
            )
            drawTextInBox(
                canvas,
                "الهاتف: ${customer.phone}   |   الهوية: ${customer.nationalId.ifBlank { "-" }}",
                bodyPaint,
                MARGIN + 14f,
                y + 32f,
                CONTENT_WIDTH - 28f,
                Layout.Alignment.ALIGN_OPPOSITE
            )
            drawTextInBox(
                canvas,
                "العنوان: ${customer.address.ifBlank { "-" }}",
                bodyPaint,
                MARGIN + 14f,
                y + 50f,
                CONTENT_WIDTH - 28f,
                Layout.Alignment.ALIGN_OPPOSITE
            )
            y += 90f

            // Grand Summary Box
            val totalFinanced = plansWithPayments.sumOf { it.plan.financedTotal + it.plan.downPayment }
            val totalPaid = plansWithPayments.sumOf { it.totalPaid }
            val totalRemaining = plansWithPayments.sumOf { it.totalRemaining }

            val colW = CONTENT_WIDTH / 3f
            val summaryHeaders = listOf("المتبقي", "إجمالي المدفوع", "إجمالي العقود")
            val summaryValues = listOf(
                InstallmentCalculator.formatCurrency(totalRemaining),
                InstallmentCalculator.formatCurrency(totalPaid),
                InstallmentCalculator.formatCurrency(totalFinanced)
            )
            canvas.drawRoundRect(
                RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 48f),
                8f,
                8f,
                cardBgPaint
            )
            canvas.drawRoundRect(
                RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 48f),
                8f,
                8f,
                borderPaint
            )
            for (i in 0..2) {
                val xStart = MARGIN + i * colW
                drawTextInBox(
                    canvas,
                    summaryHeaders[i],
                    bodyPaint,
                    xStart + 6f,
                    y + 8f,
                    colW - 12f,
                    Layout.Alignment.ALIGN_CENTER
                )
                drawTextInBox(
                    canvas,
                    summaryValues[i],
                    boldBodyPaint,
                    xStart + 6f,
                    y + 25f,
                    colW - 12f,
                    Layout.Alignment.ALIGN_CENTER
                )
            }
            y += 64f

            fun startNewPage() {
                drawFooter(canvas, pageNumber, bodyPaint)
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }

            // Table Column Definitions (left-to-right on page, ordered for clean Arabic reading)
            val colWidths = floatArrayOf(82f, 74f, 74f, 74f, 88f, 88f, 43f)
            val headers = listOf("تاريخ السداد", "الحالة", "المتبقي", "المدفوع", "المستحق", "الاستحقاق", "#")

            plansWithPayments.forEach { bundle ->
                if (y > PAGE_HEIGHT - 130f) {
                    startNewPage()
                }

                drawTextInBox(
                    canvas,
                    "العقد: ${bundle.plan.itemName}  (${bundle.plan.status.labelAr}) - الدفعة المقدمة: ${
                        InstallmentCalculator.formatCurrency(bundle.plan.downPayment)
                    }",
                    sectionTitlePaint,
                    MARGIN,
                    y,
                    CONTENT_WIDTH,
                    Layout.Alignment.ALIGN_OPPOSITE
                )
                y += 24f

                // Table header
                drawTableHeader(canvas, MARGIN, y, colWidths, headers, tableHeaderPaint, tableHeaderTextPaint)
                y += 24f

                bundle.payments.forEachIndexed { idx, payment ->
                    if (y > PAGE_HEIGHT - 60f) {
                        startNewPage()
                        drawTableHeader(canvas, MARGIN, y, colWidths, headers, tableHeaderPaint, tableHeaderTextPaint)
                        y += 24f
                    }

                    val rowRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f)
                    if (idx % 2 == 1) {
                        canvas.drawRect(rowRect, zebraPaint)
                    }
                    canvas.drawRect(rowRect, borderPaint)

                    val statusColorPaint = TextPaint(bodyPaint).apply {
                        color = when (payment.status) {
                            PaymentStatus.PAID -> Color.parseColor("#0B6E4F")
                            PaymentStatus.OVERDUE -> Color.parseColor("#B3261E")
                            PaymentStatus.PARTIAL -> Color.parseColor("#B87A14")
                            PaymentStatus.PENDING -> Color.parseColor("#3E4A47")
                        }
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    val cells = listOf(
                        payment.paidDateMillis?.let { InstallmentCalculator.formatDate(it) } ?: "-",
                        payment.status.labelAr,
                        InstallmentCalculator.formatCurrency(payment.remainingAmount, ""),
                        InstallmentCalculator.formatCurrency(payment.amountPaid, ""),
                        InstallmentCalculator.formatCurrency(payment.amountDue, ""),
                        InstallmentCalculator.formatDate(payment.dueDateMillis),
                        payment.installmentNumber.toString()
                    )

                    var cellX = MARGIN
                    cells.forEachIndexed { colIdx, text ->
                        val w = colWidths[colIdx]
                        val paintToUse = if (colIdx == 1) statusColorPaint else bodyPaint
                        drawTextInBox(
                            canvas,
                            text.trim(),
                            paintToUse,
                            cellX + 3f,
                            y + 5f,
                            w - 6f,
                            Layout.Alignment.ALIGN_CENTER
                        )
                        cellX += w
                    }
                    y += 22f
                }

                y += 18f
            }

            drawFooter(canvas, pageNumber, bodyPaint)
            document.finishPage(page)

                val outputDir = File(context.cacheDir, "invoices").apply { mkdirs() }
                val safeName = customer.fullName.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
                val file = File(outputDir, "statement_${safeName}_${System.currentTimeMillis()}.pdf")
                FileOutputStream(file).use { out ->
                    document.writeTo(out)
                }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } finally {
                runCatching { document.close() }
            }
        }
    }

    private fun drawTableHeader(
        canvas: Canvas,
        startX: Float,
        y: Float,
        colWidths: FloatArray,
        headers: List<String>,
        bgPaint: Paint,
        textPaint: TextPaint
    ) {
        canvas.drawRect(startX, y, startX + colWidths.sum(), y + 24f, bgPaint)
        var x = startX
        headers.forEachIndexed { idx, title ->
            val w = colWidths[idx]
            drawTextInBox(
                canvas,
                title,
                textPaint,
                x + 3f,
                y + 6f,
                w - 6f,
                Layout.Alignment.ALIGN_CENTER
            )
            x += w
        }
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, paint: TextPaint) {
        drawTextInBox(
            canvas,
            "صفحة $pageNumber — تم الإنشاء بواسطة نظام الأقساط",
            paint,
            MARGIN,
            PAGE_HEIGHT - 28f,
            CONTENT_WIDTH,
            Layout.Alignment.ALIGN_CENTER
        )
    }

    private fun drawTextInBox(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Float,
        alignment: Layout.Alignment
    ) {
        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            paint,
            width.toInt().coerceAtLeast(1)
        )
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }
}
