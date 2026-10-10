package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportReadiness
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfExportOptions
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.components.AppPalettePicker
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import com.hos.rushdpatients.ui.ward.AddPatientDialog
import com.hos.rushdpatients.ui.ward.CopyPatientDialog
import com.hos.rushdpatients.ui.ward.EditPatientDialog
import com.hos.rushdpatients.ui.ward.PatientCard
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.hos.rushdpatients.ui.ward.PatientReturnTarget
import com.hos.rushdpatients.ui.ward.PatientDetailsScreen
import com.hos.rushdpatients.ui.ward.patientRoundsNavigation
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.hos.rushdpatients.ui.ward.ShiftDoctorPicker
import com.hos.rushdpatients.ui.ward.SortSheet

private enum class DemoTab(val label: String) { PATIENTS("المرضى"), DASHBOARD("اللوحة"), TEAM("الفريق"), REPORT("التقرير") }
private enum class DemoFilter(val label: String) { ALL("الكل"), MINE("مرضاي"), PRIORITY("أولوية"), PENDING("مهام معلقة"), OVERDUE("مهام متأخرة") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DemoWardScreen(onExit: () -> Unit, viewModel: DemoWardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(DemoTab.PATIENTS) }
    var filter by rememberSaveable { mutableStateOf(DemoFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var expandedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var animatedId by remember { mutableStateOf<String?>(null) }
    var pinnedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var compact by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Patient?>(null) }
    var returnTarget by remember { mutableStateOf<PatientReturnTarget?>(null) }
    var recentlyEditedId by remember { mutableStateOf<String?>(null) }
    val patientListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var copy by remember { mutableStateOf<Patient?>(null) }
    var deleting by remember { mutableStateOf<Patient?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showDoctors by remember { mutableStateOf(false) }
    var showRecycle by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(AppAppearance.SYSTEM) }
    var accent by rememberSaveable { mutableStateOf(AppThemePreset.SYSTEM) }
    var elegant by rememberSaveable { mutableStateOf(false) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    var pdfColor by rememberSaveable { mutableStateOf(PdfColorPreset.TEAL) }
    val options = PdfExportOptions(orientation = if (landscape) PdfOrientation.LANDSCAPE else PdfOrientation.PORTRAIT,
        colorPreset = pdfColor)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        viewModel.exportPdf(uri, options, elegant)
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    BackHandler(enabled = tab != DemoTab.PATIENTS && detailId == null) { tab = DemoTab.PATIENTS }
    val detailStateHolder = rememberSaveableStateHolder()
    val now = System.currentTimeMillis()
    val actorId = state.doctors.first().id
    val visible = state.ordered.filter { patient ->
        (query.isBlank() || patient.name.contains(query.trim(), true) ||
            patient.initialDiagnosis.contains(query.trim(), true) || patient.admittanceNumber.contains(query.trim())) &&
            when (filter) {
                DemoFilter.ALL -> true
                DemoFilter.MINE -> patient.responsibleResidentId == actorId || patient.responsibleSpecialistId == actorId ||
                    patient.tasks.any { !it.done && it.ownerDoctorId == actorId }
                DemoFilter.PRIORITY -> patient.isPriority
                DemoFilter.PENDING -> patient.tasks.any { !it.done }
                DemoFilter.OVERDUE -> patient.tasks.any { PatientTasks.overdue(it, now) }
            }
    }.sortedByDescending { it.id in pinnedIds }
    LaunchedEffect(returnTarget, state.patients, visible, tab) {
        val target = returnTarget ?: return@LaunchedEffect
        if (tab != DemoTab.PATIENTS) return@LaunchedEffect
        val saved = state.patients.firstOrNull { it.id == target.id } ?: return@LaunchedEffect
        if (saved.revision <= target.previousRevision) return@LaunchedEffect
        val index = visible.indexOfFirst { it.id == target.id }
        if (index < 0) {
            returnTarget = null
            scope.launch { snackbar.showSnackbar("تم حفظ ${saved.name}؛ لا يطابق التصفية الحالية") }
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (patientListState.layoutInfo.visibleItemsInfo.none { it.key == target.id }) {
            patientListState.scrollToItem(index + 2) // Demo notice and search/filter header.
        }
        recentlyEditedId = target.id
        returnTarget = null
    }
    LaunchedEffect(recentlyEditedId) {
        if (recentlyEditedId != null) {
            delay(1800)
            recentlyEditedId = null
        }
    }
    RushdPatientsTheme(preset = accent, appearance = appearance) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("المناوبة التجريبية", style = MaterialTheme.typography.titleLarge)
                        Text("${state.date} · ${state.patients.size} مريض · دون إنترنت", style = MaterialTheme.typography.labelSmall)
                    }
                }, actions = {
                    if (tab == DemoTab.PATIENTS) {
                        IconButton(onClick = { expanded = !expanded; expandedIds = emptyList(); animatedId = null }) {
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "طي جميع البطاقات" else "توسيع جميع البطاقات")
                        }
                        IconButton(onClick = { showSort = true }) { Icon(Icons.Filled.Sort, "ترتيب المرضى") }
                        IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "إضافة مريض تجريبي") }
                    }
                    IconButton(onClick = { viewModel.endSession(); onExit() }, enabled = !state.exporting) { Icon(Icons.Filled.ExitToApp, "إنهاء العرض التجريبي") }
                })
            },
            bottomBar = {
                Surface {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        DemoTab.entries.forEach { destination ->
                            AppTextButton(onClick = { tab = destination }, modifier = Modifier.weight(1f).heightIn(min = UiSpacing.touchTarget),
                                colors = ButtonDefaults.textButtonColors(contentColor = if (tab == destination)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                                Text(destination.label)
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = UiSpacing.medium), state = patientListState,
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small), contentPadding = PaddingValues(vertical = UiSpacing.small)) {
                item {
                    AppNotice("بيانات وهمية للتجربة. التعديلات مؤقتة؛ لا حفظ في قاعدة المرضى ولا رفع. تصدير PDF محلي متاح.")
                }
                when (tab) {
                    DemoTab.PATIENTS -> {
                        item {
                            AppTextField(query, { query = it }, label = { Text("بحث بالاسم أو التشخيص أو رقم القبول") },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                DemoFilter.entries.forEach { value -> FilterChip(selected = filter == value,
                                    onClick = { filter = value }, label = { Text(value.label) }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                AppTextButton(onClick = { compact = !compact }) { Text(if (compact) "بطاقات مريحة" else "بطاقات مدمجة") }
                                AppTextButton(onClick = { showRecycle = true }) { Text("المحذوفات (${state.deleted.size})") }
                            }
                        }
                        if (visible.isEmpty()) item { Text("لا توجد نتائج. غيّر التصفية أو أضف مريضاً تجريبياً.") }
                        items(visible, key = { it.id }) { patient ->
                            PatientCard(patient = patient, expanded = if (expanded) patient.id !in expandedIds else patient.id in expandedIds,
                                twoColumn = false, doctorNames = state.names, compact = compact,
                                animateExpansion = animatedId == patient.id, taskNowEpochMillis = now,
                                recentlyEdited = recentlyEditedId == patient.id,
                                pinned = patient.id in pinnedIds,
                                onPinToggle = { pinnedIds = if (patient.id in pinnedIds) pinnedIds - patient.id else pinnedIds + patient.id },
                                onClick = { edit = patient }, onLongClick = { detailId = patient.id }, onEdit = { edit = patient },
                                onCopy = { copy = patient }, onDelete = { deleting = patient },
                                onPriorityChange = { viewModel.save(patient.copy(isPriority = it)) },
                                onExpandToggle = {
                                    animatedId = patient.id
                                    expandedIds = if (patient.id in expandedIds) expandedIds - patient.id else expandedIds + patient.id
                                })
                        }
                    }
                    DemoTab.DASHBOARD -> {
                        val counts = PatientTasks.counts(state.patients.flatMap { it.tasks }, now)
                        item { Text("لوحة تسليم المناوبة", style = MaterialTheme.typography.titleLarge) }
                        item { DemoMetric("المرضى", state.patients.size) { filter = DemoFilter.ALL; tab = DemoTab.PATIENTS } }
                        item { DemoMetric("المهام المعلقة", counts.pending) { filter = DemoFilter.PENDING; tab = DemoTab.PATIENTS } }
                        item { DemoMetric("المتأخرة — ضمن المعلقة", counts.overdue) { filter = DemoFilter.OVERDUE; tab = DemoTab.PATIENTS } }
                        item { Text("مهام معلقة غير معيّنة: ${counts.unassigned} · شارات: ${state.patients.sumOf { it.badges.size }}") }
                        item { Text("تجربة المظهر — لا تغيّر إعدادات المشروع", style = MaterialTheme.typography.titleMedium) }
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                AppAppearance.entries.forEach { value -> FilterChip(selected = appearance == value,
                                    onClick = { appearance = value }, label = { Text(value.arabicLabel) }) }
                            }
                            AppPalettePicker(selected = accent, onSelected = { accent = it })
                        }
                        item { AppTextButton(onClick = { showReset = true }, enabled = !state.exporting) { Text("إعادة ضبط التجربة") } }
                    }
                    DemoTab.TEAM -> {
                        item { Text("فريق بأسماء الأشجار", style = MaterialTheme.typography.titleLarge) }
                        item { Text("أطباء وهميون بلا حسابات أو رموز دخول. «مرضاي» يعرض مرضى ومهام ${state.doctors.first().fullName}.") }
                        item { AppOutlinedButton(onClick = { showDoctors = true }) { Text("اختيار أطباء المناوبة") } }
                        items(state.doctors, key = { it.id }) { doctor ->
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.fillMaxWidth().padding(UiPadding.content)) {
                                    Text(doctor.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(doctor.clinicalRole.arabicLabel + if (doctor.id in state.shiftDoctorIds) " · ضمن المناوبة" else "")
                                }
                            }
                        }
                    }
                    DemoTab.REPORT -> {
                        item {
                            Text("أطباء المناوبة", style = MaterialTheme.typography.titleLarge)
                            Text(state.shiftDoctors.joinToString("، ") { it.fullName })
                            AppOutlinedButton(onClick = { showDoctors = true }) { Text("تعديل أطباء المناوبة") }
                        }
                        item { Text("${state.date} · ${state.summary.patientCount} مريض · ${state.summary.escortCount} مرافق") }
                        item {
                            Text("خيارات PDF", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                FilterChip(selected = !elegant, onClick = { elegant = false }, label = { Text("جدول كلاسيكي") })
                                FilterChip(selected = elegant, onClick = { elegant = true }, label = { Text("صفوف أنيقة") })
                                FilterChip(selected = landscape, onClick = { landscape = !landscape }, label = { Text("أفقي") })
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                PdfColorPreset.entries.forEach { value -> FilterChip(selected = pdfColor == value,
                                    onClick = { pdfColor = value }, label = { Text(value.arabicLabel) }) }
                            }
                            AppButton(onClick = {
                                try { export.launch("ShiftReport_DEMO_${state.date}.pdf") }
                                catch (e: Exception) { viewModel.message("تعذر فتح مكان الحفظ") }
                            }, enabled = !state.exporting && state.patients.isNotEmpty() && state.shiftDoctorIds.isNotEmpty()) {
                                Text(if (state.exporting) "جارٍ إنشاء PDF…" else "تصدير PDF تجريبي")
                            }
                            Text("الملف يحمل علامة «عرض تجريبي». لا إرسال أو اتصال بتليجرام.", style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            val warnings = ReportReadiness.warnings(state.patients)
                            Text(if (warnings.isEmpty()) "لا توجد ملاحظات اكتمال" else warnings.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        items(state.ordered, key = { "report-${it.id}" }) { patient ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                Column(Modifier.fillMaxWidth().padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                    Text("${patient.sortOrder}. ${patient.name}", style = MaterialTheme.typography.titleMedium)
                                    Text(patient.initialDiagnosis)
                                    Text("الخطة: ${patient.treatmentPlan}")
                                    Text("المتابعة: ${patient.followUp}")
                                    Text("التحاليل: ${patient.labs}")
                                    Text(PatientTasks.summary(patient.tasks, state.names), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showAdd) AddPatientDialog(doctors = state.doctors, onConfirm = {
            if (viewModel.save(it)) showAdd = false
        }, onDismiss = { showAdd = false })
        edit?.let { patient -> EditPatientDialog(patient, state.doctors,
            onConfirm = { updated, stale ->
                if (viewModel.save(updated, stale)) {
                    returnTarget = PatientReturnTarget(updated.id, updated.revision)
                    edit = null
                }
            }, onDismiss = { edit = null }) }
        copy?.let { patient -> CopyPatientDialog(patient, state.doctors,
            onConfirm = { if (viewModel.save(it)) copy = null }, onDismiss = { copy = null }) }
        deleting?.let { patient -> ConfirmDialog("حذف تجريبي", "نقل ${patient.name} إلى المحذوفات؟",
            onConfirm = { viewModel.delete(patient); deleting = null }, onDismiss = { deleting = null }) }
        if (showSort) SortSheet(initial = state.sort, initialRevision = 0,
            onApply = { spec, _, _ -> viewModel.sort(spec); showSort = false }, onDismiss = { showSort = false })
        if (showDoctors) ShiftDoctorPicker(allDoctors = state.doctors, initialSelected = state.shiftDoctorIds, initialRevision = 0,
            onConfirm = { ids, _, _ -> viewModel.selectDoctors(ids); showDoctors = false }, onDismiss = { showDoctors = false })
        if (showReset) ConfirmDialog("إعادة ضبط التجربة", "إلغاء التعديلات المؤقتة واستعادة الأمثلة؟",
            onConfirm = { viewModel.reset(); expandedIds = emptyList(); pinnedIds = emptyList(); detailId = null; showReset = false },
            onDismiss = { showReset = false })
        if (showRecycle) AlertDialog(onDismissRequest = { showRecycle = false }, title = { Text("المحذوفات التجريبية") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (state.deleted.isEmpty()) Text("المحذوفات فارغة")
                    state.deleted.forEach { patient ->
                        AppTextButton(onClick = { viewModel.restore(patient) }) { Text("استعادة ${patient.name}") }
                    }
                }
            }, confirmButton = { AppTextButton(onClick = { showRecycle = false }) { Text("إغلاق") } })
        state.patients.find { it.id == detailId }?.let { patient ->
            if (edit == null && copy == null) detailStateHolder.SaveableStateProvider(patient.id) {
                PatientDetailsScreen(patient = patient, doctorNames = state.names, activity = emptyList(), readOnly = false,
                    onEdit = { edit = patient }, onCopy = { copy = patient },
                    onPriorityChange = { viewModel.save(patient.copy(isPriority = it)) }, onDismiss = { detailId = null },
                    roundsNavigation = patientRoundsNavigation(visible.map { it.id }, patient.id),
                    navigationEnabled = !showAdd,
                    onNavigateToPatient = { id ->
                        if (!showAdd && edit == null && copy == null && visible.any { it.id == id }) detailId = id
                    })
            }
        }
    }
}

@Composable
private fun DemoMetric(label: String, count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget).padding(UiPadding.content), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(count.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
