package com.hos.rushdpatients.ui.report

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.NoticeKind

/** Patient publication and report delivery are separate outcomes. */
@Composable
internal fun ReportAttemptStatus(publication: ReportStepOutcome, delivery: ReportStepOutcome,
    busy: Boolean, onHistory: () -> Unit, onBack: () -> Unit) {
    fun kind(outcome: ReportStepOutcome) = when (outcome) {
        ReportStepOutcome.CONFIRMED -> NoticeKind.SUCCESS
        ReportStepOutcome.UNCERTAIN, ReportStepOutcome.PARTIAL -> NoticeKind.WARNING
        else -> NoticeKind.INFO
    }
    AppSection(title = "نتيجة آخر محاولة") {
        AppNotice("بيانات المرضى: " + when (publication) {
            ReportStepOutcome.CONFIRMED -> "تم نشر النسخة المحلية"
            ReportStepOutcome.IN_PROGRESS -> "جار النشر"
            ReportStepOutcome.UNCERTAIN -> "لم يتأكد النشر؛ افحص النسخة الحالية قبل الإعادة"
            else -> publication.arabicLabel
        }, kind = kind(publication), compact = true)
        AppNotice("تقرير المناوبة: " + when (delivery) {
            ReportStepOutcome.CONFIRMED -> "تم تأكيد التسليم"
            ReportStepOutcome.IN_PROGRESS -> "جار الإرسال"
            ReportStepOutcome.NOT_STARTED -> "لم يتأكد تسليم تقرير في هذه المحاولة"
            else -> delivery.arabicLabel
        }, kind = kind(delivery), compact = true)
        if (publication == ReportStepOutcome.CONFIRMED && delivery == ReportStepOutcome.NOT_STARTED) {
            Text("بيانات المرضى منشورة بالفعل. عدم تسليم التقرير لا يلغي نشرها؛ راجع سبب التعذر أدناه.")
        }
        if (publication == ReportStepOutcome.UNCERTAIN || delivery == ReportStepOutcome.UNCERTAIN ||
            delivery == ReportStepOutcome.PARTIAL) {
            AppTextButton(onClick = onHistory, enabled = !busy) { Text("فحص سجل العمليات قبل الإعادة") }
        } else if (publication == ReportStepOutcome.CONFIRMED && delivery == ReportStepOutcome.CONFIRMED) {
            AppTextButton(onClick = onBack, enabled = !busy) { Text("العودة إلى المرضى") }
        }
    }
}
