package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.rememberFloatingActionSpace
import com.hos.rushdpatients.ui.components.AppFieldPair
import androidx.compose.foundation.layout.PaddingValues
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.components.AppLoadingState
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.doctor.DoctorCsvCodec
import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DoctorsScreen(
    onBack: () -> Unit,
    viewModel: DoctorsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf(false) }
    var showTeams by remember { mutableStateOf(false) }
    var transferExpanded by rememberSaveable { mutableStateOf(false) }
    var showMergeReview by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Doctor?>(null) }
    var deleteTarget by remember { mutableStateOf<Doctor?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(DoctorListFilter.ALL) }
    val busy = state.loading || state.saving || state.importing || state.exporting
    val actionSpace = rememberFloatingActionSpace(visible = !busy)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> viewModel.exportDoctors(uri) }
    LaunchedEffect(state.exportReady) {
        if (state.exportReady) {
            viewModel.doctorExportPickerLaunched()
            try {
                exportLauncher.launch(DoctorCsvCodec.FILE_NAME)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.doctorExportPickerFailed()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::prepareDoctorImport) }
    val visibleDoctors = state.doctors.filter { doctor ->
        val matchesQuery = ArabicSearchNormalizer.matches(query, doctor.fullName, doctor.telegramId?.toString().orEmpty())
        val matchesRole = when (filter) {
            DoctorListFilter.ALL -> true
            DoctorListFilter.RESIDENTS -> doctor.clinicalRole.canBeResident()
            DoctorListFilter.SUPERVISORS -> doctor.clinicalRole.canBeSupervisor()
            DoctorListFilter.ADMINS -> doctor.isAdmin
        }
        matchesQuery && matchesRole
    }.sortedWith(
        compareByDescending<Doctor> { it.isAdmin }
            .thenByDescending { it.clinicalRole.canBeSupervisor() }
            .thenBy { it.fullName }
    )

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    if (showTeams) TeamManagementDialog(doctors = state.doctors, busy = busy,
        onDismiss = { showTeams = false },
        onSave = { expected, assignments -> viewModel.saveTeams(expected, assignments) { showTeams = false } })

    if (showMergeReview && state.mergeConflicts.isNotEmpty()) {
        DoctorConflictReviewDialog(
            conflicts = state.mergeConflicts,
            saving = state.saving,
            onConfirm = viewModel::resolveRegistryConflicts,
            onDismiss = { showMergeReview = false }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "سجل الأطباء", onBack = onBack, actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "مزامنة سجل الأطباء")
                    }
                })
        },
        floatingActionButton = {
            if (!busy) ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                modifier = actionSpace.measure,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("إضافة طبيب") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading && state.doctors.isEmpty() -> AppLoadingState("جار تحميل سجل الأطباء…", Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = actionSpace.bottomPadding),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = UiSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    if (state.loading || state.importing || state.exporting) item {
                        AppLoadingState(when {
                            state.importing -> "جار استيراد سجل الأطباء…"
                            state.exporting -> "جار تصدير سجل الأطباء…"
                            else -> "جار تحديث سجل الأطباء…"
                        })
                    }
                    if (state.mergeConflicts.isNotEmpty()) {
                        item {
                            AppNotice("تعارضات سجل الأطباء: ${uiNumber(state.mergeConflicts.size)}\n" +
                                "لم تُستبدل التعديلات المحلية. راجع القيم المتعارضة قبل المزامنة.",
                                kind = NoticeKind.ERROR, actionLabel = "مراجعة التعارضات",
                                onAction = { showMergeReview = true }, actionEnabled = !state.saving)
                        }
                    }
                    item {
                        AppOutlinedButton(onClick = { showTeams = true }, enabled = !busy) { Text("إدارة الفرق معاً") }
                    }
                    item {
                        AppSearchField(query = query, onQueryChange = { query = it }, label = "البحث بالاسم أو معرف تليجرام")
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                            DoctorListFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(UiPadding.content),
                                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                            ) {
                                AppTextButton(
                                    onClick = { transferExpanded = !transferExpanded },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
                                        .semantics { stateDescription = if (transferExpanded) "موسّعة" else "مطوية" }
                                ) {
                                    Text("نقل سجل الأطباء", modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Icon(if (transferExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null)
                                }
                                if (transferExpanded) {
                                    Text(
                                        "الاستيراد يقرأ ملفاً من الجهاز، والتصدير يحفظ نسخة قابلة لإعادة الاستخدام.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AppFieldPair(
                                        first = { fieldModifier ->
                                            AppOutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) },
                                                enabled = !busy, modifier = fieldModifier) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                                Text("استيراد CSV", Modifier.padding(start = UiSpacing.small))
                                            }
                                        },
                                        second = { fieldModifier ->
                                            AppOutlinedButton(onClick = viewModel::prepareDoctorExport,
                                                enabled = !busy, modifier = fieldModifier) {
                                                Icon(Icons.Default.Save, contentDescription = null)
                                                Text("تصدير CSV", Modifier.padding(start = UiSpacing.small))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (visibleDoctors.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (state.doctors.isEmpty()) "لا يوجد أطباء" else "لا توجد نتائج",
                                subtitle = if (state.doctors.isEmpty()) {
                                    "استورد سجل CSV أو أضف طبيباً جديداً"
                                } else {
                                    "غيّر البحث أو مرشح الدور"
                                },
                                actionLabel = if (state.doctors.isNotEmpty()) "مسح البحث والتصفية" else null,
                                onAction = { query = ""; filter = DoctorListFilter.ALL }
                            )
                        }
                    }
                    items(visibleDoctors, key = { it.id }) { doctor ->
                        DoctorCard(
                            doctor = doctor,
                            teamSupervisorName = state.doctors.firstOrNull { it.id == doctor.teamSupervisorId }?.fullName,
                            onEdit = { editTarget = doctor },
                            enabled = !busy,
                            onDelete = { deleteTarget = doctor }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddEditDoctorDialog(
            existing = null,
            saving = state.saving,
            doctors = state.doctors,
            onConfirm = { _, input, _ ->
                viewModel.addDoctor(input) { showAdd = false }
            },
            onDismiss = { showAdd = false }
        )
    }

    editTarget?.let { d ->
        AddEditDoctorDialog(
            existing = d,
            saving = state.saving,
            doctors = state.doctors,
            onConfirm = { expected, input, onStale ->
                if (expected != null) viewModel.editDoctor(expected, input, onStale) { editTarget = null }
            },
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { d ->
        ConfirmDialog(
            title = "حذف طبيب",
            message = "هل أنت متأكد من حذف ${d.fullName}؟",
            onConfirm = {
                viewModel.deleteDoctor(d) { deleteTarget = null }
            },
            onDismiss = { deleteTarget = null }
        )
    }

    state.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDoctorImport,
            title = { Text("مراجعة استيراد سجل الأطباء") },
            text = {
                Column(modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .65f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text("الأطباء الفعّالون: ${preview.activeCount}")
                    listOf("إضافات" to preview.added, "تعديلات" to preview.changed,
                        "صفوف مستوردة دون تغيير" to preview.unchanged,
                        "سجلات غائبة عن الملف محفوظة كما هي" to preview.retained).forEach { (label, names) ->
                        Text("$label (${names.size}): " + names.joinToString("، ").ifBlank { "لا يوجد" })
                    }
                    Text("المديرون: ${preview.adminCount}")
                    Text("السجلات المحذوفة: ${preview.deletedCount}")
                    if (preview.newWithoutPinCount > 0) {
                        Text(
                            "${preview.newWithoutPinCount} أطباء جدد سيختار كل منهم رقماً سرياً بعد أول تحقق عبر تليجرام. لا تُصدّر أرقام PIN أو مشتقاتها إلى CSV.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        "الاستيراد يضيف ويحدّث حسب المعرّف أو تليجرام أو الاسم ويحفظ الغائبين. بعد الموافقة يُحفظ محلياً ويُنشر السجل الكامل على تليجرام. يبقى المدير الحالي والمديرون الدائمون محميين.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                AppTextButton(onClick = viewModel::confirmDoctorImport) { Text("حفظ الاستيراد ونشر السجل الكامل") }
            },
            dismissButton = {
                AppTextButton(onClick = viewModel::dismissDoctorImport) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun DoctorCard(
    doctor: Doctor,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true,
    teamSupervisorName: String? = null
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiPadding.content),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(doctor.fullName, style = MaterialTheme.typography.bodyLarge)
                val accessText = when {
                    doctor.isPermanentAdmin -> "مدير دائم (رتبة ${doctor.rank})"
                    doctor.isAdmin -> "مدير (رتبة ${doctor.rank})"
                    else -> "عضو طبي"
                }
                Text(
                    doctor.clinicalRole.arabicLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (doctor.clinicalRole == com.hos.rushdpatients.data.model.ClinicalRole.RESIDENT) {
                    Text("مشرف الفريق: " + (teamSupervisorName ?: "دون فريق"), style = MaterialTheme.typography.bodySmall)
                }
                Text("صلاحية التطبيق: $accessText", style = MaterialTheme.typography.bodySmall)
                doctor.telegramId?.let {
                    Text("تليجرام: $it", style = MaterialTheme.typography.bodySmall)
                }
                doctor.customTitle?.let {
                    Text("اللقب: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل ${doctor.fullName}")
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "حذف ${doctor.fullName}")
            }
        }
    }
}

private enum class DoctorListFilter(val label: String) {
    ALL("الكل"),
    RESIDENTS("المقيمون"),
    SUPERVISORS("المشرفون"),
    ADMINS("المديرون")
}
