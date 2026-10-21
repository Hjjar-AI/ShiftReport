package com.hos.rushdpatients.ui.report

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
import com.hos.rushdpatients.ui.components.AppFieldPair
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.ui.components.AppPdfStylePicker
import com.hos.rushdpatients.ui.components.AppPdfAppearanceControls
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
    viewModel: ReportPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var step by rememberSaveable { mutableStateOf(ReportFlowStep.DOCTORS) }
    val context = LocalContext.current
    var supervisorMenuOpen by remember { mutableStateOf(false) }
    var supervisorMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var supervisorSelectionInitialized by rememberSaveable { mutableStateOf(false) }
    var showRosterDetails by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showTextPreview by remember { mutableStateOf(false) }
    var reviewedText by remember { mutableStateOf<String?>(null) }
    var selectedSupervisorIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showShiftDoctors by remember { mutableStateOf(false) }
    var showDeliveryLog by remember { mutableStateOf(false) }
    var deliveryDecision by remember { mutableStateOf<Pair<ReportDeliveryRecord, DeliveryResolution>?>(null) }
    var showTools by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showChanges by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var customizeReport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmSend by remember { mutableStateOf(false) }
    var pendingSupervisorTargets by remember { mutableStateOf<List<SupervisorReportTarget>?>(null) }
    val busy = state.loading || state.savingDoctors || state.sending || state.previewingPdf ||
        state.exportingLocalPdf || state.sharingPdf || state.savingPdfOptions
    val selectedTargets = state.supervisorTargets.filter { it.doctorId in selectedSupervisorIds && it.chatId != null }
    val pdfSelected = supervisorMode || state.reportAsPdf
    var openedPdfUris by remember(state.pdfReviewGeneration) { mutableStateOf(emptySet<String>()) }
    val previewReady = if (!pdfSelected) reviewedText == state.previewMarkdown
        else if (supervisorMode) selectedTargets.isNotEmpty() && selectedTargets.all { target ->
            state.pdfReviewUris["supervisor:${target.doctorId}"]?.let { it in openedPdfUris } == true
        }
        else state.pdfReviewUris["general"]?.let { it in openedPdfUris } == true
    val destinationReady = state.summary != null && (!supervisorMode || selectedTargets.isNotEmpty())
    val nextDisabledReason = when {
        busy -> "انتظر انتهاء العملية الحالية"
        step == ReportFlowStep.DOCTORS && state.shift == null -> "تعذر تحميل المناوبة؛ أعد فتح التقرير"
        step == ReportFlowStep.DOCTORS && state.doctors.isEmpty() -> "اختر أطباء المناوبة أولاً"
        step != ReportFlowStep.DOCTORS && !destinationReady ->
            if (supervisorMode && selectedTargets.isEmpty()) "اختر مجموعة مشرف واحدة على الأقل"
            else "أكمل بيانات التقرير وراجع الملاحظات"
        step == ReportFlowStep.PREVIEW && !previewReady ->
            if (supervisorMode) "عاين ملف كل مجموعة مختارة أولاً" else "افتح معاينة التقرير أولاً"
        else -> null
    }
    fun openPreview() {
        if (supervisorMode) supervisorMenuOpen = true
        else if (state.reportAsPdf) viewModel.previewPdf()
        else { reviewedText = state.previewMarkdown; showTextPreview = true }
    }
    fun previousStep() {
        if (!busy) {
            if (step == ReportFlowStep.DOCTORS) onBack() else step = step.previous()
        }
    }
    BackHandler(enabled = busy || step != ReportFlowStep.DOCTORS) { previousStep() }
    val sendDisabledReason = when {
        busy -> "انتظر انتهاء العملية الحالية"
        state.isReadOnly -> "هذه المناوبة للعرض فقط"
        state.summary == null -> if (state.doctors.isEmpty()) "اختر أطباء المناوبة أولاً" else "أكمل بيانات التقرير وراجع الملاحظات أعلاه"
        supervisorMode && selectedTargets.isEmpty() -> "اختر مجموعة مشرف واحدة على الأقل"
        !previewReady -> if (supervisorMode) "ارجع إلى المعاينة وافتح كل ملف مختار"
            else "ارجع إلى المعاينة وافتح التقرير"
        else -> null
    }
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
            openedPdfUris = openedPdfUris + uri.toString()
            viewModel.consumePdfPreview()
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
                    "مشاركة التقرير"
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
            initialRevision = state.shift?.revision ?: 0L,
            saving = state.savingDoctors,
            onConfirm = { ids, revision, onStale ->
                viewModel.setShiftDoctors(ids, revision, onStale) { showShiftDoctors = false }
            },
            onDismiss = { showShiftDoctors = false }
        )
    }

    val stepScrolls = mapOf(
        ReportFlowStep.DOCTORS to rememberScrollState(),
        ReportFlowStep.DESTINATION to rememberScrollState(),
        ReportFlowStep.PREVIEW to rememberScrollState(),
        ReportFlowStep.SEND to rememberScrollState()
    )
    val contentScroll = stepScrolls.getValue(step)
    Scaffold(
        topBar = {
            Column {
                AppTopBar(title = "إرسال تقرير · ${step.arabicLabel}", onBack = ::previousStep)
                ReportFlowProgress(step)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = UiSpacing.tiny) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(UiSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    Text(if (supervisorMode) "${uiNumber(selectedTargets.size)} مجموعات مشرفين · PDF"
                        else "التقرير العام · ${if (state.reportAsPdf) "PDF" else "نصي"}",
                        style = MaterialTheme.typography.labelSmall)
                    (if (step == ReportFlowStep.SEND) sendDisabledReason else nextDisabledReason)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    AppFieldPair(first = { buttonModifier ->
                        AppOutlinedButton(onClick = ::previousStep, enabled = !busy, modifier = buttonModifier) {
                            Text(if (step == ReportFlowStep.DOCTORS) "إلغاء" else "السابق")
                        }
                    }, second = { buttonModifier ->
                        if (step == ReportFlowStep.SEND) {
                            LoadingButton(text = UiAction.SEND_REPORT.label, loading = state.sending,
                                modifier = buttonModifier, enabled = sendDisabledReason == null,
                                onClick = {
                                    if (supervisorMode) pendingSupervisorTargets = selectedTargets.toList()
                                    else confirmSend = true
                                })
                        } else {
                            AppButton(onClick = { step = step.next() }, enabled = nextDisabledReason == null,
                                modifier = buttonModifier) { Text("التالي · ${step.next().arabicLabel}") }
                        }
                    })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AppLoadingState("جار تحميل التقرير…")
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
                if (step == ReportFlowStep.DOCTORS) {
                    AppSection(title = "أطباء المناوبة", colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ), titleColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        if (state.doctors.isEmpty()) Text("اختر أطباء المناوبة من الزر أدناه")
                        else {
                            Text(state.doctors.take(2).joinToString("، ") { it.fullName } +
                                if (state.doctors.size > 2) " · +${uiNumber(state.doctors.size - 2)}" else "")
                            if (state.doctors.size > 2) AppTextButton(onClick = { showRosterDetails = !showRosterDetails }) {
                                Text(if (showRosterDetails) "طي الأسماء" else "جميع الأسماء (${uiNumber(state.doctors.size)})")
                            }
                            if (showRosterDetails) state.doctors.forEach { Text("• ${it.fullName}") }
                        }
                        if (!state.isReadOnly) AppOutlinedButton(
                            onClick = { confirmSend = false; showShiftDoctors = true },
                            enabled = !state.savingPdfOptions && state.shift != null && !state.savingDoctors && !state.sending &&
                                !state.previewingPdf && !state.exportingLocalPdf && !state.sharingPdf,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.doctors.isEmpty()) "اختيار أطباء المناوبة" else "تعديل أطباء المناوبة") }
                    }
                }
                if (step == ReportFlowStep.DESTINATION) {
                    AppSection(title = "وجهة التقرير وصيغته") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                            FilterChip(selected = !supervisorMode, enabled = !busy, onClick = { supervisorMode = false },
                                label = { Text("التقرير العام") })
                            FilterChip(selected = supervisorMode, enabled = !busy, onClick = {
                                if (!supervisorSelectionInitialized) {
                                    supervisorSelectionInitialized = true
                                    selectedSupervisorIds = state.supervisorTargets.filter { it.chatId != null }.mapTo(linkedSetOf()) { it.doctorId }
                                }
                                supervisorMode = true
                            }, label = { Text("مجموعات المشرفين") })
                        }
                        if (supervisorMode) {
                            Text("ملف PDF مستقل لكل مجموعة مختارة. عاين كل ملف قبل الإرسال.", style = MaterialTheme.typography.bodySmall)
                            if (state.supervisorTargets.isEmpty()) EmptyState("لا يوجد مشرفون مرتبطون بمرضى هذه المناوبة")
                            state.supervisorTargets.forEach { target ->
                                AppCheckRow(label = "${target.doctorName} · ${uiNumber(target.patientCount)} مريض" +
                                    if (target.chatId == null) " · لا توجد مجموعة" else "",
                                    checked = target.doctorId in selectedSupervisorIds, enabled = !busy && target.chatId != null,
                                    onCheckedChange = { checked -> selectedSupervisorIds = if (checked) selectedSupervisorIds + target.doctorId else selectedSupervisorIds - target.doctorId })
                            }
                        } else {
                            Text("جميع مرضى المناوبة · مجموعة المشروع", style = MaterialTheme.typography.bodySmall)
                            AppToggleRow(title = "إرسال كملف PDF", checked = state.reportAsPdf,
                                onCheckedChange = viewModel::setReportAsPdf, enabled = state.summary != null && !busy)
                        }
                    }
                }
                if (step == ReportFlowStep.PREVIEW || step == ReportFlowStep.SEND) {
                    state.summary?.let { summary ->
                        AppSection(title = "ملخص التقرير") {
                            Text("${uiNumber(summary.patientCount)} مريض · ${uiNumber(summary.escortCount)} مرافق · ${uiNumber(summary.shiftDate)}")
                            if (summary.psychoCount > 0) Text("حالات نفسية: ${uiNumber(summary.psychoCount)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                }
                state.publicationOutcome?.let { publication ->
                    ReportAttemptStatus(publication, state.deliveryOutcome ?: ReportStepOutcome.NOT_STARTED,
                        busy = busy, onHistory = { showDeliveryLog = true }, onBack = onBack)
                }
                if (state.deliveryRecords.any { it.status == "UNKNOWN" } &&
                    state.deliveryOutcome != ReportStepOutcome.UNCERTAIN && state.deliveryOutcome != ReportStepOutcome.PARTIAL &&
                    state.publicationOutcome != ReportStepOutcome.UNCERTAIN) {
                    AppNotice("يوجد تسليم غير مؤكد محفوظ على هذا الجهاز؛ احسمه من السجل قبل الإعادة إلى الوجهة نفسها.",
                        kind = NoticeKind.WARNING, actionLabel = "مراجعة سجل التسليم",
                        onAction = { showDeliveryLog = true }, actionEnabled = !busy)
                }
                state.error?.let { err ->
                    AppSection(title = "سبب التعذر والخطوة التالية") {
                        AppNotice(err, kind = NoticeKind.ERROR,
                            actionLabel = when (state.retryAction) {
                                ReportRetryAction.PREVIEW -> "معاينة PDF مجدداً"
                                ReportRetryAction.SEND -> "مراجعة الإرسال مجدداً"
                                ReportRetryAction.SAVE -> "إعادة حفظ PDF محلياً"
                                ReportRetryAction.SHARE -> "إعادة مشاركة PDF"
                                ReportRetryAction.ASSIGNMENTS -> "إعادة تصدير توزيع المرضى"
                                null -> null
                            },
                            onAction = if (state.retryAction == null) null else ({
                                if (state.retryAction == ReportRetryAction.SEND) {
                                    step = if (previewReady) ReportFlowStep.SEND else ReportFlowStep.PREVIEW
                                } else viewModel.retryLastAction()
                            }),
                            actionEnabled = !state.sending && !state.previewingPdf && !state.exportingLocalPdf && !state.sharingPdf)
                        when (state.doctorRegistryAction) {
                            com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.FETCH ->
                                AppOutlinedButton(onClick = viewModel::fetchDoctorsForReport, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()) { Text("جلب سجل الأطباء ثم إعادة المراجعة") }
                            com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH -> {
                                if (state.isAdmin) AppOutlinedButton(onClick = onOpenDoctors,
                                    enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("فتح سجل الأطباء لنشر التعديلات") }
                                else AppNotice("اطلب من مدير المشروع مراجعة تعديلات سجل الأطباء ونشرها أولاً.")
                            }
                            null -> Unit
                        }
                    }
                }
                state.lastOperation?.let { AppNotice(it) }
                if (state.isReadOnly) {
                    AppNotice("تقرير مناوبة محفوظة: المعاينة والحفظ المحلي متاحان، أما الإرسال والنشر فمتوقفان.")
                }

                if (step == ReportFlowStep.PREVIEW || step == ReportFlowStep.SEND) {
                    if (state.readinessWarnings.isNotEmpty()) {
                        AppSection(
                            title = "ملاحظات جاهزية التقرير",
                            titleColor = noticeColors(NoticeKind.WARNING).second,
                            colors = CardDefaults.cardColors(
                                containerColor = noticeColors(NoticeKind.WARNING).first
                            )
                        ) {
                            state.readinessWarnings.forEach { warning ->
                                Text("• $warning", color = noticeColors(NoticeKind.WARNING).second)
                            }
                            Text(
                                "يمكن المتابعة رغم هذه الملاحظات.",
                                style = MaterialTheme.typography.labelSmall,
                                color = noticeColors(NoticeKind.WARNING).second
                            )
                        }
                    }

                    AppSection(
                        title = "ما الذي تغيّر منذ آخر نشر؟",
                        titleColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        if (state.changeBriefing.isEmpty()) {
                            Text("لا توجد تغييرات محلية غير منشورة")
                        } else {
                            AppTextButton(onClick = { showChanges = !showChanges }) {
                                Text("${uiNumber(state.changeBriefing.size)} تغييرات · " + if (showChanges) "طي التفاصيل" else "عرض التفاصيل")
                            }
                            if (showChanges) state.changeBriefing.forEach { Text("• $it") }
                        }
                    }


                }
                if (step == ReportFlowStep.PREVIEW && pdfSelected) {
                    AppTextButton(onClick = { customizeReport = !customizeReport }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (customizeReport) "إغلاق تخصيص PDF" else "تخصيص PDF (اختياري)")
                    }
                    if (customizeReport) AppSection(title = "تخصيص PDF") {
                        AppPdfStylePicker(selected = state.pdfStyle,
                            onSelected = { viewModel.setPdfAppearance(it, state.pdfOptions) }, enabled = !busy)
                        val options = state.pdfOptions
                        fun update(value: PdfExportOptions) = viewModel.setPdfAppearance(state.pdfStyle, value)
                        if (state.pdfStyle != PdfStyle.CARDS) {
                            Text("حجم الورق والاتجاه", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                PdfPaperSize.entries.forEach { value ->
                                    FilterChip(selected = options.paperSize == value, enabled = !busy,
                                        onClick = { update(options.copy(paperSize = value)) }, label = { Text(value.arabicLabel) })
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                PdfOrientation.entries.forEach { value ->
                                    FilterChip(selected = options.orientation == value, enabled = !busy,
                                        onClick = { update(options.copy(orientation = value)) }, label = { Text(value.arabicLabel) })
                                }
                            }
                        }
                        AppToggleRow(title = "PDF داكن", checked = options.darkMode, enabled = !busy,
                            onCheckedChange = { update(options.copy(darkMode = it)) })
                        AppPdfAppearanceControls(weight = options.ruleWeight, contrast = options.ruleContrast,
                            shading = options.rowShading, dark = options.darkMode, enabled = !busy,
                            showShading = state.pdfStyle != PdfStyle.CARDS,
                            onWeight = { update(options.copy(ruleWeight = it)) },
                            onContrast = { update(options.copy(ruleContrast = it)) },
                            onShading = { update(options.copy(rowShading = it)) },
                            onPreset = { update(options.copy(ruleWeight = it.weight, ruleContrast = it.contrast,
                                rowShading = it.shading, darkMode = if (it.lightOnly) false else options.darkMode)) })
                        Text("بعد تغيير المظهر، عاين PDF مجدداً قبل الإرسال.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (step == ReportFlowStep.PREVIEW) {
                    AppNotice(if (supervisorMode) "عاين ملف PDF لكل مجموعة مختارة، ثم انتقل إلى مراجعة الإرسال."
                        else "افتح التقرير وافحص محتواه، ثم انتقل إلى مراجعة الإرسال.")
                    LoadingButton(text = if (supervisorMode) "معاينة ملفات المشرفين" else if (state.reportAsPdf) "معاينة PDF" else "معاينة النص",
                        outlined = true, loading = state.previewingPdf, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && destinationReady, onClick = ::openPreview)
                    if (previewReady) Text("تم فتح المعاينة؛ يمكنك المتابعة إلى الإرسال.", style = MaterialTheme.typography.bodySmall)
                }
                if (step == ReportFlowStep.SEND) AppSection(title = "مراجعة الإرسال") {
                    Text("أطباء المناوبة: " + state.doctors.joinToString("، ") { it.fullName })
                    if (supervisorMode) selectedTargets.forEach { target ->
                        Text("مجموعة ${target.doctorName} · ${uiNumber(target.patientCount)} مريض · PDF")
                    } else Text("مجموعة المشروع · ${if (state.reportAsPdf) "PDF" else "نصي"}")
                    Text("بعد الضغط على «إرسال تقرير» ستظهر مراجعة التأكيد النهائي للنشر والتسليم.",
                        style = MaterialTheme.typography.bodySmall)
                }
                AppTextButton(onClick = { showTools = !showTools }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (showTools) "إغلاق أدوات الحفظ والسجل" else "الحفظ وسجل التسليم")
                }
                if (showTools) AppSection(title = "ملفات محلية") {
                    Text("الحفظ والمشاركة لا ينشران بيانات المرضى أو يرسلان التقرير إلى المشروع.",
                        style = MaterialTheme.typography.bodySmall)
                    if (state.reportAsPdf) {
                        LoadingButton(text = "حفظ PDF محلياً", outlined = true, loading = state.exportingLocalPdf,
                            onClick = viewModel::exportLocally, modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && state.summary != null)
                        LoadingButton(text = "مشاركة PDF", outlined = true, loading = state.sharingPdf,
                            onClick = viewModel::sharePdf, modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && state.summary != null)
                    } else Text("اختر صيغة PDF من خطوة الوجهة لإظهار حفظ التقرير ومشاركته.", style = MaterialTheme.typography.bodySmall)
                    if (state.isAdmin) {
                        AppOutlinedButton(onClick = viewModel::exportAssignments, modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && state.shift != null) { Text("حفظ توزيع المرضى بالأسماء فقط (PDF)") }
                        Text("A4 عمودي · أسماء المقيمين والمرضى في جداول الفرق.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showTools) AppTextButton(onClick = { showDeliveryLog = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("سجل التسليم — التحقق من محاولة سابقة") }

            }
        }
    }

    if (showTextPreview) {
        AlertDialog(onDismissRequest = { showTextPreview = false }, title = { Text("معاينة التقرير العام") },
            text = { Column(Modifier.appDialogBody().verticalScroll(rememberScrollState())) { Text(state.previewMarkdown) } },
            confirmButton = { AppTextButton(onClick = { showTextPreview = false }) { Text("إغلاق") } })
    }
    if (supervisorMenuOpen) {
        AlertDialog(onDismissRequest = { supervisorMenuOpen = false }, title = { Text("معاينة ملفات المشرفين") }, text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState())) {
                Text("افتح ملف كل مجموعة مختارة وافحصه. سيُرسل الملف نفسه بعد التأكيد.")
                selectedTargets.forEach { target ->
                    AppTextButton(onClick = { viewModel.previewSupervisorPdf(target.doctorId) }, enabled = !busy) {
                        Text("معاينة PDF — ${target.doctorName}")
                    }
                }
            }
        }, confirmButton = { AppTextButton(onClick = { supervisorMenuOpen = false }) { Text("العودة للتقرير") } })
    }
    if (showDeliveryLog) {
        AlertDialog(onDismissRequest = { showDeliveryLog = false }, title = { Text("سجل تسليم هذه المناوبة — هذا الجهاز") }, text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text("افحص تليجرام أولاً. انتهاء المهلة أو غياب الإيصال المحلي لا يثبت عدم الوصول. الحسم للمرسل أو لمدير نشط.")
                if (state.deliveryRecords.isEmpty()) Text("لا توجد محاولات محفوظة لهذه المناوبة")
                state.deliveryRecords.asReversed().forEach { row ->
                    val label = when (row.status) { "UNKNOWN" -> "غير مؤكد"; "SENT" -> "سُلّم / أُكد وصوله"; else -> "أُكد عدم وصوله" }
                    Text("${row.label} · ${row.format} · $label")
                    Text(java.time.Instant.ofEpochMilli(row.at).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        style = MaterialTheme.typography.bodySmall)
                    if (row.status == "UNKNOWN") {
                        AppTextButton(onClick = { deliveryDecision = row to DeliveryResolution.RECEIVED }, enabled = !busy) { Text("تحققت أنه وصل") }
                        AppTextButton(onClick = { deliveryDecision = row to DeliveryResolution.NOT_RECEIVED }, enabled = !busy) { Text("تحققت أنه لم يصل") }
                    } else if (row.status == "SENT") {
                        AppTextButton(onClick = { deliveryDecision = row to DeliveryResolution.ALLOW_COPY }, enabled = !busy) { Text("السماح بنسخة إضافية واحدة") }
                    }
                }
            }
        }, confirmButton = { AppTextButton(onClick = { showDeliveryLog = false }) { Text("إغلاق") } })
    }
    deliveryDecision?.let { (row, decision) ->
        val instruction = when (decision) {
            DeliveryResolution.RECEIVED -> "بعد فحص تليجرام، هل تؤكد وصول هذا التقرير؟ لن يُسمح بتكراره تلقائياً."
            DeliveryResolution.NOT_RECEIVED -> "بعد فحص تليجرام وكل أجزاء التقرير، هل تؤكد أنه لم يصل؟ يسمح ذلك بمحاولة جديدة بعد المراجعة."
            DeliveryResolution.ALLOW_COPY -> "هذا التقرير سُلّم بالفعل. هل تسمح بنسخة إضافية واحدة من المحتوى نفسه، بعد معاينته وتأكيد إرساله؟"
        }
        AlertDialog(onDismissRequest = { deliveryDecision = null }, title = { Text("تأكيد قرار التسليم") },
            text = { Text(row.label + "\n" + instruction) },
            confirmButton = { AppButton(onClick = { deliveryDecision = null; viewModel.resolveDelivery(row, decision) }, enabled = !busy) { Text("أؤكد") } },
            dismissButton = { AppTextButton(onClick = { deliveryDecision = null }) { Text("إلغاء") } })
    }

    pendingSupervisorTargets?.let { targets ->
        val targetsUnchanged = targets.all { it in state.supervisorTargets }
        ReportSendConfirmation(destinations = targets.map { "مجموعة ${it.doctorName} · ${uiNumber(it.patientCount)} مريض" },
            format = "PDF", warningCount = state.readinessWarnings.size,
            enabled = sendDisabledReason == null && targetsUnchanged && targets.isNotEmpty(),
            unavailableReason = if (targetsUnchanged) sendDisabledReason else "تغيّرت مجموعات المشرفين؛ أغلق هذه المراجعة واختر الوجهات من جديد.",
            onConfirm = { forceLocal ->
                pendingSupervisorTargets = null
                viewModel.sendSupervisorReports(targets.mapTo(linkedSetOf()) { it.doctorId }, forceLocal)
            }, onDismiss = { pendingSupervisorTargets = null })
    }

    if (confirmSend) {
        ReportSendConfirmation(destinations = listOf("مجموعة المشروع — تقرير المناوبة الكامل"),
            format = if (state.reportAsPdf) "PDF" else "نصي", warningCount = state.readinessWarnings.size,
            enabled = sendDisabledReason == null,
            unavailableReason = sendDisabledReason,
            onConfirm = { forceLocal -> confirmSend = false; viewModel.send(forceLocal) }, onDismiss = { confirmSend = false })
    }
}
