package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.AppCollapsibleSection
import com.hos.rushdpatients.ui.components.AppText

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.noticeColors
import com.hos.rushdpatients.ui.components.AppCheckRow
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.pdf.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Summary and review presentation; artifact selection and final confirmation remain screen-owned. */
@Composable
internal fun ReportPatientSummary(summary: com.hos.rushdpatients.domain.report.ReportSummary,
    doctors: List<com.hos.rushdpatients.data.model.Doctor>) {
    val uiText = rememberUiStrings()
    AppSection(title = uiText(R.string.ui_report_preview_screen_032)) {
        val subtotalStyle = androidx.compose.ui.text.SpanStyle(
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(androidx.compose.ui.text.buildAnnotatedString {
            append(uiText(R.string.report_total_patients, uiNumber(summary.patientCount)))
            append(" ")
            pushStyle(subtotalStyle)
            append(uiText(R.string.ui_report_preview_screen_034, uiNumber(summary.psychoCount)))
            pop()
        }, style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        AppText(uiText(R.string.report_escorts_and_date, uiNumber(summary.escortCount), uiNumber(summary.shiftDate)),
            style = MaterialTheme.typography.bodySmall)
        AppText(uiText(R.string.ui_report_preview_screen_063) +
            doctors.joinToString(uiText(R.string.ui_patient_card_002)) { it.fullName },
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ReportSendingReview(state: ReportUiState, generalSelected: Boolean,
    generalDestinationLabel: String, selectedTargets: List<SupervisorReportTarget>,
    showSendingReview: Boolean, onToggleReview: () -> Unit, busy: Boolean,
    onReviewPatients: (String, Set<String>) -> Unit, showChanges: Boolean, onToggleChanges: () -> Unit,
    pdfSelected: Boolean, exportPdf: Boolean, onExportPdf: (Boolean) -> Unit,
    recoveryContent: @Composable () -> Unit) {
    val uiText = rememberUiStrings()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(com.hos.rushdpatients.ui.theme.uiContentPadding()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppCollapsibleSection(expanded = showSendingReview,
                header = { AppSectionHeader(title = uiText(R.string.ui_report_preview_screen_062),
                    expanded = showSendingReview, onToggle = onToggleReview) }) {
                if (generalSelected) AppText(generalDestinationLabel + " • " +
                    (if (state.reportAsPdf) "PDF" else uiText(R.string.ui_report_preview_screen_015)))
                selectedTargets.forEach { target ->
                    AppText(uiText(R.string.ui_report_preview_screen_064, target.doctorName, uiNumber(target.patientCount)))
                }
                AppText(uiText(R.string.ui_report_preview_screen_066),
                    style = MaterialTheme.typography.bodySmall)
                recoveryContent()
                state.registryChangeSummary?.let { summary ->
                    AppSection(title = uiText(R.string.sync_publish_all)) {
                        AppText(summary)
                        if (!state.isAdmin) AppText(uiText(R.string.sync_doctors_stay_pending))
                    }
                }
                if (state.incompletePatientIds.isEmpty()) AppNotice(uiText(R.string.report_readiness_ready), kind = NoticeKind.SUCCESS)
                if (state.incompletePatientIds.isNotEmpty()) {
                    AppSection(
                        title = uiText(R.string.ui_report_preview_screen_046),
                        titleColor = noticeColors(NoticeKind.WARNING).content,
                        colors = CardDefaults.cardColors(
                            containerColor = noticeColors(NoticeKind.WARNING).container
                        )
                    ) {
                        AppTextButton(onClick = { state.shift?.id?.let { onReviewPatients(it, state.incompletePatientIds) } }, enabled = !busy) {
                            AppText(uiText(R.string.readiness_incomplete_patients, uiNumber(state.incompletePatientIds.size)), color = noticeColors(NoticeKind.WARNING).content)
                        }
                        AppText(
                            uiText(R.string.ui_report_preview_screen_047),
                            style = MaterialTheme.typography.labelSmall,
                            color = noticeColors(NoticeKind.WARNING).content
                        )
                    }
                }

                AppSection(
                    title = uiText(R.string.ui_report_preview_screen_048),
                    titleColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    if (state.changeBriefing.isEmpty()) {
                        AppText(uiText(R.string.ui_report_preview_screen_049))
                    } else {
                        AppTextButton(onClick = { onToggleChanges() }) {
                            AppText(uiText(R.string.ui_report_preview_screen_050, uiNumber(state.changeBriefing.size)) + if (showChanges) uiText(R.string.ui_patient_card_027) else uiText(R.string.ui_ward_view_options_dialog_009))
                        }
                        if (showChanges) state.changeBriefing.forEach { AppText("• $it") }
                    }
                }
                if (pdfSelected && state.summary != null) AppCheckRow(
                    label = uiText(R.string.report_optional_normal_export), checked = exportPdf,
                    enabled = !busy, onCheckedChange = onExportPdf)
            }
        }
    }
}
