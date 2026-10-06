package com.hos.rushdpatients.ui.report

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.summary?.let { summary ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "ملخص المناوبة",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "أطباء المناوبة",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            summary.doctors.forEach { doctor ->
                                Text("• ${doctor.fullName}")
                            }
                            Divider()
                            Text("عدد المرضى: ${summary.patientCount}")
                            if (summary.psychoCount > 0) {
                                Text("عدد الحالات النفسية: ${summary.psychoCount}")
                            }
                            Text("عدد المرافقين: ${summary.escortCount}")
                            Text("تاريخ المناوبة: ${summary.shiftDate}")
                        }
                    }
                }

                if (state.readinessWarnings.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "ملاحظات جاهزية التقرير",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
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
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("ما الذي تغيّر منذ آخر نشر؟", style = MaterialTheme.typography.titleSmall)
                        if (state.changeBriefing.isEmpty()) {
                            Text("لا توجد تغييرات محلية غير منشورة")
                        } else {
                            state.changeBriefing.forEach { Text("• $it") }
                        }
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
                        enabled = !state.sending && !state.previewingPdf &&
                            !state.exportingLocalPdf && !state.sharingPdf
                    )
                }

                if (!state.reportAsPdf) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.previewMarkdown,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LoadingButton(
                        text = "معاينة ملف PDF",
                        loading = state.previewingPdf,
                        onClick = viewModel::previewPdf,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.sending && !state.exportingLocalPdf && !state.sharingPdf
                    )
                    LoadingButton(
                        text = "حفظ PDF محلياً",
                        loading = state.exportingLocalPdf,
                        onClick = viewModel::exportLocally,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.sending && !state.previewingPdf && !state.sharingPdf
                    )
                    LoadingButton(
                        text = "مشاركة PDF",
                        loading = state.sharingPdf,
                        onClick = viewModel::sharePdf,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.sending && !state.previewingPdf &&
                            !state.exportingLocalPdf
                    )
                }

                state.error?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(text = err, color = MaterialTheme.colorScheme.onErrorContainer)
                            if (state.retryAction != null) {
                                androidx.compose.material3.TextButton(
                                    onClick = viewModel::retryLastAction
                                ) { Text("إعادة المحاولة") }
                            }
                        }
                    }
                }

                state.lastOperation?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.isReadOnly) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "تقرير مناوبة محفوظة: المعاينة والحفظ المحلي متاحان، أما الإرسال والنشر فمتوقفان.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
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
                        enabled = !state.previewingPdf && !state.exportingLocalPdf &&
                            !state.sharingPdf &&
                            state.shift != null && !state.isReadOnly
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.mergeConflicts.forEach { conflict ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${conflict.patientName} — ${conflict.fieldLabel}")
                                Text("محلي: ${conflict.localValue}")
                                Text("منشور: ${conflict.remoteValue}")
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.resolveMergeConflicts(conflictChoices) },
                    enabled = state.mergeConflicts.all { it.key in conflictChoices } && !state.resolvingConflicts
                ) { Text(if (state.resolvingConflicts) "جارٍ الدمج…" else "دمج ونشر") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::dismissMergeConflicts) { Text("إلغاء") }
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
                androidx.compose.material3.TextButton(onClick = {
                    confirmSend = false
                    viewModel.send()
                }) { Text("إرسال") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmSend = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LongPressLoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val active = enabled && !loading
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .combinedClickable(
                enabled = active,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = contentColor,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(text, color = contentColor)
        }
    }
}
