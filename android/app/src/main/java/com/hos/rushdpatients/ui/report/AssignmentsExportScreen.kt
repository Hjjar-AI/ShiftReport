package com.hos.rushdpatients.ui.report

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Local, names-only admin export; deliberately separate from clinical report delivery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsExportScreen(onBack: () -> Unit, viewModel: ReportPreviewViewModel = hiltViewModel()) {
    AssignmentsExportWorkspace(onBack, remember(viewModel) { LiveReportController(viewModel) })
}

/** Both real and demo sessions use this UI; only their data/actions differ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignmentsExportWorkspace(onBack: () -> Unit, controller: ReportController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val text = rememberUiStrings()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val reviewed = state.pdfReviewUris["assignments"]?.let { it in state.openedPdfUris } == true
    BackHandler { if (!state.isBusy) onBack() }
    LaunchedEffect(state.pdfPreviewUri) {
        val uri = state.pdfPreviewUri ?: return@LaunchedEffect
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            controller.onPdfPreviewOpened(uri)
        } catch (_: Exception) {
            controller.onPdfViewerUnavailable()
        }
    }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbar.showSnackbar(it); controller.dismissSnackbar() }
    }
    Scaffold(
        topBar = { AppTopBar(title = text(R.string.assignments_export_title),
            onBack = { if (!state.isBusy) onBack() }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
            if (controller.simulation) AppNotice(text(R.string.demo_simulation_notice))
            Text(text(R.string.assignments_export_help))
            LoadingButton(
                text = text(if (reviewed) R.string.ui_save else R.string.assignments_export_preview),
                loading = state.loading || state.previewingPdf || state.exportingLocalPdf,
                enabled = !state.isBusy && state.isAdmin && state.shift != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (reviewed) controller.exportAssignments()
                    else controller.previewExportPdf("assignments", draft = false)
                })
            state.error?.let {
                AppNotice(it, kind = NoticeKind.ERROR)
                AppTextButton(onClick = { controller.load() }, enabled = !state.isBusy) {
                    Text(text(R.string.ui_recovery_reload_report))
                }
            }
        }
    }
}
