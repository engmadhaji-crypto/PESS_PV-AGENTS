package com.example.data.ai

import android.util.Log
import com.example.domain.model.DashboardStats
import com.example.domain.model.DueAlertItem
import com.example.domain.util.InstallmentCalculator
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

enum class ReminderTone(val labelAr: String) {
    POLITE("لطيف ومهني"),
    FIRM("حازم وواضح"),
    URGENT("تنبيه عاجل للتأخير")
}

sealed interface AiStreamState {
    data object Idle : AiStreamState
    data object Loading : AiStreamState
    data class Streaming(val text: String) : AiStreamState
    data class Success(val text: String) : AiStreamState
    data class Error(val message: String) : AiStreamState
}

class GeminiInstallmentAssistant {

    private val model by lazy {
        Firebase.ai.generativeModel(MODEL_NAME)
    }

    fun analyzePortfolio(
        stats: DashboardStats,
        alerts: List<DueAlertItem>
    ): Flow<AiStreamState> {
        val overdueAlerts = alerts.filter { it.isOverdue }.take(5)
        val dueSoonAlerts = alerts.filter { !it.isOverdue }.take(5)

        val overdueSummary = if (overdueAlerts.isEmpty()) {
            "لا توجد أقساط متأخرة حالياً."
        } else {
            overdueAlerts.joinToString("\n") {
                "- ${it.customerName} (${it.itemName}): متبقي ${InstallmentCalculator.formatCurrency(it.payment.remainingAmount)}، تاريخ الاستحقاق ${InstallmentCalculator.formatDate(it.payment.dueDateMillis)}"
            }
        }

        val upcomingSummary = if (dueSoonAlerts.isEmpty()) {
            "لا توجد أقساط مستحقة قريباً."
        } else {
            dueSoonAlerts.joinToString("\n") {
                "- ${it.customerName} (${it.itemName}): مبلغ ${InstallmentCalculator.formatCurrency(it.payment.remainingAmount)}، يستحق في ${InstallmentCalculator.formatDate(it.payment.dueDateMillis)}"
            }
        }

        val prompt = """
            أنت مستشار مالي متخصص في إدارة المبيعات بالتقسيط والتحصيل باللغة العربية.
            حلل وضع محفظة الأقساط التالية وقدّم توصيات عملية ومختصرة ومنظمة في نقاط واضحة:
            - عدد العملاء: ${stats.customerCount}
            - العقود النشطة: ${stats.activePlansCount} | العقود المكتملة: ${stats.completedPlansCount}
            - إجمالي المحفظة: ${InstallmentCalculator.formatCurrency(stats.totalPortfolio)}
            - إجمالي المحصّل: ${InstallmentCalculator.formatCurrency(stats.totalCollected)}
            - إجمالي المتبقي: ${InstallmentCalculator.formatCurrency(stats.totalRemaining)}
            - نسبة التحصيل: ${(stats.collectionRate * 100).toInt()}%
            - عدد الأقساط المتأخرة: ${stats.overdueCount} | المستحقة خلال 7 أيام: ${stats.dueSoonCount}

            أبرز المتأخرات:
            $overdueSummary

            أبرز الاستحقاقات القادمة:
            $upcomingSummary

            المطلوب:
            1. تقييم سريع لصحة المحفظة والسيولة.
            2. أولويات التحصيل الفورية.
            3. نصيحة عملية لتقليل التعثر وتحسين انتظام السداد.
        """.trimIndent()

        return streamPrompt(prompt)
    }

    fun draftPaymentReminder(
        alert: DueAlertItem,
        tone: ReminderTone
    ): Flow<AiStreamState> {
        val statusText = if (alert.isOverdue) "متأخر عن السداد" else "مستحق قريباً"
        val prompt = """
            اكتب رسالة تذكير جاهزة للإرسال عبر واتساب أو SMS لعميل بخصوص قسط ${statusText}.
            الأسلوب المطلوب: ${tone.labelAr}.
            بيانات القسط:
            - اسم العميل: ${alert.customerName}
            - السلعة/العقد: ${alert.itemName}
            - رقم القسط: ${alert.payment.installmentNumber}
            - المبلغ المتبقي: ${InstallmentCalculator.formatCurrency(alert.payment.remainingAmount)}
            - تاريخ الاستحقاق: ${InstallmentCalculator.formatDate(alert.payment.dueDateMillis)}

            اكتب نص الرسالة مباشرة باللغة العربية الفصحى المبسطة والودية بدون مقدمات أو شروحات إضافية.
        """.trimIndent()

        return streamPrompt(prompt)
    }

    fun askFinancialQuestion(
        question: String,
        stats: DashboardStats
    ): Flow<AiStreamState> {
        val prompt = """
            أنت مساعد ذكي لإدارة نظام الأقساط باللغة العربية.
            سياق المحفظة الحالية:
            - عدد العملاء: ${stats.customerCount}، العقود النشطة: ${stats.activePlansCount}
            - المحصّل: ${InstallmentCalculator.formatCurrency(stats.totalCollected)} من أصل ${InstallmentCalculator.formatCurrency(stats.totalPortfolio)}
            - الأقساط المتأخرة: ${stats.overdueCount}

            سؤال المستخدم:
            ${question.trim()}

            أجب بإجابة دقيقة ومباشرة وعملية باللغة العربية.
        """.trimIndent()

        return streamPrompt(prompt)
    }

    private fun streamPrompt(prompt: String): Flow<AiStreamState> = flow {
        emit(AiStreamState.Loading)
        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                model.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(AiStreamState.Streaming(accumulated.toString()))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isNotEmpty()) {
                emit(AiStreamState.Success(finalText))
            } else {
                emit(AiStreamState.Error("لم يُرجع المساعد الذكي أي استجابة. يرجى المحاولة مرة أخرى."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI request timed out", e)
            emit(AiStreamState.Error("انتهت مهلة الاتصال بالمساعد الذكي (60 ثانية). يرجى المحاولة مجدداً."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic generation failed", e)
            emit(
                AiStreamState.Error(
                    e.localizedMessage ?: "تعذّر الحصول على استجابة من المساعد الذكي."
                )
            )
        }
    }

    private companion object {
        const val TAG = "GeminiAssistant"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val AI_TIMEOUT_MS = 60_000L
    }
}
