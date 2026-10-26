package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Standalone Export always uses labelled draft artifacts and never publishes data. */
@Composable
internal fun ReportExportDialog(state: ReportUiState, controller: ReportController, onDismiss: () -> Unit) {
    val text = rememberUiStrings()
    var selection by rememberSaveable { mutableStateOf("general") }
    var changeReport by rememberSaveable { mutableStateOf(false) }
    val reports = buildMap {
        if (state.summary != null) put("general", text(R.string.ui_report_preview_screen_025))
        state.supervisorTargets.forEach { put("supervisor:${it.doctorId}", it.doctorName) }
        if (state.summary != null && state.patients.any { it.responsibleSpecialistId == null })
            put("unassigned", text(R.string.report_export_unassigned))
    }
    val supervisorRoutes = reports.keys.filterTo(linkedSetOf()) { it.startsWith("supervisor:") || it == "unassigned" }
    val choices = if (supervisorRoutes.size > 1) reports +
        ("all-supervisors" to text(R.string.report_draft_supervisor_batch)) else reports
    val active = selection.takeIf { it in choices } ?: choices.keys.firstOrNull()
    val routes = if (active == "all-supervisors") supervisorRoutes else setOfNotNull(active)
    fun opened(route: String) = state.pdfReviewUris["draft:$route"]?.let { it in state.openedPdfUris } == true
    val nextPreview = routes.firstOrNull { !opened(it) }
    val reviewed = routes.isNotEmpty() && nextPreview == null
    AppAlertDialog(onDismissRequest = { if (!state.isBusy) onDismiss() },
        title = { Text(text(R.string.report_export_draft_action)) },
        text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(text(R.string.report_export_draft_help))
                if (controller.simulation) AppNotice(text(R.string.demo_simulation_notice))
                Text(choices[active].orEmpty())
                if (choices.size > 1) AppTextButton(onClick = { changeReport = !changeReport }, enabled = !state.isBusy) {
                    Text(text(R.string.report_draft_change_report))
                }
                if (changeReport) choices.forEach { (key, label) ->
                    AppCheckRow(label = label, checked = key == active, enabled = !state.isBusy,
                        onCheckedChange = { if (it) { selection = key; changeReport = false } })
                }
                if (reviewed && routes.size == 1) AppTextButton(
                    onClick = { controller.shareExportPdf(routes.single(), draft = true) }, enabled = !state.isBusy) {
                    Text(text(R.string.report_export_share_draft))
                }
                state.error?.let {
                    AppNotice(it, kind = NoticeKind.ERROR)
                    AppTextButton(onClick = { controller.load() }, enabled = !state.isBusy) {
                        Text(text(R.string.ui_recovery_reload_report))
                    }
                }
            }
        }, confirmButton = {
            LoadingButton(text = if (nextPreview != null)
                text(R.string.report_draft_preview_destination, reports[nextPreview].orEmpty())
                else text(R.string.report_export_save_draft),
                loading = state.previewingPdf || state.exportingLocalPdf,
                enabled = !state.isBusy && routes.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (nextPreview != null) controller.previewExportPdf(nextPreview, draft = true)
                    else controller.saveExportPdfs(routes, draft = true)
                })
        }, dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !state.isBusy) { Text(text(R.string.ui_close)) }
        })
}
