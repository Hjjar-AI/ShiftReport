package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.domain.report.ReportReadiness
import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.sync.ConflictChoice
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WardScreen(
    isAdmin: Boolean,
    currentDoctorId: String,
    onOpenReport: (String) -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenVbaImport: () -> Unit,
    viewModel: WardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val foldingFeature = rememberWardFoldingFeature()
    val detailStateHolder = rememberSaveableStateHolder()
    val activityScroll = rememberLazyListState()
    val publicationScroll = rememberLazyListState()
    var listFraction by rememberSaveable { mutableStateOf(.42f) }
    var showSupportingSheet by rememberSaveable { mutableStateOf(false) }
    val expandedWindow = LocalConfiguration.current.screenWidthDp >= 600
    val topBarHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val compactNavigationHeight = UiSpacing.touchTarget * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val bottomBarHeight = compactNavigationHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val freshnessLabel = remember(state.lastBackedUpAt, state.syncStatus) {
        val lastBackup = state.lastBackedUpAt?.let { epochMillis ->
            val time = DateTimeFormatter.ofPattern("HH:mm")
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
            "آخر نسخة $time"
        }
        when (state.syncStatus) {
            PatientSyncStatus.BACKED_UP -> lastBackup ?: state.syncStatus.arabicLabel
            PatientSyncStatus.PENDING -> listOfNotNull(state.syncStatus.arabicLabel, lastBackup)
                .joinToString(" · ")
            else -> state.syncStatus.arabicLabel
        }
    }
    val doctorNames = remember(state.doctors) {
        state.doctors.associate { it.id to it.fullName }
    }
    val patientGridState = rememberLazyGridState()
    val reportReadinessIssueCount = remember(state.patients, state.mergeConflicts) {
        ReportReadiness.warnings(state.patients).size + state.mergeConflicts.size
    }

    var showAdd by remember { mutableStateOf(false) }
    var detailsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<Patient?>(null) }
    var returnTarget by remember { mutableStateOf<PatientReturnTarget?>(null) }
    var recentlyEditedId by remember { mutableStateOf<String?>(null) }
    var copyTarget by remember { mutableStateOf<Patient?>(null) }
    var deleteTarget by remember { mutableStateOf<Patient?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showGroupBy by remember { mutableStateOf(false) }
    var showShiftDoctors by remember { mutableStateOf(false) }
    var confirmLatest by remember { mutableStateOf(false) }
    var confirmPrevious by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showRecycleBin by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(WardViewMode.ALL) }
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    var priorityOnly by remember { mutableStateOf(false) }
    var warningsOnly by remember { mutableStateOf(false) }
    var unassignedOnly by remember { mutableStateOf(false) }
    var taskFilter by remember { mutableStateOf<DashboardFilter?>(null) }
    var urgentOnly by remember { mutableStateOf(false) }
    var newAdmissionsOnly by remember { mutableStateOf(false) }
    var rolloverDecisions by remember { mutableStateOf(emptyMap<String, RolloverDecision>()) }
    var showReportSheet by rememberSaveable { mutableStateOf(false) }
    var expandedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var collapsedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val anyCardsExpanded = state.patients.any { patient ->
        if (state.patientDetailsExpanded) patient.id !in collapsedPatientIds else patient.id in expandedPatientIds
    }
    var animatedPatientId by remember { mutableStateOf<String?>(null) }
    fun toggleAllCards() {
        animatedPatientId = null
        val expand = !anyCardsExpanded
        val ids = state.patients.map { it.id }
        // Both override sets express the target immediately, even before preference IO completes.
        expandedPatientIds = if (expand) ids else emptyList()
        collapsedPatientIds = if (expand) emptyList() else ids
        viewModel.setPatientDetailsExpanded(expand)
    }
    fun toggleCard(id: String) {
        animatedPatientId = id
        if (state.patientDetailsExpanded) {
            collapsedPatientIds = if (id in collapsedPatientIds) collapsedPatientIds - id else collapsedPatientIds + id
        } else {
            expandedPatientIds = if (id in expandedPatientIds) expandedPatientIds - id else expandedPatientIds + id
        }
    }
    var pinnedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var conflictChoices by remember { mutableStateOf(emptyMap<String, ConflictChoice>()) }
    var activityQuery by rememberSaveable { mutableStateOf("") }
    var activityFilter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }

    val visiblePatients = remember(
        state.patients, searchQuery, viewMode, currentDoctorId,
        priorityOnly, warningsOnly, unassignedOnly, urgentOnly, newAdmissionsOnly, taskFilter, state.taskNowEpochMillis,
        pinnedPatientIds
    ) {
        state.patients.filter { patient ->
            val matchesOwner = viewMode != WardViewMode.MINE ||
                patient.responsibleResidentId == currentDoctorId ||
                patient.responsibleSpecialistId == currentDoctorId ||
                patient.tasks.any { !it.done && it.ownerDoctorId == currentDoctorId }
            val taskCounts = PatientTasks.counts(patient.tasks, state.taskNowEpochMillis)
            val matchesTasks = when (taskFilter) {
                DashboardFilter.TASK_PENDING -> taskCounts.pending > 0
                DashboardFilter.TASK_OVERDUE -> taskCounts.overdue > 0
                DashboardFilter.TASK_UNASSIGNED -> taskCounts.unassigned > 0
                else -> true
            }
            matchesTasks && matchesOwner && (!priorityOnly || patient.isPriority) &&
                (!warningsOnly || patient.badges.isNotEmpty()) &&
                (!unassignedOnly || patient.responsibleResidentId == null || patient.responsibleSpecialistId == null) &&
                (!urgentOnly || patient.isPriority ||
                    patient.badges.any { it.priority == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH }) &&
                (!newAdmissionsOnly || patient.admittanceDays == 0) &&
                ArabicSearchNormalizer.matches(
                searchQuery,
                patient.name,
                patient.admittanceNumber,
                patient.initialDiagnosis,
                patient.treatmentPlan,
                patient.followUp,
                patient.labs,
                patient.tasks.joinToString(" ") { it.description }
            )
        }.sortedByDescending { it.id in pinnedPatientIds }
    }

    fun closeDrawer(after: () -> Unit = {}) {
        scope.launch {
            drawerState.close()
            after()
        }
    }

    val patientDestination = viewMode == WardViewMode.ALL || viewMode == WardViewMode.MINE
    val selectedPatient = state.patients.firstOrNull { it.id == detailsTargetId }
    val roundsPatients = if (state.groupByMode != GroupByMode.NONE && state.groupedPatients.isNotEmpty()) {
        state.groupedPatients.flatMap { group ->
            group.patients.filter { it in visiblePatients }.sortedByDescending { it.id in pinnedPatientIds }
        }.distinctBy { it.id }
    } else visiblePatients
    val roundsNavigation = detailsTargetId?.let { patientRoundsNavigation(roundsPatients.map { it.id }, it) }

    LaunchedEffect(returnTarget, state.patients, state.groupedPatients, visiblePatients, state.loading, state.saving) {
        val target = returnTarget ?: return@LaunchedEffect
        if (state.loading || state.saving) return@LaunchedEffect
        val saved = state.patients.firstOrNull { it.id == target.id } ?: return@LaunchedEffect
        // Repository flow can arrive after the success callback. Reveal the saved revision.
        if (saved.revision <= target.previousRevision) return@LaunchedEffect
        if (visiblePatients.none { it.id == target.id }) {
            returnTarget = null
            scope.launch { snackbarHost.showSnackbar("تم حفظ ${saved.name}؛ لا يطابق التصفية الحالية") }
            return@LaunchedEffect
        }
        val groups = if (state.groupByMode == GroupByMode.NONE) emptyList() else state.groupedPatients.map { group ->
            (group.key ?: "none") to group.patients.filter { it in visiblePatients }
                .sortedByDescending { it.id in pinnedPatientIds }.map { it.id }
        }
        val groupKey = groups.firstOrNull { target.id in it.second }?.first
        val expandedGroups = if (groupKey == null) collapsedGroups else collapsedGroups - groupKey
        collapsedGroups = expandedGroups
        val index = patientGridReturnIndex(target.id, visiblePatients.map { it.id }, groups, expandedGroups)
            ?: return@LaunchedEffect
        withFrameNanos { } // Let group expansion update the item provider before scrolling.
        patientGridState.scrollToItem(index)
        recentlyEditedId = target.id
        returnTarget = null
    }
    LaunchedEffect(recentlyEditedId) {
        if (recentlyEditedId != null) {
            delay(1800)
            recentlyEditedId = null
        }
    }
    val detailContent: @Composable (Boolean) -> Unit = { embedded ->
        if (selectedPatient != null && editTarget == null && copyTarget == null) {
            detailStateHolder.SaveableStateProvider(selectedPatient.id) {
                PatientDetailsScreen(
                    patient = selectedPatient, doctorNames = doctorNames,
                    activity = state.recentActivity.filter { it.patientId == selectedPatient.id },
                    readOnly = state.isReadOnly,
                    onEdit = { editTarget = selectedPatient },
                    onCopy = { copyTarget = selectedPatient },
                    onPriorityChange = { viewModel.setPriority(selectedPatient, it) },
                    onDismiss = { detailsTargetId = null }, embedded = embedded, foldingFeature = foldingFeature,
                    roundsNavigation = roundsNavigation,
                    navigationEnabled = !state.saving && !showAdd,
                    onNavigateToPatient = { id ->
                        if (!state.saving && !showAdd && editTarget == null && copyTarget == null && roundsPatients.any { it.id == id }) {
                            detailsTargetId = id
                        }
                    }
                )
            }
        }
    }
    BackHandler(enabled = !patientDestination) { viewMode = WardViewMode.ALL }
    BackHandler(enabled = patientDestination && detailsTargetId != null && editTarget == null && copyTarget == null) {
        detailsTargetId = null
    }
    LaunchedEffect(viewMode, isAdmin) {
        if (viewMode == WardViewMode.ACTIVITY) {
            if (isAdmin) viewModel.loadRecentActivity() else viewMode = WardViewMode.ALL
        }
    }
    LaunchedEffect(detailsTargetId, state.patients.map { it.revision }) { viewModel.loadLocalActivity() }
    LaunchedEffect(state.loading, state.shift?.id, state.patients.map { it.id }) {
        if (!state.loading && selectedPatient == null) detailsTargetId = null
    }

    fun applyDashboardFilter(filter: DashboardFilter) {
        priorityOnly = false
        warningsOnly = false
        unassignedOnly = false
        urgentOnly = false
        newAdmissionsOnly = false
        taskFilter = null
        viewMode = WardViewMode.ALL
        when (filter) {
            DashboardFilter.ALL -> Unit
            DashboardFilter.MINE -> viewMode = WardViewMode.MINE
            DashboardFilter.URGENT -> urgentOnly = true
            DashboardFilter.WARNINGS -> warningsOnly = true
            DashboardFilter.UNASSIGNED -> unassignedOnly = true
            DashboardFilter.NEW_ADMISSIONS -> newAdmissionsOnly = true
            DashboardFilter.TASK_PENDING, DashboardFilter.TASK_OVERDUE, DashboardFilter.TASK_UNASSIGNED -> taskFilter = filter
        }
    }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            val result = snackbarHost.showSnackbar(
                message = it,
                actionLabel = if (state.retryAction != null) "إعادة المحاولة" else null
            )
            viewModel.dismissSnackbar()
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.retryLastAction()
            }
        }
    }

    LaunchedEffect(state.isReadOnly) {
        if (state.isReadOnly) {
            showAdd = false
            editTarget = null
            deleteTarget = null
            showSort = false
            showShiftDoctors = false
        }
    }

    LaunchedEffect(state.rolloverPatients) {
        rolloverDecisions = state.rolloverPatients.associate { it.id to RolloverDecision.CONTINUE }
    }

    LaunchedEffect(state.mergeConflicts) {
        conflictChoices = conflictChoices.filterKeys { key ->
            state.mergeConflicts.any { it.key == key }
        }
    }

    LaunchedEffect(state.patients.map { it.id }) {
        val activeIds = state.patients.mapTo(mutableSetOf()) { it.id }
        pinnedPatientIds = pinnedPatientIds.filter { it in activeIds }
        expandedPatientIds = expandedPatientIds.filter { it in activeIds }
        collapsedPatientIds = collapsedPatientIds.filter { it in activeIds }
    }

    LaunchedEffect(state.rolloverReviewPatients, editTarget) {
        if (editTarget == null) editTarget = state.rolloverReviewPatients.firstOrNull()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            WardDrawer(
                state = state,
                currentDoctorName = doctorNames[currentDoctorId],
                isAdmin = isAdmin,
                online = online,
                freshnessLabel = freshnessLabel,
                viewMode = viewMode,
                allPatientsSelected = viewMode == WardViewMode.ALL &&
                    !priorityOnly && !warningsOnly && !unassignedOnly && !urgentOnly &&
                    !newAdmissionsOnly && taskFilter == null && searchQuery.isBlank(),
                filtersSelected = priorityOnly || warningsOnly || unassignedOnly || urgentOnly ||
                    newAdmissionsOnly || searchQuery.isNotBlank(),
                onAction = { action ->
                    when (action) {
                        WardDrawerAction.SYNC_DETAILS -> scope.launch {
                            snackbarHost.showSnackbar(state.lastOperation ?: state.syncStatus.arabicLabel)
                        }
                        WardDrawerAction.DISMISS_SYNC_HINT -> viewModel.dismissSyncHint()
                        else -> closeDrawer {
                            when (action) {
                                WardDrawerAction.ALL_PATIENTS -> applyDashboardFilter(DashboardFilter.ALL)
                                WardDrawerAction.REPORT -> state.shift?.id?.let(onOpenReport)
                                WardDrawerAction.ACTIVITY -> viewMode = WardViewMode.ACTIVITY
                                WardDrawerAction.SETTINGS -> onOpenSettings()
                                WardDrawerAction.DASHBOARD -> viewMode = WardViewMode.DASHBOARD
                                WardDrawerAction.MY_PATIENTS -> viewMode = WardViewMode.MINE
                                WardDrawerAction.FILTERS -> showFilters = true
                                WardDrawerAction.SORT -> if (!state.isReadOnly) showSort = true
                                WardDrawerAction.GROUP -> showGroupBy = true
                                WardDrawerAction.SHIFT_DOCTORS -> if (!state.isReadOnly) showShiftDoctors = true
                                WardDrawerAction.SYNC -> confirmLatest = true
                                WardDrawerAction.PREVIOUS -> confirmPrevious = true
                                WardDrawerAction.EXPORT_CSV -> viewModel.exportCsv()
                                WardDrawerAction.RECYCLE_BIN -> {
                                    viewModel.loadRecycleBin()
                                    showRecycleBin = true
                                }
                                WardDrawerAction.ADMIN -> onOpenAdmin()
                                WardDrawerAction.IMPORT -> onOpenVbaImport()
                                WardDrawerAction.ABOUT -> onOpenAbout()
                                WardDrawerAction.SYNC_DETAILS, WardDrawerAction.DISMISS_SYNC_HINT -> Unit
                            }
                        }
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                    listFraction = .42f, onListFractionChange = {},
                    modifier = Modifier.fillMaxWidth().height(topBarHeight), primaryTitle = "أدوات المناوبة", primary = {
                        TopAppBar(
                            title = {
                                val appName = stringResource(R.string.app_name)
                                Column {
                                    Text(
                                        state.hospitalName.ifBlank { appName },
                                        modifier = Modifier.semantics { heading() },
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${state.shift?.date ?: "—"} · ${state.patients.size} مريض",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "القائمة")
                                }
                            },
                            actions = {
                                if (patientDestination) {
                                    IconButton(onClick = ::toggleAllCards, enabled = state.patients.isNotEmpty()) {
                                        Icon(if (anyCardsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (anyCardsExpanded) "طي جميع بطاقات المرضى" else "توسيع جميع بطاقات المرضى")
                                    }
                                }
                                if (!state.isReadOnly) {
                                    IconButton(onClick = { showAdd = true }) {
                                        Icon(Icons.Filled.Add, contentDescription = "إضافة مريض")
                                    }
                                }
                                IconButton(onClick = {
                                    if (patientDestination && showSearch && searchQuery.isBlank()) {
                                        showSearch = false
                                        requestSearchFocus = false
                                        focusManager.clearFocus()
                                    } else {
                                        if (!patientDestination) viewMode = WardViewMode.ALL
                                        showSearch = true
                                        requestSearchFocus = true
                                        scope.launch { patientGridState.animateScrollToItem(0) }
                                    }
                                }) {
                                    Icon(Icons.Filled.Search, contentDescription = "بحث عن مريض")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                })
            },
            floatingActionButton = {
                state.shift?.takeIf { !patientDestination || selectedPatient == null }?.let {
                    AppButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showReportSheet = true
                        },
                        contentPadding = UiPadding.content,
                        modifier = Modifier.heightIn(min = UiSpacing.touchTarget).semantics {
                            stateDescription = if (reportReadinessIssueCount == 0) "التقرير جاهز"
                                else "$reportReadinessIssueCount ملاحظات جاهزية"
                        }
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
                        Text("إرسال تقرير", Modifier.padding(start = UiSpacing.small))
                        if (reportReadinessIssueCount > 0) {
                            Badge(modifier = Modifier.padding(start = UiSpacing.small),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) { Text(reportReadinessIssueCount.toString()) }
                        }
                    }
                }
            },
            bottomBar = {
                if (!expandedWindow) {
                    WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                        listFraction = .42f, onListFractionChange = {},
                        modifier = Modifier.fillMaxWidth().height(bottomBarHeight), primaryTitle = "التنقل الرئيسي", primary = {
                        PrimaryNavigationBar(
                            viewMode = viewMode,
                            isAdmin = isAdmin,
                            onPatients = { viewMode = WardViewMode.ALL },
                            onDashboard = { viewMode = WardViewMode.DASHBOARD },
                            onActivity = {
                                viewMode = WardViewMode.ACTIVITY
                            }
                        )
                    })
                }
            },
            snackbarHost = { SnackbarHost(snackbarHost) }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (expandedWindow) {
                    WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                        listFraction = .42f, onListFractionChange = {},
                        modifier = Modifier.width(80.dp).fillMaxHeight(), primaryTitle = "التنقل الرئيسي", primary = {
                        PrimaryNavigationRail(
                            viewMode = viewMode,
                            isAdmin = isAdmin,
                            onPatients = { viewMode = WardViewMode.ALL },
                            onDashboard = { viewMode = WardViewMode.DASHBOARD },
                            onActivity = {
                                viewMode = WardViewMode.ACTIVITY
                            }
                        )
                    })
                }
                WardAdaptivePanes(
                    enableSplit = patientDestination,
                    allowMediumSupport = selectedPatient == null,
                    foldingFeature = foldingFeature,
                    listFraction = listFraction,
                    onListFractionChange = { listFraction = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    primaryTitle = if (patientDestination) "قائمة المرضى" else if (viewMode == WardViewMode.ACTIVITY) "مركز النشاط" else "لوحة المناوبة",
                    supportingTitle = if (selectedPatient == null) "جاهزية التقرير والنشاط" else "ملف المريض",
                    supporting = {
                        if (selectedPatient != null && editTarget == null && copyTarget == null) detailContent(true)
                        else WardSupportingPane(state, freshnessLabel, online, isAdmin = isAdmin,
                            onReviewReport = { showReportSheet = true },
                            onActivity = { viewMode = WardViewMode.ACTIVITY })
                    },
                    compactOverlay = { if (patientDestination) detailContent(false) },
                    primary = { dualPane ->
                    Box(Modifier.fillMaxSize()) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (state.error != null) {
                            AppNotice(
                                message = "تعذر تحميل بيانات الوردية\n${state.error.orEmpty()}",
                                kind = NoticeKind.ERROR,
                                modifier = Modifier.align(Alignment.Center).padding(UiPadding.content),
                                actionLabel = if (state.retryAction != null) "إعادة المحاولة" else null,
                                onAction = if (state.retryAction != null) viewModel::retryLastAction else null,
                                actionEnabled = !state.syncing && !state.saving
                            )
                        } else if (viewMode == WardViewMode.ACTIVITY && isAdmin) {
                            WardActivityScreen(
                                activity = state.recentActivity, publications = state.publications,
                                patientNames = state.patients.associate { it.id to it.name },
                                query = activityQuery, onQueryChange = { activityQuery = it },
                                filter = activityFilter, onFilterChange = { activityFilter = it },
                                activityScroll = activityScroll, publicationScroll = publicationScroll,
                                onRefresh = viewModel::loadRecentActivity,
                                loading = state.activityLoading, error = state.activityError
                            )
                        } else if (viewMode == WardViewMode.DASHBOARD) {
                            HandoverDashboard(
                                isAdmin = isAdmin,
                                patients = state.patients,
                                currentDoctorId = currentDoctorId,
                                syncStatus = state.syncStatus,
                                nowEpochMillis = state.taskNowEpochMillis,
                                onFilter = ::applyDashboardFilter,
                                onSync = { confirmLatest = true },
                                onActivity = {
                                    viewMode = WardViewMode.ACTIVITY
                                },
                                modifier = Modifier.padding(UiPadding.content)
                            )
                        } else if (state.patients.isEmpty()) {
                            EmptyState(
                                title = "لا يوجد مرضى",
                                subtitle = if (state.isReadOnly) {
                                    "هذه مناوبة محفوظة للعرض فقط"
                                } else {
                                    "أضف مريضاً جديداً للبدء"
                                },
                                actionLabel = if (state.isReadOnly) null else "إضافة مريض",
                                onAction = if (state.isReadOnly) null else ({ showAdd = true })
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = if (dualPane) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 360.dp),
                                state = patientGridState,
                                contentPadding = PaddingValues(bottom = 104.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = UiSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(UiSpacing.small),
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                        Text(
                                            text = "${visiblePatients.size} مريض • تاريخ المناوبة: " +
                                                    (state.shift?.date ?: ""),
                                            modifier = Modifier.padding(top = UiSpacing.small),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.micro)
                                        ) {
                                            AppTextButton(
                                                onClick = { showFilters = true },
                                                contentPadding = PaddingValues(horizontal = UiSpacing.small, vertical = 0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.FilterList,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(UiSize.iconSmall)
                                                )
                                                Text("تصفية", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                            CompactFilterChip(
                                                selected = viewMode == WardViewMode.ALL && !urgentOnly &&
                                                    !unassignedOnly && !warningsOnly && !priorityOnly &&
                                                    !newAdmissionsOnly && taskFilter == null && searchQuery.isBlank(),
                                                onClick = { applyDashboardFilter(DashboardFilter.ALL) },
                                                label = "الكل"
                                            )
                                            CompactFilterChip(
                                                selected = viewMode == WardViewMode.MINE,
                                                onClick = { applyDashboardFilter(DashboardFilter.MINE) },
                                                label = "مرضاي"
                                            )
                                            CompactFilterChip(
                                                selected = urgentOnly,
                                                onClick = { applyDashboardFilter(DashboardFilter.URGENT) },
                                                label = "عاجل"
                                            )
                                            CompactFilterChip(
                                                selected = unassignedOnly,
                                                onClick = { applyDashboardFilter(DashboardFilter.UNASSIGNED) },
                                                label = "غير معيّن"
                                            )
                                            CompactFilterChip(
                                                selected = taskFilter == DashboardFilter.TASK_PENDING,
                                                onClick = { applyDashboardFilter(DashboardFilter.TASK_PENDING) }, label = "مهام معلقة"
                                            )
                                            CompactFilterChip(
                                                selected = taskFilter == DashboardFilter.TASK_OVERDUE,
                                                onClick = { applyDashboardFilter(DashboardFilter.TASK_OVERDUE) }, label = "مهام متأخرة"
                                            )
                                            CompactFilterChip(
                                                selected = taskFilter == DashboardFilter.TASK_UNASSIGNED,
                                                onClick = { applyDashboardFilter(DashboardFilter.TASK_UNASSIGNED) }, label = "مهام غير معيّنة"
                                            )
                                            CompactFilterChip(
                                                selected = state.compactCards,
                                                onClick = { viewModel.setCompactCards(!state.compactCards) },
                                                label = if (state.compactCards) "مضغوط" else "مريح"
                                            )
                                        }
                                        if (state.isReadOnly) {
                                            AppNotice("نسخة محفوظة — للعرض والاستعادة فقط", icon = Icons.Filled.Lock)
                                        }
                                        if (showSearch || searchQuery.isNotBlank()) {
                                            WardPatientSearchField(
                                                query = searchQuery,
                                                onQueryChange = { searchQuery = it },
                                                requestFocus = requestSearchFocus,
                                                onFocusRequested = { requestSearchFocus = false }
                                            )
                                        }
                                        if (priorityOnly || warningsOnly || unassignedOnly || urgentOnly ||
                                            newAdmissionsOnly || taskFilter != null || searchQuery.isNotBlank()
                                        ) {
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                                if (searchQuery.isNotBlank()) FilterChip(
                                                    selected = true,
                                                    onClick = { searchQuery = "" },
                                                    label = { Text("بحث: $searchQuery ×") }
                                                )
                                                if (priorityOnly) ActiveFilterChip("أولوية", { priorityOnly = false })
                                                if (warningsOnly) ActiveFilterChip("مع شارة", { warningsOnly = false })
                                                if (unassignedOnly) ActiveFilterChip("غير معيّن", { unassignedOnly = false })
                                                if (urgentOnly) ActiveFilterChip("عاجل", { urgentOnly = false })
                                                if (newAdmissionsOnly) ActiveFilterChip("دخول اليوم", { newAdmissionsOnly = false })
                                                val taskFilterLabel = when (taskFilter) {
                                                    DashboardFilter.TASK_PENDING -> "مهام معلقة"
                                                    DashboardFilter.TASK_OVERDUE -> "مهام متأخرة"
                                                    DashboardFilter.TASK_UNASSIGNED -> "مهام غير معيّنة"
                                                    else -> null
                                                }
                                                taskFilterLabel?.let { label ->
                                                    ActiveFilterChip(label, { taskFilter = null })
                                                }
                                            }
                                        }
                                    }
                                }

                                if (visiblePatients.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyState(
                                            title = "لا توجد نتائج مطابقة",
                                            subtitle = "غيّر البحث أو أزل بعض عوامل التصفية",
                                            actionLabel = "مسح عوامل التصفية",
                                            onAction = {
                                                searchQuery = ""
                                                priorityOnly = false
                                                warningsOnly = false
                                                unassignedOnly = false
                                                urgentOnly = false
                                                newAdmissionsOnly = false
                                                taskFilter = null
                                            }
                                        )
                                    }
                                } else if (state.groupByMode != GroupByMode.NONE &&
                                    state.groupedPatients.isNotEmpty()
                                ) {
                                    state.groupedPatients.forEach { group ->
                                        val groupPatients = group.patients
                                            .filter { it in visiblePatients }
                                            .sortedByDescending { it.id in pinnedPatientIds }
                                        if (groupPatients.isEmpty()) return@forEach
                                        item(
                                            key = "grp-${group.key ?: "none"}",
                                            span = { GridItemSpan(maxLineSpan) }
                                        ) {
                                            GroupHeader(
                                                name = group.name,
                                                count = groupPatients.size,
                                                collapsed = (group.key ?: "none") in collapsedGroups,
                                                onToggle = {
                                                    val key = group.key ?: "none"
                                                    collapsedGroups = if (key in collapsedGroups) {
                                                        collapsedGroups - key
                                                    } else collapsedGroups + key
                                                }
                                            )
                                        }
                                        if ((group.key ?: "none") !in collapsedGroups) gridItems(groupPatients, key = { it.id }) { patient ->
                                            PatientCard(
                                                patient = patient,
                                                expanded = if (state.patientDetailsExpanded) patient.id !in collapsedPatientIds else patient.id in expandedPatientIds,
                                                animateExpansion = animatedPatientId == patient.id,
                                                twoColumn = state.twoColumn,
                                                doctorNames = doctorNames,
                                                readOnly = state.isReadOnly,
                                                selected = detailsTargetId == patient.id,
                                                recentlyEdited = recentlyEditedId == patient.id,
                                                onEdit = { editTarget = patient },
                                                onClick = {
                                                    if (dualPane || state.isReadOnly) {
                                                        viewModel.loadLocalActivity()
                                                        detailsTargetId = patient.id
                                                    } else {
                                                        editTarget = patient
                                                    }
                                                },
                                                onLongClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.loadLocalActivity()
                                                    detailsTargetId = patient.id
                                                },
                                                onCopy = { copyTarget = patient },
                                                onPriorityChange = { viewModel.setPriority(patient, it) },
                                                onDelete = { deleteTarget = patient },
                                                pinned = patient.id in pinnedPatientIds,
                                                onPinToggle = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    pinnedPatientIds = if (patient.id in pinnedPatientIds) {
                                                        pinnedPatientIds - patient.id
                                                    } else pinnedPatientIds + patient.id
                                                },
                                                onExpandToggle = { toggleCard(patient.id) },
                                                taskNowEpochMillis = state.taskNowEpochMillis,
                                                compact = state.compactCards
                                            )
                                        }
                                    }
                                } else {
                                    gridItems(visiblePatients, key = { it.id }) { patient ->
                                        PatientCard(
                                            patient = patient,
                                            expanded = if (state.patientDetailsExpanded) patient.id !in collapsedPatientIds else patient.id in expandedPatientIds,
                                            animateExpansion = animatedPatientId == patient.id,
                                            twoColumn = state.twoColumn,
                                            doctorNames = doctorNames,
                                            readOnly = state.isReadOnly,
                                            selected = detailsTargetId == patient.id,
                                                recentlyEdited = recentlyEditedId == patient.id,
                                            onEdit = { editTarget = patient },
                                            onClick = {
                                                if (dualPane || state.isReadOnly) {
                                                    viewModel.loadLocalActivity()
                                                    detailsTargetId = patient.id
                                                } else {
                                                    editTarget = patient
                                                }
                                            },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.loadLocalActivity()
                                                detailsTargetId = patient.id
                                            },
                                            onCopy = { copyTarget = patient },
                                            onPriorityChange = { viewModel.setPriority(patient, it) },
                                            onDelete = { deleteTarget = patient },
                                            pinned = patient.id in pinnedPatientIds,
                                            onPinToggle = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                pinnedPatientIds = if (patient.id in pinnedPatientIds) {
                                                    pinnedPatientIds - patient.id
                                                } else pinnedPatientIds + patient.id
                                            },
                                            onExpandToggle = { toggleCard(patient.id) },
                                            taskNowEpochMillis = state.taskNowEpochMillis,
                                            compact = state.compactCards
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                )
            }
        }
    }

    if (showSupportingSheet) {
        ModalBottomSheet(onDismissRequest = { showSupportingSheet = false }) {
            WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                listFraction = .42f, onListFractionChange = {},
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp), primary = {
                WardSupportingPane(state, freshnessLabel, online, isAdmin = isAdmin,
                    onReviewReport = { showSupportingSheet = false; showReportSheet = true },
                    onActivity = { showSupportingSheet = false; viewMode = WardViewMode.ACTIVITY },
                    modifier = Modifier.fillMaxSize())
            })
        }
    }

    if (showReportSheet) {
        WardReportSheet(
            patientCount = state.patients.size,
            freshnessLabel = freshnessLabel,
            issueCount = reportReadinessIssueCount,
            hasMergeConflicts = state.mergeConflicts.isNotEmpty(),
            foldingFeature = foldingFeature,
            onReviewReport = {
                state.shift?.id?.let { shiftId ->
                    showReportSheet = false
                    onOpenReport(shiftId)
                }
            },
            onDismiss = { showReportSheet = false }
        )
    }

    if (showAdd) {
        AddPatientDialog(
            doctors = state.doctors,
            draft = state.patientDraft,
            saving = state.saving,
            onDraftChanged = viewModel::saveDraft,
            onConfirm = {
                viewModel.addPatient(it) { showAdd = false }
            },
            onDismiss = { showAdd = false }
        )
    }

    if (state.rolloverPatients.isNotEmpty()) {
        RolloverReviewDialog(
            patients = state.rolloverPatients,
            decisions = rolloverDecisions,
            onDecision = { id, decision -> rolloverDecisions = rolloverDecisions + (id to decision) },
            onConfirm = { viewModel.applyRollover(rolloverDecisions) },
            onDismiss = viewModel::dismissRollover
        )
    }

    if (state.mergeConflicts.isNotEmpty()) {
        PatientConflictDialog(
            conflicts = state.mergeConflicts,
            choices = conflictChoices,
            resolving = state.resolvingConflicts,
            onChoice = { key, choice -> conflictChoices = conflictChoices + (key to choice) },
            onConfirm = { viewModel.resolveMergeConflicts(conflictChoices) },
            onDismiss = viewModel::dismissMergeConflicts
        )
    }

    if (confirmLatest) {
        ConfirmDialog(
            title = "تحديث البيانات المحلية",
            message = "سيتم استبدال بيانات المرضى المحلية بأحدث نسخة منشورة. هل تريد المتابعة؟",
            onConfirm = {
                confirmLatest = false
                viewModel.bringLatestData()
            },
            onDismiss = { confirmLatest = false }
        )
    }

    if (confirmPrevious) {
        ConfirmDialog(
            title = "استعادة النسخة السابقة",
            message = "سيتم استبدال البيانات المحلية بالنسخة التي سبقت أحدث نشر. لن تتغير النسخة المنشورة على تلجرام.",
            onConfirm = {
                confirmPrevious = false
                viewModel.bringPreviousData()
            },
            onDismiss = { confirmPrevious = false }
        )
    }

    if (state.showSnapshotPicker) {
        ShiftSnapshotPickerDialog(
            options = state.snapshotChoices,
            onSelect = viewModel::selectDownloadedSnapshot,
            onDismiss = viewModel::dismissSnapshotPicker
        )
    }

    editTarget?.let { p ->
        EditPatientDialog(
            patient = p,
            doctors = state.doctors,
            saving = state.saving,
            onConfirm = { draft, onStale ->
                viewModel.updatePatient(draft, onStale) {
                    viewModel.consumeRolloverReview(p.id)
                    returnTarget = PatientReturnTarget(draft.id, draft.revision)
                    editTarget = null
                }
            },
            onDismiss = {
                viewModel.consumeRolloverReview(p.id)
                editTarget = null
            }
        )
    }

    copyTarget?.let { source ->
        CopyPatientDialog(
            source = source,
            doctors = state.doctors,
            saving = state.saving,
            onConfirm = { patient ->
                viewModel.addPatient(patient) { copyTarget = null }
            },
            onDismiss = { copyTarget = null }
        )
    }

    deleteTarget?.let { p ->
        ConfirmDialog(
            title = "حذف مريض",
            message = "هل أنت متأكد من حذف ${p.name}؟",
            onConfirm = {
                viewModel.deletePatient(p) { deletedPatient ->
                    deleteTarget = null
                    scope.launch {
                        val result = snackbarHost.showSnackbar(
                            message = "تم نقل ${p.name} إلى سلة المحذوفات",
                            actionLabel = "تراجع"
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            viewModel.restorePatient(deletedPatient)
                        }
                    }
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showSort) {
        SortSheet(
            initial = state.sortSpec,
            initialRevision = state.shift?.revision ?: 0,
            saving = state.saving,
            onApply = { spec, revision, onStale ->
                viewModel.applySort(spec, revision, onStale) { showSort = false }
            },
            onDismiss = { showSort = false }
        )
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("بحث وتصفية وترتيب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    AppTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("بحث") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    FilterChip(priorityOnly, { priorityOnly = !priorityOnly }, { Text("أولوية فقط") })
                    FilterChip(warningsOnly, { warningsOnly = !warningsOnly }, { Text("مع شارة") })
                    FilterChip(unassignedOnly, { unassignedOnly = !unassignedOnly }, { Text("غير معيّن") })
                    FilterChip(urgentOnly, { urgentOnly = !urgentOnly }, { Text("عاجل") })
                    FilterChip(newAdmissionsOnly, { newAdmissionsOnly = !newAdmissionsOnly }, { Text("دخول اليوم") })
                    AppTextButton(onClick = { showFilters = false; showSort = true }) { Text("خيارات الترتيب") }
                    AppTextButton(onClick = { showFilters = false; showGroupBy = true }) { Text("خيارات التجميع") }
                }
            },
            confirmButton = { AppTextButton(onClick = { showFilters = false; viewMode = WardViewMode.ALL }) { Text("تطبيق") } },
            dismissButton = {
                AppTextButton(onClick = {
                    searchQuery = ""; priorityOnly = false; warningsOnly = false; unassignedOnly = false
                    urgentOnly = false; newAdmissionsOnly = false; taskFilter = null
                }) { Text("مسح") }
            }
        )
    }

    if (showGroupBy) {
        GroupBySheet(
            initial = state.groupByMode,
            onApply = { mode ->
                viewModel.setGroupByMode(mode)
                showGroupBy = false
            },
            onDismiss = { showGroupBy = false }
        )
    }

    if (showShiftDoctors) {
        ShiftDoctorPicker(
            allDoctors = state.doctors,
            initialSelected = state.shift?.doctorIds ?: emptyList(),
            initialRevision = state.shift?.revision ?: 0,
            saving = state.saving,
            onConfirm = { ids, revision, onStale ->
                viewModel.setShiftDoctors(ids, revision, onStale) { showShiftDoctors = false }
            },
            onDismiss = { showShiftDoctors = false }
        )
    }

    if (showRecycleBin) {
        PatientRecycleBinDialog(
            patients = state.deletedPatients,
            readOnly = state.isReadOnly,
            onRestore = viewModel::restorePatient,
            onDismiss = { showRecycleBin = false }
        )
    }

}
