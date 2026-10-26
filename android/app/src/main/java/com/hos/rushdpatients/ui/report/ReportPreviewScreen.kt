package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.domain.report.DeliveryStatus
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.noticeColors
import com.hos.rushdpatients.ui.components.AppCheckRow
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.ReportSendConfirmation
import com.hos.rushdpatients.ui.components.AppLoadingState
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppSection
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.ui.components.AppPdfStylePicker
import com.hos.rushdpatients.ui.components.AppPdfAppearanceControls
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
import androidx.compose.material3.Text
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
    var showRosterDetails by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showTextPreview by remember { mutableStateOf(false) }
    var reviewedText by remember { mutableStateOf<String?>(null) }
    var selectedSupervisorIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showShiftDoctors by remember { mutableStateOf(false) }
    var showDeliveryLog by remember { mutableStateOf(false) }
    var deliveryDecision by remember { mutableStateOf<Pair<ReportDeliveryRecord, DeliveryResolution>?>(null) }
    var showExportMenu by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showChanges by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var customizeReport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var pendingDelivery by remember { mutableStateOf<ReportDestinationReview?>(null) }
    val selectedTargets = state.supervisorTargets.filter { it.doctorId in selectedSupervisorIds && it.chatId != null }
    val supervisorMode = selectedTargets.isNotEmpty()
    val pdfSelected = supervisorMode || state.reportAsPdf
    val flowGate = reportFlowGate(state, step, supervisorMode, selectedTargets, reviewedText, state.openedPdfUris, generalSelected)
    val busy = flowGate.busy
    val previewReady = flowGate.previewReady
    val destinationReady = flowGate.destinationReady
    val nextDisabledReason = flowGate.nextDisabledReason?.let { uiText(it) }
    val sendDisabledReason = flowGate.sendDisabledReason?.let { uiText(it) }
    val previews = buildList {
        if (generalSelected) add(ReportPreviewItem("general", uiText(R.string.ui_report_preview_screen_025),
            flowGate.previewReadiness.getValue("general")))
        selectedTargets.forEach { target ->
            val key = "supervisor:${target.doctorId}"
            add(ReportPreviewItem(key, target.doctorName, flowGate.previewReadiness.getValue(key)))
        }
    }
    val nextPreview = previews.firstOrNull { !it.reviewed }
    fun preview(item: ReportPreviewItem) {
        if (busy || !destinationReady) return
        if (item.key != "general") viewModel.previewSupervisorPdf(item.key.removePrefix("supervisor:"))
        else if (state.reportAsPdf) viewModel.previewPdf()
        else { reviewedText = state.previewMarkdown; showTextPreview = true }
    }
    fun openPreview() {
        (nextPreview ?: previews.firstOrNull())?.let(::preview)
    }
    fun previousStep() {
        if (!busy) {
            if (step == ReportFlowStep.DOCTORS) onBack() else step = step.previous()
        }
    }
    BackHandler(enabled = busy || step != ReportFlowStep.DOCTORS) { previousStep() }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

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

    val stepScrolls = mapOf(
        ReportFlowStep.DOCTORS to rememberScrollState(),
        ReportFlowStep.PREVIEW to rememberScrollState()
    )
    val contentScroll = stepScrolls.getValue(step)
    Scaffold(
        topBar = {
            Column {
                AppTopBar(title = uiText(R.string.ui_report_preview_screen_013, step.arabicLabel(uiText)), onBack = ::previousStep)
                if (viewModel.simulation) AppNotice(uiText(R.string.demo_simulation_notice))
                ReportFlowProgress(step)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = com.hos.rushdpatients.ui.theme.LocalUiStyle.current.actionBarElevation) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(UiSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    val targetSummary = uiText(R.string.ui_report_selected_destinations,
                        uiNumber(selectedTargets.size + if (generalSelected) 1 else 0))
                    Text(targetSummary, style = MaterialTheme.typography.bodySmall)
                    val previewNeeded = step == ReportFlowStep.PREVIEW && !previewReady
                    val disabledReason = when {
                        step == ReportFlowStep.DOCTORS -> nextDisabledReason
                        !destinationReady -> sendDisabledReason
                        previewNeeded -> null // The primary action opens the next required preview directly.
                        else -> sendDisabledReason
                    }
                    disabledReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    when {
                        step == ReportFlowStep.DOCTORS -> AppButton(
                            onClick = { step = step.next() }, enabled = nextDisabledReason == null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(uiText(R.string.ui_report_preview_screen_017, step.next().arabicLabel(uiText))) }
                        previewNeeded -> LoadingButton(
                            text = if (state.previewingPdf) uiText(R.string.ui_progress_prepare_preview)
                                else nextPreview?.let { uiText(R.string.report_preview_destination, it.label) }
                                    ?: uiText(R.string.ui_report_preview_screen_058),
                            loading = state.previewingPdf, enabled = !busy && destinationReady && nextPreview != null,
                            modifier = Modifier.fillMaxWidth(), onClick = ::openPreview)
                        else -> LoadingButton(text = UiAction.SEND_REPORT.label(uiText), loading = state.sending,
                            modifier = Modifier.fillMaxWidth(), enabled = sendDisabledReason == null,
                            onClick = {
                                pendingDelivery = ReportDestinationReview(generalSelected, selectedTargets.toList(), state.reportAsPdf, exportPdf && pdfSelected)
                            })
                    }
                    AppTextButton(onClick = ::previousStep, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (step == ReportFlowStep.DOCTORS) uiText(R.string.ui_cancel) else uiText(R.string.ui_previous))
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
                    .padding(padding)
                    .padding(UiSpacing.screen)
                    .verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                if (step == ReportFlowStep.PREVIEW) {
                    ReportPreviewList(previews, enabled = !busy && destinationReady, onPreview = ::preview)
                }
                if (step == ReportFlowStep.DOCTORS) {
                    AppSection(title = uiText(R.string.ui_shift_doctor_picker_001), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ), titleColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        if (state.doctors.isEmpty()) Text(uiText(R.string.ui_report_preview_screen_019))
                        else {
                            Text(state.doctors.take(2).joinToString(uiText(R.string.ui_patient_card_002)) { it.fullName } +
                                if (state.doctors.size > 2) " · +${uiNumber(state.doctors.size - 2)}" else "")
                            if (state.doctors.size > 2) AppTextButton(onClick = { showRosterDetails = !showRosterDetails }) {
                                Text(if (showRosterDetails) uiText(R.string.ui_report_preview_screen_020) else uiText(R.string.ui_report_preview_screen_021, uiNumber(state.doctors.size)))
                            }
                            if (showRosterDetails) state.doctors.forEach { Text("• ${it.fullName}") }
                        }
                        AppOutlinedButton(
                            onClick = { pendingDelivery = null; showShiftDoctors = true },
                            enabled = !state.isBusy && state.shift != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.doctors.isEmpty()) uiText(R.string.ui_report_preview_screen_022) else uiText(R.string.ui_report_preview_screen_023)) }
                    }
                }
                if (step == ReportFlowStep.DOCTORS) {
                    AppSection(title = uiText(R.string.ui_report_preview_screen_024)) {
                        AppCheckRow(label = uiText(R.string.ui_report_preview_screen_025),
                            checked = generalSelected, enabled = !busy,
                            onCheckedChange = { generalSelected = it })
                        val selectableIds = state.supervisorTargets.filter { it.chatId != null }.mapTo(linkedSetOf()) { it.doctorId }
                        AppCheckRow(label = uiText(R.string.ui_report_select_all_destinations),
                            checked = generalSelected && selectableIds.all { it in selectedSupervisorIds },
                            enabled = !busy,
                            onCheckedChange = { checked ->
                                generalSelected = checked
                                selectedSupervisorIds = if (checked) selectableIds else emptySet()
                            })
                        Text(uiText(R.string.ui_report_destination_checklist_hint), style = MaterialTheme.typography.bodySmall)
                        state.supervisorTargets.forEach { target ->
                            AppCheckRow(label = uiText(R.string.ui_report_preview_screen_028, target.doctorName, uiNumber(target.patientCount)) +
                                if (target.chatId == null) uiText(R.string.ui_report_preview_screen_029) else "",
                                checked = target.doctorId in selectedSupervisorIds && target.chatId != null,
                                enabled = !busy && target.chatId != null,
                                onCheckedChange = { checked -> selectedSupervisorIds = if (checked) selectedSupervisorIds + target.doctorId else selectedSupervisorIds - target.doctorId })
                        }
                        if (state.supervisorTargets.isEmpty()) Text(uiText(R.string.ui_report_preview_screen_027),
                            style = MaterialTheme.typography.bodySmall)
                        if (generalSelected) AppToggleRow(title = uiText(R.string.ui_report_preview_screen_031), checked = state.reportAsPdf,
                            onCheckedChange = viewModel::setReportAsPdf, enabled = state.summary != null && !busy)
                    }
                }
                if (step == ReportFlowStep.PREVIEW) {
                    state.summary?.let { summary ->
                        AppSection(title = uiText(R.string.ui_report_preview_screen_032)) {
                            val subtotalStyle = androidx.compose.ui.text.SpanStyle(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(androidx.compose.ui.text.buildAnnotatedString {
                                append(uiText(R.string.report_total_patients, uiNumber(summary.patientCount)))
                                append(" ")
                                pushStyle(subtotalStyle)
                                append(uiText(R.string.ui_report_preview_screen_034, uiNumber(summary.psychoCount)))
                                pop()
                            }, style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(uiText(R.string.report_escorts_and_date, uiNumber(summary.escortCount), uiNumber(summary.shiftDate)),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }

                }
                state.publicationOutcome?.let { publication ->
                    ReportAttemptStatus(publication, state.deliveryOutcome ?: ReportStepOutcome.NOT_STARTED,
                        busy = busy, onHistory = { showDeliveryLog = true }, onBack = onBack)
                }
                if (state.deliveryRecords.any { it.status == DeliveryStatus.UNKNOWN } &&
                    state.deliveryOutcome != ReportStepOutcome.UNCERTAIN && state.deliveryOutcome != ReportStepOutcome.PARTIAL &&
                    state.publicationOutcome != ReportStepOutcome.UNCERTAIN) {
                    AppNotice(uiText(R.string.ui_report_preview_screen_035),
                        kind = NoticeKind.WARNING, actionLabel = uiText(R.string.ui_ward_workflow_strip_007),
                        onAction = { showDeliveryLog = true }, actionEnabled = !busy)
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
                            onAction = if (state.retryAction == null) null else ({
                                if (state.retryAction == ReportRetryAction.SEND) {
                                    step = ReportFlowStep.PREVIEW
                                } else if (state.retryAction == ReportRetryAction.PREVIEW) openPreview()
                                else viewModel.retryLastAction { step = ReportFlowStep.PREVIEW }
                            }),
                            actionEnabled = !state.sending && !state.previewingPdf && !state.exportingLocalPdf && !state.sharingPdf)
                        when (state.doctorRegistryAction) {
                            com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.FETCH ->
                                AppOutlinedButton(onClick = viewModel::fetchDoctorsForReport, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_report_preview_screen_042)) }
                            com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH -> {
                                if (state.isAdmin) AppOutlinedButton(onClick = onOpenDoctors,
                                    enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_report_preview_screen_043)) }
                                else AppNotice(uiText(R.string.ui_report_preview_screen_044))
                            }
                            null -> Unit
                        }
                        if (state.doctorRegistryAction == com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH) {
                            AppOutlinedButton(onClick = { viewModel.load() }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_recovery_reload_report)) }
                        }
                    }
                }
                state.deliveryRecords.filter { it.status == DeliveryStatus.UNKNOWN }.forEach { receipt ->
                    AppSection(title = receipt.label) {
                        DeliveryQuestion(receipt, !busy, viewModel::resolveDelivery)
                    }
                }
                state.publicationWarning?.let { AppNotice(it, kind = NoticeKind.WARNING) }
                state.lastOperation?.let { AppNotice(it) }

                if (step == ReportFlowStep.PREVIEW) {
                    state.registryChangeSummary?.let { summary ->
                        AppSection(title = uiText(R.string.sync_publish_all)) {
                            Text(summary)
                            if (!state.isAdmin) Text(uiText(R.string.sync_doctors_stay_pending))
                        }
                    }
                    if (state.readinessWarnings.isEmpty()) AppNotice(uiText(R.string.report_readiness_ready), kind = NoticeKind.SUCCESS)
                    if (state.readinessWarnings.isNotEmpty()) {
                        AppSection(
                            title = uiText(R.string.ui_report_preview_screen_046),
                            titleColor = noticeColors(NoticeKind.WARNING).content,
                            colors = CardDefaults.cardColors(
                                containerColor = noticeColors(NoticeKind.WARNING).container
                            )
                        ) {
                            state.readinessIssues.forEach { issue ->
                                AppTextButton(onClick = { state.shift?.id?.let { onReviewPatients(it, issue.patientIds) } }, enabled = !busy) {
                                    Text(uiText(R.string.readiness_incomplete_patients, uiNumber(issue.patientIds.size)), color = noticeColors(NoticeKind.WARNING).content)
                                }
                            }
                            Text(
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
                            Text(uiText(R.string.ui_report_preview_screen_049))
                        } else {
                            AppTextButton(onClick = { showChanges = !showChanges }) {
                                Text(uiText(R.string.ui_report_preview_screen_050, uiNumber(state.changeBriefing.size)) + if (showChanges) uiText(R.string.ui_patient_card_027) else uiText(R.string.ui_ward_view_options_dialog_009))
                            }
                            if (showChanges) state.changeBriefing.forEach { Text("• $it") }
                        }
                    }


                }
                if (step == ReportFlowStep.PREVIEW && pdfSelected) {
                    AppTextButton(onClick = { customizeReport = !customizeReport }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (customizeReport) uiText(R.string.ui_report_preview_screen_051) else uiText(R.string.ui_report_preview_screen_052))
                    }
                    if (customizeReport) AppSection(title = uiText(R.string.ui_report_preview_screen_053)) {
                        AppPdfStylePicker(selected = state.pdfStyle,
                            onSelected = { viewModel.setPdfAppearance(it, state.pdfOptions) }, enabled = !busy)
                        val options = state.pdfOptions
                        fun update(value: PdfExportOptions) = viewModel.setPdfAppearance(state.pdfStyle, value)
                        if (state.pdfStyle != PdfStyle.CARDS) {
                            Text(uiText(R.string.ui_report_preview_screen_054), style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                PdfPaperSize.entries.forEach { value ->
                                    AppFilterChip(selected = options.paperSize == value, enabled = !busy,
                                        onClick = { update(options.copy(paperSize = value)) }, label = { Text(value.uiLabel(uiText)) })
                                }
                            }
                        }
                        AppPdfAppearanceControls(shading = options.rowShading, enabled = !busy,
                            showShading = state.pdfStyle != PdfStyle.CARDS,
                            onShading = { update(options.copy(rowShading = it)) })
                        Text(uiText(R.string.ui_report_preview_screen_055), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (step == ReportFlowStep.PREVIEW) AppSection(title = uiText(R.string.ui_report_preview_screen_062)) {
                    Text(uiText(R.string.ui_report_preview_screen_063) + state.doctors.joinToString(uiText(R.string.ui_patient_card_002)) { it.fullName })
                    if (generalSelected) Text(uiText(R.string.ui_report_preview_screen_065,
                        if (state.reportAsPdf) "PDF" else uiText(R.string.ui_report_preview_screen_015)))
                    selectedTargets.forEach { target ->
                        Text(uiText(R.string.ui_report_preview_screen_064, target.doctorName, uiNumber(target.patientCount)))
                    }
                    Text(uiText(R.string.ui_report_preview_screen_066),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (step == ReportFlowStep.PREVIEW) {
                    if (pdfSelected && state.summary != null) AppCheckRow(
                        label = uiText(R.string.report_optional_normal_export), checked = exportPdf,
                        enabled = !busy, onCheckedChange = { exportPdf = it })
                    AppOutlinedButton(onClick = { showExportMenu = true },
                        enabled = !busy && state.summary != null,
                        modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.report_export_draft_action)) }
                }
                AppTextButton(onClick = { showDeliveryLog = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_report_preview_screen_076)) }

            }
        }
    }

    if (showExportMenu) ReportExportDialog(state, viewModel, onDismiss = { showExportMenu = false })

    if (showTextPreview) {
        AppAlertDialog(onDismissRequest = { showTextPreview = false }, title = { Text(uiText(R.string.ui_report_preview_screen_077)) },
            text = { Column(Modifier.appDialogBody().verticalScroll(rememberScrollState())) { Text(state.previewMarkdown) } },
            confirmButton = { AppTextButton(onClick = { showTextPreview = false }) { Text(uiText(R.string.ui_close)) } })
    }
    if (showDeliveryLog) {
        AppAlertDialog(onDismissRequest = { showDeliveryLog = false }, title = { Text(uiText(R.string.ui_report_preview_screen_081)) }, text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(uiText(R.string.ui_report_preview_screen_082))
                if (state.deliveryRecords.isEmpty()) Text(uiText(R.string.ui_report_preview_screen_083))
                state.deliveryRecords.asReversed().forEach { row ->
                    val label = when (row.status) { DeliveryStatus.UNKNOWN_COPY_ALLOWED -> uiText(R.string.delivery_uncertain_copy_allowed); DeliveryStatus.UNKNOWN -> uiText(R.string.ui_report_preview_screen_084); DeliveryStatus.SENT -> uiText(R.string.ui_report_preview_screen_085); else -> uiText(R.string.ui_report_preview_screen_086) }
                    Text("${row.label} · ${row.format} · $label")
                    Text(java.time.Instant.ofEpochMilli(row.at).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        style = MaterialTheme.typography.bodySmall)
                    if (row.status == DeliveryStatus.UNKNOWN || row.status == DeliveryStatus.UNKNOWN_COPY_ALLOWED) {
                        DeliveryQuestion(row, !busy, viewModel::resolveDelivery)
                    } else if (row.status == DeliveryStatus.SENT) {
                        Text(uiText(R.string.ui_report_preview_screen_092), style = MaterialTheme.typography.bodySmall)
                        AppOutlinedButton(onClick = { deliveryDecision = row to DeliveryResolution.ALLOW_COPY }, enabled = !busy) {
                            Text(uiText(R.string.ui_report_preview_screen_089))
                        }
                    }
                }
            }
        }, confirmButton = { AppTextButton(onClick = { showDeliveryLog = false }) { Text(uiText(R.string.ui_close)) } })
    }
    deliveryDecision?.let { (row, decision) ->
        val instruction = when (decision) {
            DeliveryResolution.RECEIVED -> uiText(R.string.ui_report_preview_screen_090)
            DeliveryResolution.NOT_RECEIVED -> uiText(R.string.ui_report_preview_screen_091)
            DeliveryResolution.ALLOW_COPY -> uiText(R.string.ui_report_preview_screen_092)
        }
        AppAlertDialog(onDismissRequest = { deliveryDecision = null }, title = { Text(uiText(R.string.ui_report_preview_screen_093)) },
            text = { Text(row.label + "\n" + instruction) },
            confirmButton = { AppButton(onClick = { deliveryDecision = null; viewModel.resolveDelivery(row, decision) }, enabled = !busy) { Text(uiText(R.string.ui_report_preview_screen_094)) } },
            dismissButton = { AppTextButton(onClick = { deliveryDecision = null }) { Text(uiText(R.string.ui_cancel)) } })
    }

    pendingDelivery?.let { review ->
        val targetsUnchanged = review.supervisors == selectedTargets &&
            review.supervisors.all { it in state.supervisorTargets } &&
            review.includeGeneral == generalSelected && review.generalAsPdf == state.reportAsPdf &&
            review.exportPdf == (exportPdf && pdfSelected)
        val destinations = buildList {
            if (review.includeGeneral) add(uiText(R.string.ui_report_preview_screen_097))
            review.supervisors.forEach { add(uiText(R.string.ui_report_preview_screen_095, it.doctorName, uiNumber(it.patientCount))) }
        }
        val format = if (!review.includeGeneral || review.generalAsPdf) "PDF"
            else if (review.supervisors.isEmpty()) uiText(R.string.ui_report_preview_screen_015)
            else uiText(R.string.ui_report_mixed_format)
        val confirmedSupervisorIds = review.supervisors.mapTo(linkedSetOf()) { it.doctorId }
        val reportPatientIds = state.patients.filter {
            review.includeGeneral || it.responsibleSpecialistId in confirmedSupervisorIds
        }.mapTo(linkedSetOf()) { it.id }
        ReportSendConfirmation(destinations = destinations, format = format, exportPdf = review.exportPdf,
            warningCount = state.readinessIssues.flatMap { it.patientIds }.toSet().intersect(reportPatientIds).size,
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

@Composable
private fun DeliveryQuestion(row: ReportDeliveryRecord, enabled: Boolean,
    resolve: (ReportDeliveryRecord, DeliveryResolution) -> Unit) {
    val uiText = rememberUiStrings()
    Text(uiText(R.string.delivery_inline_question))
    com.hos.rushdpatients.ui.components.AppButton(style = com.hos.rushdpatients.ui.components.AppButtonStyle.TONAL, onClick = { resolve(row, DeliveryResolution.RECEIVED) }, enabled = enabled) { Text(uiText(R.string.delivery_received_explicit)) }
    com.hos.rushdpatients.ui.components.AppButton(style = com.hos.rushdpatients.ui.components.AppButtonStyle.TONAL, onClick = { resolve(row, DeliveryResolution.NOT_RECEIVED) }, enabled = enabled) { Text(uiText(R.string.delivery_not_received_explicit)) }
    if (row.status == DeliveryStatus.UNKNOWN) {
        com.hos.rushdpatients.ui.components.AppButton(style = com.hos.rushdpatients.ui.components.AppButtonStyle.TONAL, onClick = { resolve(row, DeliveryResolution.ALLOW_COPY) }, enabled = enabled) { Text(uiText(R.string.delivery_allow_copy_explicit)) }
    }
}
