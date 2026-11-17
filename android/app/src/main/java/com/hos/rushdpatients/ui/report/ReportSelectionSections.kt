package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.*

/** Presentation only; selection, review state and artifact invalidation stay with the workspace/controller. */
@Composable
internal fun ReportDoctorSummary(
    doctors: List<Doctor>, enabled: Boolean, onSelectDoctors: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppSection(title = uiText(R.string.ui_shift_doctor_picker_001), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ), titleColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        if (doctors.isEmpty()) AppText(uiText(R.string.ui_report_preview_screen_019))
        else doctors.forEach { AppText(it.fullName, style = MaterialTheme.typography.bodyMedium) }
        AppOutlinedButton(
            onClick = onSelectDoctors,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            ButtonEmblemContent(AppIcons.Groups, enabled) {
                AppText(if (doctors.isEmpty()) uiText(R.string.ui_report_preview_screen_022) else uiText(R.string.ui_report_preview_screen_023))
            }
        }
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabels = buildList {
        if (generalSelected) add(generalDestinationLabel + " • " +
            if (reportAsPdf) uiText(R.string.ui_report_preview_screen_031) else uiText(R.string.ui_report_preview_screen_015))
        targets.filter { it.chatId != null && it.doctorId in selectedSupervisorIds }.forEach {
            add(it.doctorName + " • PDF")
        }
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(com.hos.rushdpatients.ui.theme.uiContentPadding()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppSectionHeader(title = uiText(R.string.ui_report_preview_screen_024), expanded = expanded,
                onToggle = { if (!busy) expanded = !expanded })
            if (selectedLabels.isEmpty()) AppText(uiText(R.string.report_no_destination_selected),
                style = MaterialTheme.typography.bodySmall)
            selectedLabels.forEach { AppText(it, style = MaterialTheme.typography.bodySmall) }
            if (expanded) {
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
                AppText(uiText(R.string.ui_report_destination_checklist_hint), style = MaterialTheme.typography.bodySmall)
                targets.forEach { target ->
                    AppCheckRow(label = uiText(R.string.ui_report_preview_screen_028, target.doctorName, uiNumber(target.patientCount)) +
                        if (target.chatId == null) uiText(R.string.ui_report_preview_screen_029) else "\n" + target.destinationLabel(uiText),
                        checked = target.doctorId in selectedSupervisorIds && target.chatId != null,
                        enabled = !busy && target.chatId != null,
                        onCheckedChange = { checked -> onSupervisorSelected(target.doctorId, checked) })
                }
                if (targets.isEmpty()) AppText(uiText(R.string.ui_report_preview_screen_027),
                    style = MaterialTheme.typography.bodySmall)
                if (generalSelected) AppToggleRow(title = uiText(R.string.ui_report_preview_screen_031), checked = reportAsPdf,
                    onCheckedChange = onReportAsPdf, enabled = canChangeReportFormat && !busy)
            }
        }
    }
}

