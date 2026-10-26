package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

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
    val uiText = rememberUiStrings()
    fun kind(outcome: ReportStepOutcome) = when (outcome) {
        ReportStepOutcome.CONFIRMED -> NoticeKind.SUCCESS
        ReportStepOutcome.UNCERTAIN, ReportStepOutcome.PARTIAL -> NoticeKind.WARNING
        else -> NoticeKind.INFO
    }
    AppSection(title = uiText(R.string.ui_report_attempt_status_001)) {
        AppNotice(uiText(R.string.ui_report_attempt_status_002) + when (publication) {
            ReportStepOutcome.CONFIRMED -> uiText(R.string.ui_report_attempt_status_003)
            ReportStepOutcome.IN_PROGRESS -> uiText(R.string.ui_report_attempt_status_004)
            ReportStepOutcome.UNCERTAIN -> uiText(R.string.ui_report_attempt_status_005)
            else -> publication.arabicLabel(uiText)
        }, kind = kind(publication), compact = true)
        AppNotice(uiText(R.string.ui_report_attempt_status_006) + when (delivery) {
            ReportStepOutcome.CONFIRMED -> uiText(R.string.ui_report_attempt_status_007)
            ReportStepOutcome.IN_PROGRESS -> uiText(R.string.ui_report_attempt_status_008)
            ReportStepOutcome.NOT_STARTED -> uiText(R.string.ui_report_attempt_status_009)
            else -> delivery.arabicLabel(uiText)
        }, kind = kind(delivery), compact = true)
        if (publication == ReportStepOutcome.CONFIRMED && delivery == ReportStepOutcome.NOT_STARTED) {
            Text(uiText(R.string.ui_report_attempt_status_010))
        }
        if (publication == ReportStepOutcome.UNCERTAIN || delivery == ReportStepOutcome.UNCERTAIN ||
            delivery == ReportStepOutcome.PARTIAL) {
            AppTextButton(onClick = onHistory, enabled = !busy) { Text(uiText(R.string.ui_report_attempt_status_011)) }
        } else if (publication == ReportStepOutcome.CONFIRMED && delivery == ReportStepOutcome.CONFIRMED) {
            AppTextButton(onClick = onBack, enabled = !busy) { Text(uiText(R.string.ui_report_attempt_status_012)) }
        }
    }
}
