package com.hos.rushdpatients.ui.report

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
            TopAppBar(
                title = { Text("معاينة التقرير") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع",
                            modifier = Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 180f else 0f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
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
                CircularProgressIndicator()
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
                        Text("عدد المرضى: ${summary.patientCount}")
                        if (summary.psychoCount > 0) {
                            Text("عدد الحالات النفسية: ${summary.psychoCount}")
                        }
                        Text("عدد المرافقين: ${summary.escortCount}")
                        Text("تاريخ المناوبة: ${summary.shiftDate}")
                    }
                }

                if (state.readinessWarnings.isNotEmpty()) {
                    AppSection(
                        title = "ملاحظات جاهزية التقرير",
                        titleColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        state.readinessWarnings.forEach { warning ->
                            Text("• $warning", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Text(
                            "يمكن المتابعة رغم هذه الملاحظات.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إرسال كملف PDF")
                    Switch(
                        checked = state.reportAsPdf,
                        onCheckedChange = viewModel::setReportAsPdf,
                        enabled = state.summary != null && !state.savingDoctors && !state.sending && !state.previewingPdf &&
                            !state.exportingLocalPdf && !state.sharingPdf
                    )
                }

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
                        loading = state.previewingPdf,
                        onClick = viewModel::previewPdf,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.summary != null && !state.savingDoctors && !state.sending && !state.exportingLocalPdf && !state.sharingPdf
                    )
                    LoadingButton(
                        text = "حفظ PDF محلياً",
                        loading = state.exportingLocalPdf,
                        onClick = viewModel::exportLocally,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.summary != null && !state.savingDoctors && !state.sending && !state.previewingPdf && !state.sharingPdf
                    )
                    LoadingButton(
                        text = "مشاركة PDF",
                        loading = state.sharingPdf,
                        onClick = viewModel::sharePdf,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.summary != null && !state.savingDoctors && !state.sending && !state.previewingPdf &&
                            !state.exportingLocalPdf
                    )
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

                Box(modifier = Modifier.fillMaxWidth()) {
                    LongPressLoadingButton(
                        text = if (state.sending) "جارٍ إرسال التقرير…" else "إرسال إلى تلجرام",
                        loading = state.sending,
                        onClick = { confirmSend = true },
                        onLongClick = {
                            selectedSupervisorIds = state.supervisorTargets
                                .filter { it.chatId != null }
                                .mapTo(linkedSetOf()) { it.doctorId }
                            supervisorMenuOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.savingDoctors && !state.previewingPdf && !state.exportingLocalPdf &&
                            !state.sharingPdf &&
                            state.summary != null && !state.isReadOnly
                    )
                    DropdownMenu(
                        expanded = supervisorMenuOpen,
                        onDismissRequest = { supervisorMenuOpen = false }
                    ) {
                        if (state.supervisorTargets.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("لا يوجد مشرفون مرتبطون بمرضى هذه المناوبة") },
                                onClick = {},
                                enabled = false
                            )
                        }
                        state.supervisorTargets.forEach { target ->
                            val configured = target.chatId != null
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${target.doctorName} (${target.patientCount})" +
                                            if (configured) "" else " — لا توجد مجموعة"
                                    )
                                },
                                leadingIcon = {
                                    Checkbox(
                                        checked = target.doctorId in selectedSupervisorIds,
                                        onCheckedChange = null,
                                        enabled = configured
                                    )
                                },
                                enabled = configured,
                                onClick = {
                                    selectedSupervisorIds = if (
                                        target.doctorId in selectedSupervisorIds
                                    ) {
                                        selectedSupervisorIds - target.doctorId
                                    } else {
                                        selectedSupervisorIds + target.doctorId
                                    }
                                }
                            )
                        }
                        Divider()
                        DropdownMenuItem(
                            text = { Text("إرسال PDF إلى المجموعات المحددة") },
                            enabled = selectedSupervisorIds.isNotEmpty() && !state.isReadOnly,
                            onClick = {
                                supervisorMenuOpen = false
                                viewModel.sendSupervisorReports(selectedSupervisorIds)
                            }
                        )
                    }
                }
                Text(
                    "اضغط مطولاً لاختيار مجموعات المشرفين (PDF فقط)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (state.mergeConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMergeConflicts,
            title = { Text("حل تعارضات المرضى قبل الإرسال") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
                ) {
                    state.mergeConflicts.forEach { conflict ->
                        AppCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                Text("${conflict.patientName} — ${conflict.fieldLabel}")
                                Text("محلي: ${conflict.localValue}")
                                Text("منشور: ${conflict.remoteValue}")
                                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                    androidx.compose.material3.FilterChip(
                                        selected = conflictChoices[conflict.key] == ConflictChoice.LOCAL,
                                        onClick = { conflictChoices = conflictChoices + (conflict.key to ConflictChoice.LOCAL) },
                                        label = { Text("المحلي") }
                                    )
                                    androidx.compose.material3.FilterChip(
                                        selected = conflictChoices[conflict.key] == ConflictChoice.REMOTE,
                                        onClick = { conflictChoices = conflictChoices + (conflict.key to ConflictChoice.REMOTE) },
                                        label = { Text("المنشور") }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    onClick = { viewModel.resolveMergeConflicts(conflictChoices) },
                    enabled = state.mergeConflicts.all { it.key in conflictChoices } && !state.resolvingConflicts
                ) { Text(if (state.resolvingConflicts) "جارٍ الدمج…" else "دمج ونشر") }
            },
            dismissButton = {
                AppTextButton(onClick = viewModel::dismissMergeConflicts) { Text("إلغاء") }
            }
        )
    }

    if (confirmSend) {
        AlertDialog(
            onDismissRequest = { confirmSend = false },
            title = { Text("إرسال التقرير") },
            text = {
                Text(
                    "سيتم دمج ونشر أحدث CSV أولاً، ثم إرسال التقرير من النسخة المقبولة. " +
                        if (state.readinessWarnings.isEmpty()) "التقرير جاهز." else
                            "توجد ${state.readinessWarnings.size} ملاحظات جاهزية غير مانعة."
                )
            },
            confirmButton = {
                AppTextButton(onClick = {
                    confirmSend = false
                    viewModel.send()
                }) { Text("إرسال") }
            },
            dismissButton = {
                AppTextButton(onClick = { confirmSend = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
