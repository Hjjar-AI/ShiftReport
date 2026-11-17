package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.readingPaneWidth
import com.hos.rushdpatients.ui.components.AppText

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.appContentEntrance
import com.hos.rushdpatients.ui.components.ContentEntrance
import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.noticeColors
import com.hos.rushdpatients.ui.components.AppCheckRow
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.ReportSendConfirmation
import com.hos.rushdpatients.ui.components.AppLoadingState
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.uiListGap
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppSectionHeader
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.hos.rushdpatients.pdf.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.ui.ward.ShiftDoctorPicker
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.domain.report.DeliveryResolution

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReportPreviewScreen(
    onBack: () -> Unit,
    onOpenDoctors: () -> Unit = {},
    viewModel: ReportPreviewViewModel = hiltViewModel(),
    onReviewPatients: (String, Set<String>) -> Unit = { _, _ -> },
    refreshRequest: Boolean = false, onRefreshConsumed: () -> Unit = {}
) {
    ReportWorkspace(onBack, onOpenDoctors, remember(viewModel) { LiveReportController(viewModel) },
        onReviewPatients, refreshRequest, onRefreshConsumed)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReportWorkspace(
    onBack: () -> Unit,
    onOpenDoctors: () -> Unit,
    viewModel: ReportController,
    onReviewPatients: (String, Set<String>) -> Unit = { _, _ -> },
    refreshRequest: Boolean = false, onRefreshConsumed: () -> Unit = {}
) {
    LaunchedEffect(refreshRequest) {
        if (refreshRequest) { viewModel.load(); onRefreshConsumed() }
    }
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var step by rememberSaveable { mutableStateOf(ReportFlowStep.DOCTORS) }
    val context = LocalContext.current
    var generalSelected by rememberSaveable { mutableStateOf(true) }
    var exportPdf by rememberSaveable { mutableStateOf(true) }
    var showTextPreview by remember { mutableStateOf(false) }
    var reviewedText by remember { mutableStateOf<String?>(null) }
    var selectedSupervisorIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showShiftDoctors by remember { mutableStateOf(false) }
    var showPatientSort by remember { mutableStateOf(false) }
    var showDeliveryLog by remember { mutableStateOf(false) }
    var deliveryDecision by remember { mutableStateOf<Pair<ReportDeliveryRecord, DeliveryResolution>?>(null) }
    var showChanges by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showSendingReview by rememberSaveable { mutableStateOf(false) }
    var pendingDelivery by remember { mutableStateOf<ReportDestinationReview?>(null) }
    val selectedTargets = state.supervisorTargets.filter { it.doctorId in selectedSupervisorIds && it.chatId != null }
    val supervisorMode = selectedTargets.isNotEmpty()
    val pdfSelected = supervisorMode || state.reportAsPdf
    val flowGate = reportFlowGate(state, step, supervisorMode, selectedTargets, reviewedText, state.openedPdfUris, generalSelected)
    val busy = flowGate.busy
    // A read-only load can be left; operations that save or deliver keep their navigation guard.
    val navigationBlocked = state.savingDoctors || state.savingSort || state.fetchingDoctors || state.sending ||
        state.previewingPdf || state.exportingLocalPdf || state.sharingPdf || state.savingPdfOptions
    val previewReady = flowGate.previewReady
    val destinationReady = flowGate.destinationReady
    val nextDisabledReason = flowGate.nextDisabledReason?.let { uiText(it) }
    val sendDisabledReason = flowGate.sendDisabledReason?.let { uiText(it) }
    val generalDestinationLabel = uiText(if (state.reportsToResidents) R.string.residents_report_label else R.string.project_report_label)
    val fullReportPreviewed = if (state.reportAsPdf) {
        state.pdfReviewUris["general"]?.let { it in state.openedPdfUris } == true
    } else reviewedText == state.previewMarkdown
    fun openPreview() {
        if (busy || state.summary == null) return
        if (state.reportAsPdf) viewModel.previewPdf()
        else { reviewedText = state.previewMarkdown; showTextPreview = true }
    }
    fun previousStep() {
        if (!navigationBlocked) {
            if (step == ReportFlowStep.DOCTORS) onBack() else step = step.previous()
        }
    }
    val recoveryContent: @Composable () -> Unit = {
        ReportRecoverySection(state = state, busy = busy,
            onDeliveryLog = { showDeliveryLog = true }, onBack = onBack,
            onRetry = {
                if (state.retryAction == ReportRetryAction.SEND) {
                    step = ReportFlowStep.PREVIEW
                } else if (state.retryAction == ReportRetryAction.PREVIEW) openPreview()
                else viewModel.retryLastAction { step = ReportFlowStep.PREVIEW }
            },
            onFetchDoctors = viewModel::fetchDoctorsForReport, onOpenDoctors = onOpenDoctors,
            onReload = { viewModel.load() }, onResolveDelivery = viewModel::resolveDelivery)
    }
    LaunchedEffect(state.error, state.publicationOutcome, state.deliveryOutcome) {
        if (step == ReportFlowStep.PREVIEW &&
            (state.error != null || state.publicationOutcome != null || state.deliveryOutcome != null)) {
            showSendingReview = true
        }
    }
    BackHandler { previousStep() }
    com.hos.rushdpatients.ui.components.AppSnackbarEffect(state.snackbar, snackbar, viewModel::dismissSnackbar)

    LaunchedEffect(state.pdfPreviewUri) {
        val uri = state.pdfPreviewUri ?: return@LaunchedEffect
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            viewModel.onPdfPreviewOpened(uri)
        } catch (_: ActivityNotFoundException) {
            viewModel.onPdfViewerUnavailable()
        } catch (_: Exception) {
            viewModel.onPdfViewerUnavailable()
        }
    }

    LaunchedEffect(state.pdfShareUri) {
        val uri = state.pdfShareUri ?: return@LaunchedEffect
        try {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    uiText(R.string.ui_report_preview_screen_012)
                )
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            viewModel.onPdfShareUnavailable()
        } finally {
            viewModel.consumePdfShare()
        }
    }

    if (showShiftDoctors) {
        ShiftDoctorPicker(
            allDoctors = state.availableDoctors,
            initialSelected = state.shift?.doctorIds.orEmpty(),
            saving = state.savingDoctors,
            onConfirm = { ids ->
                viewModel.setShiftDoctors(ids) { showShiftDoctors = false }
            },
            onDismiss = { showShiftDoctors = false }
        )
    }

    if (showPatientSort) {
        com.hos.rushdpatients.ui.ward.SortSheet(
            initial = com.hos.rushdpatients.domain.sort.SortSpecCodec.decode(state.shift?.sortSpecJson),
            saving = busy,
            onApply = { spec ->
                pendingDelivery = null
                reviewedText = null
                viewModel.setPatientSort(spec) { showPatientSort = false }
            },
            onDismiss = { if (!busy) showPatientSort = false }
        )
    }

    val stepScrolls = mapOf(
        ReportFlowStep.DOCTORS to rememberScrollState(),
        ReportFlowStep.PREVIEW to rememberScrollState()
    )
    val contentScroll = stepScrolls.getValue(step)
    Scaffold(
        topBar = {
            Column {
                AppTopBar(title = uiText(R.string.ui_report_preview_screen_013, step.arabicLabel(uiText)),
                    onBack = ::previousStep, backEnabled = !navigationBlocked)
                if (viewModel.simulation) AppNotice(uiText(R.string.demo_simulation_notice))
                ReportFlowProgress(step)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = com.hos.rushdpatients.ui.theme.LocalUiStyle.current.actionBarElevation) {
                Column(Modifier.fillMaxWidth().readingPaneWidth().navigationBarsPadding().imePadding().padding(UiSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    val targetSummary = uiText(R.string.ui_report_selected_destinations,
                        uiNumber(selectedTargets.size + if (generalSelected) 1 else 0))
                    AppText(targetSummary, style = MaterialTheme.typography.bodySmall)
                    val previewNeeded = step == ReportFlowStep.PREVIEW && !previewReady
                    val disabledReason = when {
                        step == ReportFlowStep.DOCTORS -> nextDisabledReason
                        !destinationReady -> sendDisabledReason
                        previewNeeded -> null // Only a selected general text report requires opening its preview.
                        else -> sendDisabledReason
                    }
                    disabledReason?.let { AppText(it, style = MaterialTheme.typography.bodySmall) }
                    when {
                        step == ReportFlowStep.DOCTORS -> AppButton(
                            onClick = { step = step.next() }, enabled = nextDisabledReason == null,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppText(uiText(R.string.ui_report_preview_screen_017, step.next().arabicLabel(uiText))) }
                        previewNeeded -> LoadingButton(
                            text = if (state.previewingPdf) uiText(R.string.ui_progress_prepare_preview)
                                else uiText(R.string.report_full_preview_action),
                            loading = state.previewingPdf, enabled = !busy && destinationReady,
                            modifier = Modifier.fillMaxWidth(), onClick = ::openPreview)
                        else -> LoadingButton(text = UiAction.SEND_REPORT.label(uiText), loading = state.sending,
                            emblem = UiAction.SEND_REPORT.icon,
                            modifier = Modifier.fillMaxWidth(), enabled = sendDisabledReason == null,
                            onClick = {
                                pendingDelivery = ReportDestinationReview(generalSelected, selectedTargets.toList(), state.reportAsPdf, exportPdf && pdfSelected)
                            })
                    }
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AppLoadingState(uiText(R.string.ui_report_preview_screen_018))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding).readingPaneWidth()
                    .padding(UiSpacing.screen)
                    .appContentEntrance(step, ContentEntrance.REPORT_STEP, step.ordinal)
                    .verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(uiListGap())
            ) {
                if (step == ReportFlowStep.DOCTORS) {
                    ReportDoctorSummary(
                        doctors = state.doctors,
                        enabled = !state.isBusy && state.shift != null,
                        onSelectDoctors = { pendingDelivery = null; showShiftDoctors = true })
                }
                if (step == ReportFlowStep.DOCTORS) {
                    ReportDestinationSelection(
                        targets = state.supervisorTargets, reportAsPdf = state.reportAsPdf,
                        canChangeReportFormat = state.summary != null, busy = busy,
                        generalDestinationLabel = generalDestinationLabel,
                        generalSelected = generalSelected, selectedSupervisorIds = selectedSupervisorIds,
                        onGeneralSelected = { generalSelected = it },
                        onSelectAll = { checked, ids ->
                            generalSelected = checked
                            selectedSupervisorIds = if (checked) ids else emptySet()
                        },
                        onSupervisorSelected = { id, checked ->
                            selectedSupervisorIds = if (checked) selectedSupervisorIds + id else selectedSupervisorIds - id
                        },
                        onReportAsPdf = viewModel::setReportAsPdf)
                }
                if (step == ReportFlowStep.PREVIEW) {
                    state.summary?.let { summary ->
                        ReportPatientSummary(summary, state.doctors)
                    }

                }
                if (step == ReportFlowStep.DOCTORS) recoveryContent()
                if (step == ReportFlowStep.PREVIEW) {
                    ReportSendingReview(state, generalSelected, generalDestinationLabel, selectedTargets,
                        showSendingReview, onToggleReview = { showSendingReview = !showSendingReview },
                        busy = busy, onReviewPatients = onReviewPatients, showChanges = showChanges,
                        onToggleChanges = { showChanges = !showChanges }, pdfSelected = pdfSelected,
                        exportPdf = exportPdf, onExportPdf = { exportPdf = it }, recoveryContent = recoveryContent)
                }
                if (step == ReportFlowStep.PREVIEW) {
                    ReportFullPreview(reviewed = fullReportPreviewed,
                        enabled = !busy && state.summary != null, onPreview = ::openPreview,
                        onSortPatients = { pendingDelivery = null; showPatientSort = true })
                }

            }
        }
    }

    if (showTextPreview) {
        AppAlertDialog(onDismissRequest = { showTextPreview = false }, title = { AppText(uiText(R.string.ui_report_preview_screen_077)) },
            text = { Column(Modifier.appDialogBody().verticalScroll(rememberScrollState())) { AppText(state.previewMarkdown) } },
            confirmButton = { AppTextButton(onClick = { showTextPreview = false }) { AppText(uiText(R.string.ui_close)) } })
    }
    if (showDeliveryLog) ReportDeliveryLogDialog(
        records = state.deliveryRecords, busy = busy, onResolveDelivery = viewModel::resolveDelivery,
        onAllowCopy = { deliveryDecision = it to DeliveryResolution.ALLOW_COPY },
        onDismiss = { showDeliveryLog = false })
    deliveryDecision?.let { (row, decision) ->
        ReportDeliveryDecisionDialog(row = row, decision = decision, busy = busy,
            onConfirm = { deliveryDecision = null; viewModel.resolveDelivery(row, decision) },
            onDismiss = { deliveryDecision = null })
    }

    pendingDelivery?.let { review ->
        val targetsUnchanged = review.supervisors == selectedTargets &&
            review.supervisors.all { it in state.supervisorTargets } &&
            review.includeGeneral == generalSelected && review.generalAsPdf == state.reportAsPdf &&
            review.exportPdf == (exportPdf && pdfSelected)
        val destinations = buildList {
            if (review.includeGeneral) add(generalDestinationLabel)
            review.supervisors.forEach { add(uiText(R.string.ui_report_preview_screen_095, it.doctorName, uiNumber(it.patientCount)) + " • " + it.destinationLabel(uiText)) }
        }
        val format = if (!review.includeGeneral || review.generalAsPdf) "PDF"
            else if (review.supervisors.isEmpty()) uiText(R.string.ui_report_preview_screen_015)
            else uiText(R.string.ui_report_mixed_format)
        val confirmedSupervisorIds = review.supervisors.mapTo(linkedSetOf()) { it.doctorId }
        val reportPatientIds = state.patients.filter {
            review.includeGeneral || it.responsibleSpecialistId in confirmedSupervisorIds
        }.mapTo(linkedSetOf()) { it.id }
        ReportSendConfirmation(destinations = destinations, format = format, exportPdf = review.exportPdf,
            warningCount = state.incompletePatientIds.intersect(reportPatientIds).size,
            patientCount = reportPatientIds.size,
            enabled = sendDisabledReason == null && targetsUnchanged && destinations.isNotEmpty(),
            unavailableReason = if (targetsUnchanged) sendDisabledReason else uiText(R.string.ui_report_preview_screen_096),
            onConfirm = {
                pendingDelivery = null
                viewModel.sendSelectedReports(review.includeGeneral,
                    review.supervisors.mapTo(linkedSetOf()) { it.doctorId }, review.exportPdf)
            }, onDismiss = { pendingDelivery = null })
    }
}
