package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.report.DeliveryResolution
import com.hos.rushdpatients.domain.report.DeliveryStatus
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.ui.components.*

/** Recovery presentation only; the workspace owns navigation, retry and receipt decisions. */
@Composable
internal fun ReportRecoverySection(
    state: ReportUiState, busy: Boolean,
    onDeliveryLog: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit,
    onFetchDoctors: () -> Unit, onOpenDoctors: () -> Unit, onReload: () -> Unit,
    onResolveDelivery: (ReportDeliveryRecord, DeliveryResolution) -> Unit
) {
    val uiText = rememberUiStrings()
    state.publicationOutcome?.let { publication ->
        ReportAttemptStatus(publication, state.deliveryOutcome ?: ReportStepOutcome.NOT_STARTED,
            busy = busy, onHistory = onDeliveryLog, onBack = onBack)
    }
    if (state.deliveryRecords.any { it.status == DeliveryStatus.UNKNOWN } &&
        state.deliveryOutcome != ReportStepOutcome.UNCERTAIN && state.deliveryOutcome != ReportStepOutcome.PARTIAL &&
        state.publicationOutcome != ReportStepOutcome.UNCERTAIN) {
        AppNotice(uiText(R.string.ui_report_preview_screen_035),
            kind = NoticeKind.WARNING, actionLabel = uiText(R.string.ui_ward_workflow_strip_007),
            onAction = onDeliveryLog, actionEnabled = !busy)
    }
    state.reviewNotice?.let { notice -> AppNotice(notice, kind = NoticeKind.WARNING) }
    state.error?.let { err ->
        AppSection(title = uiText(R.string.ui_report_preview_screen_036)) {
            AppNotice(when (state.doctorRegistryAction) {
                com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH -> uiText(R.string.ui_recovery_doctors_publish)
                com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.FETCH -> uiText(R.string.ui_recovery_doctors_fetch)
                null -> err
            }, kind = NoticeKind.ERROR,
                actionLabel = when (state.retryAction) {
                    ReportRetryAction.PREVIEW -> uiText(R.string.ui_report_preview_screen_037)
                    ReportRetryAction.SEND -> uiText(R.string.ui_report_preview_screen_038)
                    ReportRetryAction.SAVE, ReportRetryAction.SAVE_SUPERVISORS -> uiText(R.string.ui_report_preview_screen_039)
                    ReportRetryAction.SHARE -> uiText(R.string.ui_report_preview_screen_040)
                    ReportRetryAction.ASSIGNMENTS -> uiText(R.string.ui_report_preview_screen_041)
                    null -> null
                },
                onAction = if (state.retryAction == null) null else onRetry,
                actionEnabled = !state.sending && !state.previewingPdf && !state.exportingLocalPdf && !state.sharingPdf)
            when (state.doctorRegistryAction) {
                com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.FETCH ->
                    AppOutlinedButton(onClick = onFetchDoctors, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_report_preview_screen_042)) }
                com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH -> {
                    if (state.isAdmin) AppOutlinedButton(onClick = onOpenDoctors,
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_report_preview_screen_043)) }
                    else AppNotice(uiText(R.string.ui_report_preview_screen_044))
                }
                null -> Unit
            }
            if (state.doctorRegistryAction == com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH) {
                AppOutlinedButton(onClick = onReload, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_recovery_reload_report)) }
            }
        }
    }
    state.deliveryRecords.filter { it.status == DeliveryStatus.UNKNOWN }.forEach { receipt ->
        AppSection(title = receipt.label) {
            DeliveryQuestion(receipt, !busy, onResolveDelivery)
        }
    }
    state.publicationWarning?.let { AppNotice(it, kind = NoticeKind.WARNING) }
    state.lastOperation?.let { AppNotice(it) }
}
