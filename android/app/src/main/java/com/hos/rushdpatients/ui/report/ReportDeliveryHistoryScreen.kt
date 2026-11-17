package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.readingPaneWidth
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.report.DeliveryResolution
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun ReportDeliveryHistoryScreen(onBack: () -> Unit, viewModel: ReportPreviewViewModel = hiltViewModel()) {
    ReportDeliveryHistoryWorkspace(onBack, remember(viewModel) { LiveReportController(viewModel) })
}

/** Receipts remain available independently of report preparation, including an empty roster. */
@Composable
internal fun ReportDeliveryHistoryWorkspace(onBack: () -> Unit, controller: ReportController) {
    val uiText = rememberUiStrings()
    val state by controller.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var allowCopy by remember { mutableStateOf<ReportDeliveryRecord?>(null) }
    BackHandler(onBack = onBack)
    com.hos.rushdpatients.ui.components.AppSnackbarEffect(state.snackbar, snackbar, controller::dismissSnackbar)
    Scaffold(topBar = { AppTopBar(title = uiText(R.string.ui_report_preview_screen_081), onBack = onBack) },
        snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).readingPaneWidth().verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            if (controller.simulation) AppNotice(uiText(R.string.demo_simulation_notice))
            state.error?.let { AppNotice(it, kind = NoticeKind.WARNING) }
            ReportDeliveryLogContent(state.deliveryRecords, state.isBusy,
                onResolveDelivery = controller::resolveDelivery, onAllowCopy = { allowCopy = it })
        }
    }
    allowCopy?.let { row ->
        ReportDeliveryDecisionDialog(row, DeliveryResolution.ALLOW_COPY, state.isBusy,
            onConfirm = { allowCopy = null; controller.resolveDelivery(row, DeliveryResolution.ALLOW_COPY) },
            onDismiss = { allowCopy = null })
    }
}
