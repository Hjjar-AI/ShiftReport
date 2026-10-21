package com.hos.rushdpatients.ui.demo

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.*
import com.hos.rushdpatients.ui.ward.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoWardScreen(onExit: () -> Unit, viewModel: DemoWardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pages = rememberSaveableStateHolder()
    val detailStateHolder = rememberSaveableStateHolder()
    val foldingFeature = rememberWardFoldingFeature()
    var tab by rememberSaveable { mutableStateOf(DemoDestination.PATIENTS) }
    var filter by rememberSaveable { mutableStateOf(DashboardFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var expandedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pinnedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var compact by rememberSaveable { mutableStateOf(false) }
    var twoColumn by rememberSaveable { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(AppAppearance.LIGHT) }
    var accent by rememberSaveable { mutableStateOf(AppThemePreset.VANILLA) }
    var font by rememberSaveable { mutableStateOf(AppFontScale.NORMAL) }
    var animatedId by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Patient?>(null) }
    var copy by remember { mutableStateOf<Patient?>(null) }
    var deleting by remember { mutableStateOf<Patient?>(null) }
    var returnTarget by remember { mutableStateOf<PatientReturnTarget?>(null) }
    var recentlyEditedId by remember { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showDoctors by remember { mutableStateOf(false) }
    var showRecycle by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val patientListState = rememberLazyListState()
    val mainDestination = tab == DemoDestination.PATIENTS || tab == DemoDestination.DASHBOARD
    val showAddFloatingAction = mainDestination && detailId == null &&
        (tab != DemoDestination.PATIENTS || state.patients.isNotEmpty())
    val actionSpace = rememberFloatingActionSpace(showAddFloatingAction)
    val baseDensity = LocalDensity.current
    val demoDensity = remember(baseDensity, font) {
        Density(baseDensity.density, (baseDensity.fontScale * font.multiplier).coerceIn(0.85f, 2f))
    }
    fun navigate(destination: DemoDestination) { tab = destination; detailId = null }
    fun closeDrawer(action: () -> Unit) { scope.launch { drawer.close(); action() } }
    fun resetFilters() { query = ""; filter = DashboardFilter.ALL }
    fun expandAll(value: Boolean) { expanded = value; expandedIds = emptyList(); animatedId = null }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        viewModel.exportPreparedPdf(it)
    }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    BackHandler(enabled = !drawer.isOpen && !showAdd && edit == null && copy == null && detailId == null) {
        if (tab != DemoDestination.PATIENTS) tab = DemoDestination.PATIENTS
        else if (!state.exporting) showExit = true
    }
    val visible = state.ordered.filter { patient ->
        ArabicSearchNormalizer.matches(query, patient.name, patient.initialDiagnosis, patient.admittanceNumber,
            patient.treatmentPlan, patient.followUp, patient.labs, patient.tasks.joinToString(" ") { it.description }) &&
            when (filter) {
                DashboardFilter.ALL -> true
                DashboardFilter.MINE -> patient.responsibleResidentId == state.actorId || patient.responsibleSpecialistId == state.actorId ||
                    patient.tasks.any { !it.done && it.ownerDoctorId == state.actorId }
                DashboardFilter.URGENT -> patient.isPriority || patient.badges.any { it.priority == PatientBadgePriority.HIGH }
                DashboardFilter.WARNINGS -> patient.badges.isNotEmpty()
                DashboardFilter.UNASSIGNED -> patient.responsibleResidentId == null || patient.responsibleSpecialistId == null
                DashboardFilter.NEW_ADMISSIONS -> patient.admittanceDays == 0
                DashboardFilter.TASK_PENDING -> patient.tasks.any { !it.done }
                DashboardFilter.TASK_OVERDUE -> patient.tasks.any { PatientTasks.overdue(it, now) }
                DashboardFilter.TASK_UNASSIGNED -> patient.tasks.any { !it.done && it.ownerDoctorId == null }
            }
    }.sortedByDescending { it.id in pinnedIds }
    LaunchedEffect(returnTarget, state.patients, visible, tab) {
        val target = returnTarget ?: return@LaunchedEffect
        if (tab != DemoDestination.PATIENTS) return@LaunchedEffect
        val saved = state.patients.firstOrNull { it.id == target.id } ?: return@LaunchedEffect
        if (saved.revision <= target.previousRevision) return@LaunchedEffect
        val index = visible.indexOfFirst { it.id == target.id }
        if (index < 0) {
            returnTarget = null
            scope.launch { snackbar.showSnackbar("حُفظ في التجربة؛ لا يطابق التصفية الحالية") }
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (patientListState.layoutInfo.visibleItemsInfo.none { it.key == target.id }) patientListState.scrollToItem(index + 1)
        recentlyEditedId = target.id
        returnTarget = null
    }
    LaunchedEffect(recentlyEditedId) {
        if (recentlyEditedId != null) { delay(1800); recentlyEditedId = null }
    }
    LaunchedEffect(state.patients.map { it.id }) {
        val ids = state.patients.map { it.id }.toSet()
        expandedIds = expandedIds.filter { it in ids }; pinnedIds = pinnedIds.filter { it in ids }
        if (detailId != null && detailId !in ids) detailId = null
    }
    RushdPatientsTheme(preset = accent, appearance = appearance) {
        CompositionLocalProvider(LocalDensity provides demoDensity) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= UiLayout.splitMinWidth
                ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                    DemoDrawer(state, tab, onNavigate = { destination -> closeDrawer { navigate(destination) } },
                        onRecycle = { closeDrawer { showRecycle = true } },
                        onReset = { closeDrawer { showReset = true } }, onExit = { closeDrawer { showExit = true } })
                }) {
                    Scaffold(topBar = {
                        Column {
                            AppCompactTopBar(title = {
                                Text(if (mainDestination) "مشفى تجريبي · تقرير المناوبة" else tab.title,
                                    style = MaterialTheme.typography.titleSmall)
                                Text("${state.actor.fullName} · ${state.date}", style = MaterialTheme.typography.labelSmall)
                            }, navigation = {
                                IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "فتح القائمة") }
                            }, actions = {
                                if (!mainDestination) IconButton(onClick = { navigate(DemoDestination.PATIENTS) }) {
                                    Icon(Icons.Filled.Close, "العودة إلى المرضى")
                                }
                                if (tab == DemoDestination.PATIENTS) {
                                    IconButton(onClick = { expandAll(!expanded) }) {
                                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            if (expanded) "طي جميع البطاقات" else "توسيع جميع البطاقات")
                                    }
                                }
                            })
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("عرض تجريبي · التعديلات مؤقتة · لا اتصال بتليجرام",
                                    Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen, vertical = UiSpacing.tiny),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }, bottomBar = {
                        if (!wide && mainDestination) PrimaryNavigationBar(
                            viewMode = if (tab == DemoDestination.DASHBOARD) WardViewMode.DASHBOARD else WardViewMode.ALL,
                            isAdmin = false, onPatients = { navigate(DemoDestination.PATIENTS) },
                            onDashboard = { navigate(DemoDestination.DASHBOARD) }, onActivity = {})
                    }, floatingActionButton = {
                        if (showAddFloatingAction) AppButton(onClick = { showAdd = true },
                            enabled = !showAdd && edit == null && copy == null,
                            contentPadding = UiPadding.content,
                            modifier = actionSpace.measure.heightIn(min = UiSpacing.touchTarget)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
                            Text("إضافة مريض", Modifier.padding(start = UiSpacing.small))
                        }
                    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                        Row(Modifier.fillMaxSize().padding(padding)) {
                            if (wide && mainDestination) PrimaryNavigationRail(
                                viewMode = if (tab == DemoDestination.DASHBOARD) WardViewMode.DASHBOARD else WardViewMode.ALL,
                                isAdmin = false, onPatients = { navigate(DemoDestination.PATIENTS) },
                                onDashboard = { navigate(DemoDestination.DASHBOARD) }, onActivity = {})
                            WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                                listFraction = .42f, onListFractionChange = {}, modifier = Modifier.weight(1f).fillMaxHeight(),
                                primary = {
                                    pages.SaveableStateProvider(tab.name) {
                                        val contentModifier = Modifier.padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small)
                                        when (tab) {
                                            DemoDestination.PATIENTS -> LazyColumn(Modifier.fillMaxSize().then(contentModifier),
                                                state = patientListState, verticalArrangement = Arrangement.spacedBy(UiPatientCard.listGap),
                                                contentPadding = PaddingValues(bottom = actionSpace.bottomPadding)) {
                                                item {
                                                    WardPatientListControls(visible.size, state.patients.size, null,
                                                        buildList { if (filter != DashboardFilter.ALL) add(filter.demoLabel()); if (query.isNotBlank()) add("بحث: $query") },
                                                        filter, onPreset = { filter = it }, onOptions = { showOptions = true },
                                                        onReset = ::resetFilters, showSearch = true, query = query,
                                                        onQueryChange = { query = it }, requestFocus = false, onFocusRequested = {})
                                                }
                                                if (visible.isEmpty()) item {
                                                    EmptyState(if (state.patients.isEmpty()) "لا يوجد مرضى" else "لا توجد نتائج مطابقة",
                                                        actionLabel = if (state.patients.isEmpty()) "إضافة مريض" else "مسح التصفية",
                                                        onAction = { if (state.patients.isEmpty()) showAdd = true else resetFilters() })
                                                }
                                                items(visible, key = { it.id }) { patient ->
                                                    PatientCard(patient, expanded = if (expanded) patient.id !in expandedIds else patient.id in expandedIds,
                                                        twoColumn = twoColumn, doctorNames = state.names, compact = compact,
                                                        animateExpansion = animatedId == patient.id, taskNowEpochMillis = now,
                                                        recentlyEdited = recentlyEditedId == patient.id, pinned = patient.id in pinnedIds,
                                                        onPinToggle = { pinnedIds = if (patient.id in pinnedIds) pinnedIds - patient.id else pinnedIds + patient.id },
                                                        onClick = { edit = patient }, onLongClick = { detailId = patient.id }, onEdit = { edit = patient },
                                                        onCopy = { copy = patient }, onDelete = { deleting = patient },
                                                        onPriorityChange = { viewModel.save(patient.copy(isPriority = it)) },
                                                        onExpandToggle = { animatedId = patient.id; expandedIds = if (patient.id in expandedIds) expandedIds - patient.id else expandedIds + patient.id })
                                                }
                                            }
                                            DemoDestination.DASHBOARD -> HandoverDashboard(false, state.patients, state.actorId,
                                                PatientSyncStatus.LOCAL, online = false, freshnessLabel = "جلسة تجريبية مؤقتة",
                                                nowEpochMillis = now, onFilter = { filter = it; query = ""; navigate(DemoDestination.PATIENTS) },
                                                onSync = {}, onActivity = {}, modifier = contentModifier,
                                                bottomClearance = actionSpace.bottomPadding, showSynchronization = false)
                                            DemoDestination.TEAM -> DemoTeamContent(state, { showDoctors = true }, viewModel::selectActor, contentModifier)
                                            DemoDestination.SETTINGS -> DemoPreferencesContent(appearance, { appearance = it }, accent, { accent = it },
                                                font, { font = it }, expanded, twoColumn, compact, state.saveAndNext, viewModel::setSaveAndNext, contentModifier)
                                            DemoDestination.REPORT -> DemoReportContent(state, { showDoctors = true }, onExport = { options, style ->
                                                viewModel.preparePdfExport(options, style == PdfStyle.CARDS)
                                                try { export.launch("ShiftReport_DEMO_${state.date}.pdf") }
                                                catch (e: CancellationException) { throw e }
                                                catch (e: Exception) { viewModel.exportPreparedPdf(null); viewModel.message("تعذر فتح مكان الحفظ") }
                                            }, modifier = contentModifier)
                                        }
                                    }
                                })
                        }
                    }
                }
            }
            if (showOptions) pages.SaveableStateProvider("view-options") {
                DemoViewOptions(filter, { filter = it }, expanded, ::expandAll, compact, { compact = it },
                    twoColumn, { twoColumn = it }, state.sort, viewModel::sort, ::resetFilters, { showOptions = false })
            }
            if (showExit) ConfirmDialog("إنهاء التجربة", "ستُحذف التغييرات المؤقتة وتعود إلى صفحة البدء.",
                confirmText = "إنهاء التجربة", onConfirm = { showExit = false; viewModel.endSession(); onExit() },
                onDismiss = { showExit = false })
            if (showAdd) AddPatientDialog(doctors = state.doctors, onConfirm = {
                if (viewModel.save(it)) showAdd = false
            }, onDismiss = { showAdd = false })
            edit?.let { patient ->
                fun saveEdited(updated: Patient, stale: (Patient?) -> Unit, advance: Boolean) {
                    val nextId = if (advance) patientRoundsNavigation(visible.map { it.id }, patient.id)?.nextId else null
                    if (viewModel.save(updated, stale)) {
                        val next = nextId?.let { id -> viewModel.state.value.patients.firstOrNull { it.id == id } }
                        if (next != null) {
                            returnTarget = null
                            edit = next
                            if (detailId != null) detailId = next.id
                        } else {
                            returnTarget = PatientReturnTarget(updated.id, updated.revision)
                            edit = null
                        }
                    }
                }
                key(patient.id) {
                    EditPatientDialog(patient, state.doctors,
                        onConfirm = { updated, stale -> saveEdited(updated, stale, false) },
                        onConfirmAndNext = if (state.saveAndNext &&
                            patientRoundsNavigation(visible.map { it.id }, patient.id)?.nextId != null)
                            { updated, stale -> saveEdited(updated, stale, true) } else null,
                        onDismiss = { edit = null })
                }
            }
            copy?.let { patient -> CopyPatientDialog(patient, state.doctors,
                onConfirm = { if (viewModel.save(it)) copy = null }, onDismiss = { copy = null }) }
            deleting?.let { patient -> ConfirmDialog("نقل إلى السلة", "سيُنقل ${patient.name} إلى سلة المحذوفات التجريبية.",
                confirmText = "نقل إلى السلة", destructive = true,
                onConfirm = { viewModel.delete(patient); deleting = null }, onDismiss = { deleting = null }) }
            if (showDoctors) ShiftDoctorPicker(allDoctors = state.doctors, initialSelected = state.shiftDoctorIds, initialRevision = 0,
                onConfirm = { ids, _, _ -> viewModel.selectDoctors(ids); showDoctors = false }, onDismiss = { showDoctors = false })
            if (showReset) ConfirmDialog("إعادة ضبط التجربة", "ستُستعاد أمثلة المرضى والأطباء وتُحذف التعديلات على البيانات.",
                confirmText = "إعادة الضبط", destructive = true,
                onConfirm = { viewModel.reset(); expandedIds = emptyList(); pinnedIds = emptyList(); detailId = null
                    query = ""; filter = DashboardFilter.ALL; tab = DemoDestination.PATIENTS; showReset = false },
                onDismiss = { showReset = false })
            if (showRecycle) AlertDialog(onDismissRequest = { showRecycle = false }, title = { Text("المحذوفات التجريبية") },
                text = {
                    Column(Modifier.appDialogBody().verticalScroll(rememberScrollState())) {
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
}
