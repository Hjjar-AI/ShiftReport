package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.pdf.PdfExportOptions
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.components.*

/** Presentation only; selection, review state and artifact invalidation stay with the workspace/controller. */
@Composable
internal fun ReportDoctorSummary(
    doctors: List<Doctor>, showRosterDetails: Boolean, enabled: Boolean,
    onToggleDetails: () -> Unit, onSelectDoctors: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppSection(title = uiText(R.string.ui_shift_doctor_picker_001), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ), titleColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        if (doctors.isEmpty()) Text(uiText(R.string.ui_report_preview_screen_019))
        else {
            Text(doctors.take(2).joinToString(uiText(R.string.ui_patient_card_002)) { it.fullName } +
                if (doctors.size > 2) " • +${uiNumber(doctors.size - 2)}" else "")
            if (doctors.size > 2) AppTextButton(onClick = onToggleDetails) {
                Text(if (showRosterDetails) uiText(R.string.ui_report_preview_screen_020) else uiText(R.string.ui_report_preview_screen_021, uiNumber(doctors.size)))
            }
            if (showRosterDetails) doctors.forEach { Text("• ${it.fullName}") }
        }
        AppOutlinedButton(
            onClick = onSelectDoctors,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (doctors.isEmpty()) uiText(R.string.ui_report_preview_screen_022) else uiText(R.string.ui_report_preview_screen_023)) }
    }
}

@Composable
internal fun ReportDestinationSelection(
    targets: List<SupervisorReportTarget>, reportAsPdf: Boolean, canChangeReportFormat: Boolean,
    busy: Boolean, generalDestinationLabel: String,
    generalSelected: Boolean, selectedSupervisorIds: Set<String>,
    onGeneralSelected: (Boolean) -> Unit, onSelectAll: (Boolean, Set<String>) -> Unit,
    onSupervisorSelected: (String, Boolean) -> Unit, onReportAsPdf: (Boolean) -> Unit
) {
    val uiText = rememberUiStrings()
    AppSection(title = uiText(R.string.ui_report_preview_screen_024)) {
        AppCheckRow(label = generalDestinationLabel,
            checked = generalSelected, enabled = !busy,
            onCheckedChange = onGeneralSelected)
        val selectableIds = targets.filter { it.chatId != null }.mapTo(linkedSetOf()) { it.doctorId }
        AppCheckRow(label = uiText(R.string.ui_report_select_all_destinations),
            checked = generalSelected && selectableIds.all { it in selectedSupervisorIds },
            enabled = !busy,
            onCheckedChange = { checked ->
                onSelectAll(checked, selectableIds)
            })
        Text(uiText(R.string.ui_report_destination_checklist_hint), style = MaterialTheme.typography.bodySmall)
        targets.forEach { target ->
            AppCheckRow(label = uiText(R.string.ui_report_preview_screen_028, target.doctorName, uiNumber(target.patientCount)) +
                if (target.chatId == null) uiText(R.string.ui_report_preview_screen_029) else "",
                checked = target.doctorId in selectedSupervisorIds && target.chatId != null,
                enabled = !busy && target.chatId != null,
                onCheckedChange = { checked -> onSupervisorSelected(target.doctorId, checked) })
        }
        if (targets.isEmpty()) Text(uiText(R.string.ui_report_preview_screen_027),
            style = MaterialTheme.typography.bodySmall)
        if (generalSelected) AppToggleRow(title = uiText(R.string.ui_report_preview_screen_031), checked = reportAsPdf,
            onCheckedChange = onReportAsPdf, enabled = canChangeReportFormat && !busy)
    }
}

@Composable
internal fun ReportPdfCustomization(
    style: PdfStyle, options: PdfExportOptions, busy: Boolean, expanded: Boolean,
    onToggleExpanded: () -> Unit, onAppearanceChange: (PdfStyle, PdfExportOptions) -> Unit
) {
    val uiText = rememberUiStrings()
    AppTextButton(onClick = onToggleExpanded, enabled = !busy,
        modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) uiText(R.string.ui_report_preview_screen_051) else uiText(R.string.ui_report_preview_screen_052))
    }
    if (expanded) AppSection(title = uiText(R.string.ui_report_preview_screen_053)) {
        AppPdfStylePicker(selected = style,
            onSelected = { onAppearanceChange(it, options) }, enabled = !busy)
        if (style == PdfStyle.CARDS) {
            Text(uiText(R.string.pdf_two_column_sections_hint), style = MaterialTheme.typography.bodySmall)
        }
        Text(uiText(R.string.pdf_uses_app_theme, options.appTheme.arabicLabel(uiText)),
            style = MaterialTheme.typography.bodySmall)
        Text(uiText(R.string.ui_report_preview_screen_055), style = MaterialTheme.typography.bodySmall)
    }
}
