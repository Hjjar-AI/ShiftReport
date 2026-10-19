package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.noticeColors
import com.hos.rushdpatients.ui.components.AppCheckRow
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.ReportSendConfirmation
import com.hos.rushdpatients.ui.components.AppLoadingState
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.ward.PatientConflictDialog
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.LongPressLoadingButton
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppSection
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.ui.ward.ShiftDoctorPicker
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.sync.ConflictChoice

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReportPreviewScreen(
    onBack: () -> Unit,
    viewModel: ReportPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var supervisorMenuOpen by remember { mutableStateOf(false) }
    var selectedSupervisorIds by remember { mutableStateOf(emptySet<String>()) }
    var showShiftDoctors by remember { mutableStateOf(false) }
    var confirmSend by remember { mutableStateOf(false) }
    var pendingSupervisorTargets by remember { mutableStateOf<List<SupervisorReportTarget>?>(null) }
    val busy = state.loading || state.savingDoctors || state.sending || state.previewingPdf ||
        state.exportingLocalPdf || state.sharingPdf || state.resolvingConflicts
    fun selectSupervisorGroups() {
        selectedSupervisorIds = state.supervisorTargets.filter { it.chatId != null }.mapTo(linkedSetOf()) { it.doctorId }
        supervisorMenuOpen = true
    }
    var conflictChoices by remember { mutableStateOf(emptyMap<String, ConflictChoice>()) }

    LaunchedEffect(state.mergeConflicts) {
        conflictChoices = conflictChoices.filterKeys { key -> state.mergeConflicts.any { it.key == key } }
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

    Scaffold(
        topBar = {
            AppTopBar(title = "معاينة التقرير", onBack = onBack)
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                AppSection(title = "أطباء المناوبة", colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ), titleColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    state.doctors.forEach { Text("• ${it.fullName}") }
                    if (state.doctors.isEmpty()) Text("اختر أطباء المناوبة من الزر أدناه")
                    if (!state.isReadOnly) AppOutlinedButton(
                        onClick = { confirmSend = false; showShiftDoctors = true },
                        enabled = state.shift != null && !state.savingDoctors && !state.sending &&
                            !state.previewingPdf && !state.exportingLocalPdf && !state.sharingPdf &&
                            !state.resolvingConflicts,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.doctors.isEmpty()) "اختيار أطباء المناوبة" else "تعديل أطباء المناوبة") }
                }
                state.summary?.let { summary ->
                    AppSection(title = "ملخص المناوبة") {
                        Text("عدد المرضى: ${uiNumber(summary.patientCount)}")
                        if (summary.psychoCount > 0) {
                            Text("عدد الحالات النفسية: ${uiNumber(summary.psychoCount)}")
                        }
                        Text("عدد المرافقين: ${uiNumber(summary.escortCount)}")
                        Text("تاريخ المناوبة: ${uiNumber(summary.shiftDate)}")
                    }
                }

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
                        state.changeBriefing.forEach { Text("• $it") }
                    }
                }

                AppToggleRow(title = "إرسال كملف PDF", checked = state.reportAsPdf,
                    onCheckedChange = viewModel::setReportAsPdf, enabled = state.summary != null && !busy)

                if (!state.reportAsPdf) {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.previewMarkdown,
                            modifier = Modifier.padding(UiPadding.content),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LoadingButton(
                        text = "معاينة ملف PDF",
                        outlined = true,
                        loading = state.previewingPdf,
                        onClick = viewModel::previewPdf,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.resolvingConflicts && state.summary != null && !state.savingDoctors && !state.sending && !state.exportingLocalPdf && !state.sharingPdf
                    )
                    LoadingButton(
                        text = "حفظ PDF محلياً",
                        outlined = true,
                        loading = state.exportingLocalPdf,
                        onClick = viewModel::exportLocally,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.resolvingConflicts && state.summary != null && !state.savingDoctors && !state.sending && !state.previewingPdf && !state.sharingPdf
                    )
                    LoadingButton(
                        text = "مشاركة PDF",
                        outlined = true,
                        loading = state.sharingPdf,
                        onClick = viewModel::sharePdf,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.resolvingConflicts && state.summary != null && !state.savingDoctors && !state.sending && !state.previewingPdf &&
                            !state.exportingLocalPdf
                    )
                }

                if (state.isAdmin) {
                    AppOutlinedButton(onClick = viewModel::exportAssignments, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && state.shift != null) { Text("حفظ توزيع المرضى بالأسماء فقط (PDF)") }
                    Text("صفحة طولية واحدة: جدول لكل مشرف، أسماء المقيمين في صف واحد ثم أسماء المرضى فقط.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                state.error?.let { err ->
                    AppNotice(err, kind = NoticeKind.ERROR,
                        actionLabel = if (state.retryAction == null) null else "إعادة المحاولة",
                        onAction = if (state.retryAction == null) null else viewModel::retryLastAction,
                        actionEnabled = !state.sending && !state.previewingPdf && !state.exportingLocalPdf && !state.sharingPdf && !state.resolvingConflicts)
                }
                state.lastOperation?.let { AppNotice(it) }
                if (state.isReadOnly) {
                    AppNotice("تقرير مناوبة محفوظة: المعاينة والحفظ المحلي متاحان، أما الإرسال والنشر فمتوقفان.")
                }

                LongPressLoadingButton(text = if (state.sending) "جارٍ إرسال التقرير…" else "إرسال تقرير",
                    loading = state.sending, onClick = { confirmSend = true }, onLongClick = ::selectSupervisorGroups,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && state.summary != null && !state.isReadOnly)
                AppOutlinedButton(onClick = ::selectSupervisorGroups, modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && state.summary != null && !state.isReadOnly) { Text("إرسال إلى مجموعات المشرفين (PDF)") }

            }
        }
    }

    if (state.mergeConflicts.isNotEmpty()) {
        PatientConflictDialog(conflicts = state.mergeConflicts, choices = conflictChoices,
            resolving = state.resolvingConflicts,
            onChoice = { key, choice -> conflictChoices = conflictChoices + (key to choice) },
            onConfirm = { viewModel.resolveMergeConflicts(conflictChoices) }, onDismiss = viewModel::dismissMergeConflicts)
    }

    if (supervisorMenuOpen) {
        AlertDialog(onDismissRequest = { supervisorMenuOpen = false }, title = { Text("مجموعات المشرفين") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (state.supervisorTargets.isEmpty()) EmptyState("لا يوجد مشرفون مرتبطون بمرضى هذه المناوبة")
                state.supervisorTargets.forEach { target ->
                    AppCheckRow(label = "${target.doctorName} (${uiNumber(target.patientCount)} مريض)" +
                        if (target.chatId == null) " — لا توجد مجموعة" else "", checked = target.doctorId in selectedSupervisorIds,
                        enabled = !busy && target.chatId != null, onCheckedChange = { checked ->
                            selectedSupervisorIds = if (checked) selectedSupervisorIds + target.doctorId else selectedSupervisorIds - target.doctorId
                        })
                }
            }
        }, confirmButton = {
            AppButton(enabled = !busy && selectedSupervisorIds.isNotEmpty(), onClick = {
                pendingSupervisorTargets = state.supervisorTargets.filter { it.doctorId in selectedSupervisorIds && it.chatId != null }
                supervisorMenuOpen = false
            }) { Text("مراجعة الإرسال") }
        }, dismissButton = { AppTextButton(onClick = { supervisorMenuOpen = false }) { Text("إلغاء") } })
    }
    pendingSupervisorTargets?.let { targets ->
        val targetsUnchanged = targets.all { it in state.supervisorTargets }
        ReportSendConfirmation(destinations = targets.map { "مجموعة ${it.doctorName} · ${uiNumber(it.patientCount)} مريض" },
            format = "PDF", warningCount = state.readinessWarnings.size,
            enabled = !busy && !state.isReadOnly && targetsUnchanged && targets.isNotEmpty(),
            unavailableReason = if (targetsUnchanged) null else "تغيّرت مجموعات المشرفين؛ أغلق هذه المراجعة واختر الوجهات من جديد.",
            onConfirm = {
                pendingSupervisorTargets = null
                viewModel.sendSupervisorReports(targets.mapTo(linkedSetOf()) { it.doctorId })
            }, onDismiss = { pendingSupervisorTargets = null })
    }

    if (confirmSend) {
        ReportSendConfirmation(destinations = listOf("مجموعة المشروع — تقرير المناوبة الكامل"),
            format = if (state.reportAsPdf) "PDF" else "نصي", warningCount = state.readinessWarnings.size,
            enabled = !busy && !state.isReadOnly && state.summary != null,
            onConfirm = { confirmSend = false; viewModel.send() }, onDismiss = { confirmSend = false })
    }
}
