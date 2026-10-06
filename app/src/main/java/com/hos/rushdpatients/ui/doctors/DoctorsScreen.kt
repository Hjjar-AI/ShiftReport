package com.hos.rushdpatients.ui.doctors

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var showMergeReview by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Doctor?>(null) }
    var deleteTarget by remember { mutableStateOf<Doctor?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DoctorListFilter.ALL) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(viewModel::exportDoctors) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::prepareDoctorImport) }
    val visibleDoctors = state.doctors.filter { doctor ->
        val matchesQuery = query.isBlank() ||
            doctor.fullName.contains(query.trim(), ignoreCase = true) ||
            doctor.telegramId?.toString()?.contains(query.trim()) == true
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
            TopAppBar(
                title = { Text("سجل الأطباء") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.saving && !state.importing && !state.exporting
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "مزامنة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
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
                state.loading || state.importing || state.exporting -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.mergeConflicts.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("تعارضات سجل الأطباء: ${state.mergeConflicts.size}")
                                    Text("لم تُستبدل التعديلات المحلية. راجع القيم المتعارضة قبل المزامنة.")
                                    TextButton(onClick = { showMergeReview = true }, enabled = !state.saving) {
                                        Text("مراجعة التعارضات")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "نقل سجل الأطباء",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "الاستيراد يقرأ ملفاً من الجهاز، والتصدير يحفظ نسخة قابلة لإعادة الاستخدام.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                                        enabled = !state.saving && !state.importing && !state.exporting,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null)
                                        Text("استيراد CSV", Modifier.padding(start = 6.dp))
                                    }
                                    OutlinedButton(
                                        onClick = { exportLauncher.launch(DoctorCsvCodec.FILE_NAME) },
                                        enabled = !state.saving && !state.importing && !state.exporting,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = null)
                                        Text("تصدير CSV", Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("البحث بالاسم أو معرف تليجرام") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DoctorListFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label) }
                                )
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
                                }
                            )
                        }
                    }
                    items(visibleDoctors, key = { it.id }) { doctor ->
                        DoctorCard(
                            doctor = doctor,
                            onEdit = { editTarget = doctor },
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الأطباء الفعّالون: ${preview.activeCount}")
                    Text("المديرون: ${preview.adminCount}")
                    Text("السجلات المحذوفة: ${preview.deletedCount}")
                    if (preview.newWithoutPinCount > 0) {
                        Text(
                            "${preview.newWithoutPinCount} أطباء جدد سيختار كل منهم رقماً سرياً بعد أول تحقق عبر تليجرام. لا تُصدّر أرقام PIN أو مشتقاتها إلى CSV.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        "يُدمج الملف مع السجل المحلي حسب المعرّف أو تليجرام أو الاسم. لا يحذف الاستيراد طبيباً محلياً فعّالاً، ويحمي المدير الحالي والمديرين الدائمين.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDoctorImport) { Text("استيراد ومزامنة") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDoctorImport) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun DoctorCard(
    doctor: Doctor,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                Text("صلاحية التطبيق: $accessText", style = MaterialTheme.typography.bodySmall)
                doctor.telegramId?.let {
                    Text("تليجرام: $it", style = MaterialTheme.typography.bodySmall)
                }
                doctor.customTitle?.let {
                    Text("اللقب: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف")
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
