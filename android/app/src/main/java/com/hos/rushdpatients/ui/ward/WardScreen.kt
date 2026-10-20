package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.theme.UiPatientCard
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.components.noticeColors
import com.hos.rushdpatients.ui.components.uiNumber
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import com.hos.rushdpatients.ui.components.rememberFloatingActionSpace
import com.hos.rushdpatients.ui.components.AppSectionIndex
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WardScreen(
    isAdmin: Boolean,
    currentDoctorId: String,
    onOpenReport: (String) -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenDataManagement: () -> Unit = onOpenSettings,
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
    val freshnessLabel = state.freshnessLabel
    val doctorNames = remember(state.doctors) {
        state.doctors.associate { it.id to it.fullName }
    }
    val patientGridState = rememberLazyGridState()
    val reportReadinessIssueCount = remember(state.patients) {
        ReportReadiness.warnings(state.patients).size
    }

    var showAdd by remember { mutableStateOf(false) }
    var detailsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<Patient?>(null) }
    var returnTarget by remember { mutableStateOf<PatientReturnTarget?>(null) }
    var recentlyEditedId by remember { mutableStateOf<String?>(null) }
    var copyTarget by remember { mutableStateOf<Patient?>(null) }
    var deleteTarget by remember { mutableStateOf<Patient?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showShiftDoctors by remember { mutableStateOf(false) }
    var confirmLatest by remember { mutableStateOf(false) }
    var confirmPrevious by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showRecycleBin by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(WardViewMode.ALL) }
    var collapsedGroups by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedViewOption by rememberSaveable { mutableStateOf(WardViewOption.FILTERS) }
    val viewOptionsScroll = rememberScrollState()
    var priorityOnly by rememberSaveable { mutableStateOf(false) }
    var warningsOnly by rememberSaveable { mutableStateOf(false) }
    var unassignedOnly by rememberSaveable { mutableStateOf(false) }
    var taskFilter by rememberSaveable { mutableStateOf<DashboardFilter?>(null) }
    var urgentOnly by rememberSaveable { mutableStateOf(false) }
    var newAdmissionsOnly by rememberSaveable { mutableStateOf(false) }
    var showReportSheet by rememberSaveable { mutableStateOf(false) }
    var expandedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var collapsedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val anyCardsExpanded = state.patients.any { patient ->
        if (state.patientDetailsExpanded) patient.id !in collapsedPatientIds else patient.id in expandedPatientIds
    }
    var showWardTasks by remember { mutableStateOf(false) }
    var animatedPatientId by remember { mutableStateOf<String?>(null) }
    fun setAllPatientsExpanded(expand: Boolean) {
        animatedPatientId = null
        val ids = state.patients.map { it.id }
        // Both override sets express the target immediately, even before preference IO completes.
        expandedPatientIds = if (expand) ids else emptyList()
        collapsedPatientIds = if (expand) emptyList() else ids
        viewModel.setPatientDetailsExpanded(expand)
    }
    fun toggleAllCards() = setAllPatientsExpanded(!anyCardsExpanded)
    fun toggleCard(id: String) {
        animatedPatientId = id
        if (state.patientDetailsExpanded) {
            collapsedPatientIds = if (id in collapsedPatientIds) collapsedPatientIds - id else collapsedPatientIds + id
        } else {
            expandedPatientIds = if (id in expandedPatientIds) expandedPatientIds - id else expandedPatientIds + id
        }
    }
    var pinnedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
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
    val reportActionSpace = rememberFloatingActionSpace(state.shift != null && (!patientDestination || selectedPatient == null))
    val roundsPatients = if (state.groupByMode != GroupByMode.NONE && state.groupedPatients.isNotEmpty()) {
        state.groupedPatients.flatMap { group ->
            group.patients.filter { it in visiblePatients }.sortedByDescending { it.id in pinnedPatientIds }
        }.distinctBy { it.id }
    } else visiblePatients
    val roundsNavigation = detailsTargetId?.let { patientRoundsNavigation(roundsPatients.map { it.id }, it) }

    LaunchedEffect(returnTarget, state.patients, state.groupedPatients, visiblePatients, state.loading, state.saving, state.error, patientDestination) {
        val target = returnTarget ?: return@LaunchedEffect
        if (!patientDestination || state.loading || state.saving || state.error != null) return@LaunchedEffect
        val saved = state.patients.firstOrNull { it.id == target.id } ?: return@LaunchedEffect
        // Repository flow can arrive after the success callback. Reveal the saved revision.
        if (saved.revision <= target.previousRevision) return@LaunchedEffect
        if (visiblePatients.none { it.id == target.id }) {
            returnTarget = null
            scope.launch { snackbarHost.showSnackbar("تم حفظ ${saved.name} محلياً؛ لا يطابق التصفية الحالية") }
            return@LaunchedEffect
        }
        val groups = if (state.groupByMode == GroupByMode.NONE) emptyList() else state.groupedPatients.map { group ->
            (group.key ?: "none") to group.patients.filter { it in visiblePatients }
                .sortedByDescending { it.id in pinnedPatientIds }.map { it.id }
        }
        val groupKey = groups.firstOrNull { target.id in it.second }?.first
        val expandedGroups = if (groupKey == null) collapsedGroups else collapsedGroups - groupKey
        collapsedGroups = expandedGroups
        val index = patientGridReturnIndex(target.id, visiblePatients.map { it.id }, groups, expandedGroups.toSet())
            ?: return@LaunchedEffect
        withFrameNanos { } // Let group expansion update the item provider before scrolling.
        if (patientGridState.layoutInfo.visibleItemsInfo.none { it.key == target.id }) {
            patientGridState.scrollToItem(index)
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
    if (showWardTasks) WardTaskBoard(state.patients, state.doctors, state.taskNowEpochMillis,
        onDismiss = { showWardTasks = false }, onPatient = { id ->
            if (!state.saving && !state.syncing && !showAdd && editTarget == null && copyTarget == null && state.patients.any { it.id == id }) {
                showWardTasks = false
                viewMode = WardViewMode.ALL
                searchQuery = ""; priorityOnly = false; warningsOnly = false; unassignedOnly = false
                urgentOnly = false; newAdmissionsOnly = false; taskFilter = null
                detailsTargetId = id
            }
        })
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
        searchQuery = ""
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

    fun resetPatientFilters() {
        searchQuery = ""
        applyDashboardFilter(DashboardFilter.ALL)
        focusManager.clearFocus()
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

    LaunchedEffect(state.loading, state.patients.map { it.id }) {
        if (state.loading) return@LaunchedEffect
        val activeIds = state.patients.mapTo(mutableSetOf()) { it.id }
        pinnedPatientIds = pinnedPatientIds.filter { it in activeIds }
        expandedPatientIds = expandedPatientIds.filter { it in activeIds }
        collapsedPatientIds = collapsedPatientIds.filter { it in activeIds }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            WardDrawer(
                state = state,
                currentDoctorName = doctorNames[currentDoctorId],
                isAdmin = isAdmin,
                online = online,
                viewMode = viewMode,
                allPatientsSelected = viewMode == WardViewMode.ALL &&
                    !priorityOnly && !warningsOnly && !unassignedOnly && !urgentOnly &&
                    !newAdmissionsOnly && taskFilter == null && searchQuery.isBlank(),
                filtersSelected = priorityOnly || warningsOnly || unassignedOnly || urgentOnly ||
                    newAdmissionsOnly || taskFilter != null || viewMode == WardViewMode.MINE || searchQuery.isNotBlank(),
                onAction = { action -> closeDrawer {
                    when (action) {
                        WardDrawerAction.ALL_PATIENTS -> applyDashboardFilter(DashboardFilter.ALL)
                        WardDrawerAction.REPORT -> state.shift?.id?.let(onOpenReport)
                        WardDrawerAction.ACTIVITY -> viewMode = WardViewMode.ACTIVITY
                        WardDrawerAction.SETTINGS -> onOpenSettings()
                        WardDrawerAction.DATA_MANAGEMENT -> onOpenDataManagement()
                        WardDrawerAction.DASHBOARD -> viewMode = WardViewMode.DASHBOARD
                        WardDrawerAction.MY_PATIENTS -> applyDashboardFilter(DashboardFilter.MINE)
                        WardDrawerAction.FILTERS -> showFilters = true
                        WardDrawerAction.SHIFT_DOCTORS -> if (!state.isReadOnly) showShiftDoctors = true
                        WardDrawerAction.START_SHIFT -> viewModel.offerPreviousShift()
                        WardDrawerAction.PREVIOUS -> confirmPrevious = true
                        WardDrawerAction.RECYCLE_BIN -> {
                            viewModel.loadRecycleBin()
                            showRecycleBin = true
                        }
                        WardDrawerAction.ADMIN -> onOpenAdmin()
                        WardDrawerAction.PROJECT -> onOpenProject()
                        WardDrawerAction.ABOUT -> onOpenAbout()
                    }
                } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                WardToolbarLayout(foldingFeature) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        WardToolbarContent(title = {
                            Text(state.hospitalName.ifBlank { stringResource(R.string.app_name) },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() }, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${uiNumber(state.shift?.date ?: "—")} · ${uiNumber(state.patients.size)} مريض",
                                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }, navigation = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "القائمة")
                            }
                        }, actions = {
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
                        })
                    }
                }
                WardWorkflowStrip(state, onNext = {
                    when (state.workflowAction) {
                        WardWorkflowAction.DELIVERY_HISTORY -> state.shift?.id?.let(onOpenReport)
                        WardWorkflowAction.REVIEW_PUBLICATION -> showReportSheet = true
                        WardWorkflowAction.DOWNLOAD -> confirmLatest = true
                    }
                }, onTasks = { if (!showAdd && editTarget == null && copyTarget == null) showWardTasks = true })
                }
            },
            floatingActionButton = {
                state.shift?.takeIf { !patientDestination || selectedPatient == null }?.let {
                    AppButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showReportSheet = true
                        },
                        contentPadding = UiPadding.content,
                        modifier = reportActionSpace.measure.heightIn(min = UiSpacing.touchTarget).semantics {
                            stateDescription = if (reportReadinessIssueCount == 0) "التقرير جاهز"
                                else "${uiNumber(reportReadinessIssueCount)} ملاحظات جاهزية"
                        }
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
                        Text(UiAction.SEND_REPORT.label, Modifier.padding(start = UiSpacing.small))
                        if (reportReadinessIssueCount > 0) {
                            Badge(modifier = Modifier.padding(start = UiSpacing.small),
                                containerColor = noticeColors(NoticeKind.WARNING).first,
                                contentColor = noticeColors(NoticeKind.WARNING).second
                            ) { Text(uiNumber(reportReadinessIssueCount)) }
                        }
                    }
                }
            },
            bottomBar = {
                if (!expandedWindow) {
                    WardToolbarLayout(foldingFeature) {
                        PrimaryNavigationBar(viewMode = viewMode, isAdmin = isAdmin,
                            onPatients = { viewMode = WardViewMode.ALL },
                            onDashboard = { viewMode = WardViewMode.DASHBOARD },
                            onActivity = { viewMode = WardViewMode.ACTIVITY })
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHost) }
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            // Draw behind the transparent pill gutters; reserve its height in scroll content instead.
            val navigationClearance = if (expandedWindow) 0.dp else padding.calculateBottomPadding()
            val scrollBottomClearance = reportActionSpace.bottomPadding + navigationClearance
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = if (expandedWindow) padding.calculateBottomPadding() else 0.dp
                    ))
            ) {
                if (expandedWindow) {
                    WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                        listFraction = .42f, onListFractionChange = {},
                        modifier = Modifier.width(UiLayout.navigationRailWidth).fillMaxHeight(), primaryTitle = "التنقل الرئيسي", primary = {
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
                        Box(Modifier.fillMaxSize().padding(bottom = navigationClearance)) {
                            if (selectedPatient != null && editTarget == null && copyTarget == null) detailContent(true)
                            else WardSupportingPane(state, freshnessLabel, online, isAdmin = isAdmin,
                                onReviewReport = { showReportSheet = true },
                                onActivity = { viewMode = WardViewMode.ACTIVITY })
                        }
                    },
                    compactOverlay = { if (patientDestination) detailContent(false) },
                    primary = { dualPane ->
                    Box(Modifier.fillMaxSize()) {
                        if (state.loading) {
                            com.hos.rushdpatients.ui.components.AppLoadingState("جار تحميل بيانات المناوبة…", Modifier.align(Alignment.Center))
                        } else if (state.error != null) {
                            AppNotice(
                                message = "تعذر تحميل بيانات المناوبة\n${state.error.orEmpty()}",
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
                                loading = state.activityLoading, error = state.activityError, bottomClearance = scrollBottomClearance
                            )
                        } else if (viewMode == WardViewMode.DASHBOARD) {
                            HandoverDashboard(
                                isAdmin = isAdmin,
                                patients = state.patients,
                                currentDoctorId = currentDoctorId,
                                syncStatus = state.syncStatus,
                            online = online, freshnessLabel = freshnessLabel,
                                nowEpochMillis = state.taskNowEpochMillis,
                                bottomClearance = scrollBottomClearance,
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
                                columns = if (dualPane) GridCells.Fixed(1) else GridCells.Adaptive(minSize = UiLayout.patientGridMinWidth),
                                state = patientGridState,
                                contentPadding = PaddingValues(bottom = scrollBottomClearance),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = UiSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(UiPatientCard.listGap),
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                        val activeFilters = buildList<String> {
                                            if (viewMode == WardViewMode.MINE) add("مرضاي")
                                            if (searchQuery.isNotBlank()) add("بحث: $searchQuery")
                                            if (priorityOnly) add("أولوية")
                                            if (warningsOnly) add("مع شارة")
                                            if (unassignedOnly) add("غير معيّن")
                                            if (urgentOnly) add("عاجل")
                                            if (newAdmissionsOnly) add("دخول اليوم")
                                            val taskLabel = when (taskFilter) {
                                                DashboardFilter.TASK_PENDING -> "مهام معلقة"
                                                DashboardFilter.TASK_OVERDUE -> "مهام متأخرة"
                                                DashboardFilter.TASK_UNASSIGNED -> "مهام غير معيّنة"
                                                else -> null
                                            }
                                            taskLabel?.let { add(it) }
                                        }
                                        val selectedPreset = when {
                                            activeFilters.isEmpty() -> DashboardFilter.ALL
                                            activeFilters.size != 1 -> null
                                            viewMode == WardViewMode.MINE -> DashboardFilter.MINE
                                            urgentOnly -> DashboardFilter.URGENT
                                            taskFilter == DashboardFilter.TASK_OVERDUE -> DashboardFilter.TASK_OVERDUE
                                            else -> null
                                        }
                                        WardPatientListControls(
                                            visibleCount = visiblePatients.size, totalCount = state.patients.size,
                                            groupingLabel = state.groupByMode.takeUnless { it == GroupByMode.NONE }?.arabicLabel,
                                            activeFilters = activeFilters, selectedPreset = selectedPreset,
                                            onPreset = ::applyDashboardFilter,
                                            onOptions = { showFilters = true }, onReset = ::resetPatientFilters,
                                            showSearch = showSearch, query = searchQuery,
                                            onQueryChange = { searchQuery = it }, requestFocus = requestSearchFocus,
                                            onFocusRequested = { requestSearchFocus = false }
                                        )
                                        if (state.isReadOnly) {
                                            AppNotice("نسخة محفوظة — للعرض والاستعادة فقط", icon = Icons.Filled.Lock)
                                        }
                                    }
                                }

                                if (visiblePatients.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyState(
                                            title = "لا توجد نتائج مطابقة",
                                            subtitle = "غيّر البحث أو استخدم «مسح التصفية» أعلى القائمة"
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
                modifier = Modifier.fillMaxWidth().appDialogBody(UiDialog.sheetMaxHeight), primary = {
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
        val insertionPatients = remember { state.patients.toList() }
        val insertionRevision = remember { state.shift?.revision }
        var insertionPosition by remember { mutableStateOf("") }
        AddPatientDialog(
            doctors = state.doctors,
            draft = state.patientDraft,
            saving = state.saving,
            position = insertionPosition,
            positionLimit = insertionPatients.size + 1,
            onPositionChange = { value -> insertionPosition = value.filter { it.isDigit() }.take(3) },
            onDraftChanged = viewModel::saveDraft,
            onConfirm = {
                val requested = insertionPosition.map { digit -> Character.digit(digit, 10) }.joinToString("").toIntOrNull()
                if (insertionPosition.isBlank() || requested != null) {
                    viewModel.addPatient(it, requested, insertionPatients, insertionRevision) { showAdd = false }
                }
            },
            onDismiss = { showAdd = false }
        )
    }

    if (state.rolloverPatients.isNotEmpty()) {
        RolloverReviewDialog(
            patients = state.rolloverPatients,
            saving = state.saving,
            sourceLabel = state.rolloverSourceLabel,
            onConfirm = viewModel::applyRollover,
            onDismiss = viewModel::dismissRollover
        )
    }

    if (confirmLatest) {
        ConfirmDialog(
            title = "جلب أحدث نسخة",
            message = "ستُستبدل بيانات المرضى المحلية بأحدث نسخة منشورة. راجع تعديلاتك المحلية قبل الجلب.",
            confirmText = "جلب النسخة",
            onConfirm = {
                confirmLatest = false
                viewModel.bringLatestData()
            },
            onDismiss = { confirmLatest = false }
        )
    }

    if (confirmPrevious) {
        ConfirmDialog(
            title = "جلب نسخة سابقة",
            message = "ستُستبدل البيانات المحلية بالنسخة التي سبقت أحدث نشر. تبقى النسخة المنشورة على تليجرام كما هي.",
            confirmText = "جلب النسخة السابقة",
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
        fun saveEdited(draft: Patient, onStale: (Patient?) -> Unit, advance: Boolean) {
            // Capture the displayed successor before saving can change filters/order.
            val nextId = if (advance) patientRoundsNavigation(roundsPatients.map { it.id }, p.id)?.nextId else null
            viewModel.updatePatient(draft, onStale) {
                val current = viewModel.state.value
                val next = nextId?.takeIf { !current.isReadOnly && current.shift?.id == state.shift?.id }
                    ?.let { id -> current.patients.firstOrNull { it.id == id && !it.isDeleted } }
                if (next != null) {
                    returnTarget = null
                    editTarget = next
                    if (detailsTargetId != null) detailsTargetId = next.id
                } else {
                    returnTarget = PatientReturnTarget(draft.id, draft.revision)
                    editTarget = null
                }
            }
        }
        key(p.id) {
            EditPatientDialog(
                patient = p,
                doctors = state.doctors,
                saving = state.saving,
                onConfirm = { draft, onStale -> saveEdited(draft, onStale, false) },
                onConfirmAndNext = if (state.saveAndNext &&
                    patientRoundsNavigation(roundsPatients.map { it.id }, p.id)?.nextId != null)
                    { draft, onStale -> saveEdited(draft, onStale, true) } else null,
                roundsNavigation = patientRoundsNavigation(roundsPatients.map { it.id }, p.id),
                saveAndNextEnabled = state.saveAndNext,
                onSaveAndNextChanged = viewModel::setSaveAndNext,
                onDismiss = { editTarget = null }
            )
        }
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
            title = "نقل إلى السلة",
            message = "سيُنقل ${p.name} إلى سلة المحذوفات. يمكنك استعادته من السلة.",
            confirmText = "نقل إلى السلة",
            destructive = true,
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

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) { showFilters = false; showSort = false } },
            title = {
                Column {
                    Text("خيارات العرض")
                    AppSectionIndex(sections = WardViewOption.entries.toList(), selected = selectedViewOption,
                        label = { it.label }, enabled = !state.saving, onSelect = {
                            selectedViewOption = it
                            scope.launch { viewOptionsScroll.scrollTo(0) }
                        })
                }
            },
            text = {
                Column(Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight)
                    .verticalScroll(viewOptionsScroll), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text("البحث والتصفية والتجميع للعرض فقط؛ الترتيب يُحفظ ويستخدم في التقارير وCSV. مرضى الأولوية أولاً.", style = MaterialTheme.typography.bodySmall)
                    if (selectedViewOption == WardViewOption.FILTERS) {
                        AppSearchField(query = searchQuery, onQueryChange = { searchQuery = it }, label = "بحث")
                        Text("تصفية · للعرض فقط", style = MaterialTheme.typography.titleSmall)
                        CompactFilterChip(viewMode == WardViewMode.MINE,
                            { viewMode = if (viewMode == WardViewMode.MINE) WardViewMode.ALL else WardViewMode.MINE },
                            "مرضاي ومهامي")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                            CompactFilterChip(priorityOnly, { priorityOnly = !priorityOnly }, "أولوية فقط")
                            CompactFilterChip(warningsOnly, { warningsOnly = !warningsOnly }, "مع شارة")
                            CompactFilterChip(unassignedOnly, { unassignedOnly = !unassignedOnly }, "غير معيّن")
                            CompactFilterChip(urgentOnly, { urgentOnly = !urgentOnly }, "عاجل")
                            CompactFilterChip(newAdmissionsOnly, { newAdmissionsOnly = !newAdmissionsOnly }, "دخول اليوم")
                            listOf(DashboardFilter.TASK_PENDING to "مهام معلقة",
                                DashboardFilter.TASK_OVERDUE to "مهام متأخرة",
                                DashboardFilter.TASK_UNASSIGNED to "مهام غير معيّنة").forEach { (value, label) ->
                                CompactFilterChip(selected = taskFilter == value,
                                    onClick = { taskFilter = if (taskFilter == value) null else value }, label = label)
                            }
                        }
                    }
                    if (selectedViewOption == WardViewOption.GROUPING) {
                        Text("تجميع · للعرض فقط", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                            com.hos.rushdpatients.domain.sort.GroupByMode.entries.forEach { mode ->
                                CompactFilterChip(state.groupByMode == mode,
                                    { viewModel.setGroupByMode(mode) }, mode.arabicLabel)
                            }
                        }
                    }
                    if (selectedViewOption == WardViewOption.CARDS) {
                        Text("بطاقات المرضى · تفضيلات الجهاز", style = MaterialTheme.typography.titleSmall)
                        AppToggleRow(title = "عرض التفاصيل", checked = state.patientDetailsExpanded,
                            onCheckedChange = ::setAllPatientsExpanded)
                        AppToggleRow(title = "بطاقة بعمودين عند توفر المساحة", checked = state.twoColumn,
                            onCheckedChange = viewModel::setTwoColumn)
                        AppToggleRow(title = "بطاقات مضغوطة", checked = state.compactCards,
                            onCheckedChange = viewModel::setCompactCards)
                    }
                    if (selectedViewOption == WardViewOption.SORT) {
                        Text("ترتيب · يُحفظ مع المناوبة", style = MaterialTheme.typography.titleSmall)
                        Text(sortReviewLabel(state.sortSpec), style = MaterialTheme.typography.bodySmall)
                        if (!state.isReadOnly) {
                            AppTextButton(onClick = { showSort = !showSort }, enabled = !state.saving) {
                                Text(if (showSort) "طي خيارات الترتيب" else "تعديل ترتيب المرضى")
                            }
                            if (showSort) PatientSortOptions(
                                initial = state.sortSpec,
                                initialRevision = state.shift?.revision ?: 0,
                                saving = state.saving,
                                onApply = { spec, revision, onStale ->
                                    viewModel.applySort(spec, revision, onStale) { showSort = false }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { showFilters = false; showSort = false },
                enabled = !state.saving) { Text("تم") } },
            dismissButton = {
                AppTextButton(onClick = ::resetPatientFilters) { Text("مسح عوامل التصفية") }
            }
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
