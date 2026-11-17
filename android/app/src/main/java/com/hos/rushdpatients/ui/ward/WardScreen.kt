package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiOpacity
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.appContentEntrance

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.AppSheet
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.settings.SettingsWorkspace
import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.rememberScrollState
import com.hos.rushdpatients.ui.components.rememberFloatingActionSpace
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WardScreen(isAdmin: Boolean, currentDoctorId: String, navigation: WardNavigation,
    viewModel: WardViewModel = hiltViewModel(), patientFilterIds: Set<String>? = null,
    reviewShiftId: String? = null, onBackToPreview: (() -> Unit)? = null) {
    val controller = remember(viewModel) { LiveWardController(viewModel) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel, reviewShiftId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) viewModel.onForeground(reviewShiftId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.onForeground(reviewShiftId)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    WardWorkspace(isAdmin, currentDoctorId, navigation, controller,
        patientFilterIds = patientFilterIds, onBackToPreview = onBackToPreview)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun WardWorkspace(
    isAdmin: Boolean, currentDoctorId: String, navigation: WardNavigation, viewModel: WardController,
    initialView: WardViewMode? = null,
    onViewChanged: (WardViewMode) -> Unit = {},
    drawerContent: (@Composable ((() -> Unit) -> Unit) -> Unit)? = null,
    patientFilterIds: Set<String>? = null, onBackToPreview: (() -> Unit)? = null
) {
    val dayLabel by viewModel.dayLabel.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(LocalWardDayLabel provides dayLabel) {
        WardWorkspaceContent(isAdmin, currentDoctorId, navigation, viewModel, initialView, onViewChanged, drawerContent, patientFilterIds, onBackToPreview)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun WardWorkspaceContent(
    isAdmin: Boolean, currentDoctorId: String, navigation: WardNavigation, viewModel: WardController,
    initialView: WardViewMode?, onViewChanged: (WardViewMode) -> Unit,
    drawerContent: (@Composable ((() -> Unit) -> Unit) -> Unit)?,
    patientFilterIds: Set<String>?, onBackToPreview: (() -> Unit)?
) {
    val onOpenReport = navigation.onOpenReport
    val onOpenDataManagement = navigation.onOpenDataManagement
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    var offlineFetchFailed by rememberSaveable { mutableStateOf(false) }
    var showBackOnline by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(online, state.retryAction, state.syncIssue) {
        if (!online && state.retryAction == WardRetryAction.FETCH_LATEST)
            offlineFetchFailed = true
        if (online && offlineFetchFailed) {
            offlineFetchFailed = false
            showBackOnline = true
        }
    }
    val patientIds = remember(state.patients) { state.patients.map { it.id } }
    val dayLabel = LocalWardDayLabel.current
    val filtersState = rememberSaveable(saver = WardFilters.Saver) { WardFilters(WardFilterOptions(viewMode = initialView ?: WardViewMode.ALL)) }
    var searchQuery by filtersState::searchQuery
    var viewMode by filtersState::viewMode
    var previousRequestedView by rememberSaveable { mutableStateOf(initialView) }
    LaunchedEffect(initialView) {
        if (initialView != null && previousRequestedView != initialView) {
            viewMode = initialView
            previousRequestedView = initialView
        }
    }
    LaunchedEffect(viewMode) { onViewChanged(viewMode) }
    val overlays = rememberSaveable(saver = WardOverlayState.Saver) { WardOverlayState() }
    val snackbarHost = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val foldingFeature = rememberWardFoldingFeature()
    val workspace = rememberWardWorkspaceState()
    var listFraction by workspace::listFraction
    var showSupportingSheet by overlays::showSupportingSheet
    val configuration = LocalConfiguration.current
    val expandedWindow = configuration.screenWidthDp >= UiLayout.expandedWindowWidthDp &&
        configuration.screenHeightDp >= UiLayout.expandedWindowMinHeightDp
    val freshnessLabel = state.freshnessLabel(uiText)
    val doctorNames = remember(state.doctors) {
        state.doctors.associate { it.id to it.fullName }
    }
    val patientGridState = rememberLazyGridState()
    val editor = viewModel.editor
    var returnTarget by workspace::returnTarget
    var recentlyEditedId by workspace::recentlyEditedId
    var confirmLatest by overlays::confirmLatest
    var showSearch by workspace::showSearch
    var requestSearchFocus by workspace::requestSearchFocus
    var showRecycleBin by overlays::showRecycleBin
    var collapsedGroups by workspace::collapsedGroups
    var showFilters by overlays::showFilters
    var selectedViewOption by workspace::selectedViewOption
    val viewOptionsScroll = rememberScrollState()
    // A bulk click owns the displayed mode immediately; preference IO only persists it.
    var bulkExpansionOverride by workspace::bulkExpansionOverride
    val allCardsExpanded = bulkExpansionOverride ?: state.patientDetailsExpanded
    var expandedPatientIds by workspace::expandedPatientIds
    var collapsedPatientIds by workspace::collapsedPatientIds
    var showWardTasks by overlays::showWardTasks
    var animatedPatientId by workspace::animatedPatientId

    val filters = filtersState.options()
    val searchIndex = remember(state.patients) { wardSearchIndex(state.patients) }
    val expandedIds = remember(expandedPatientIds) { expandedPatientIds.toSet() }
    val collapsedIds = remember(collapsedPatientIds) { collapsedPatientIds.toSet() }
    val anyCardsExpanded = remember(state.patients, allCardsExpanded, expandedIds, collapsedIds) {
        state.patients.any { patient -> if (allCardsExpanded) patient.id !in collapsedIds else patient.id in expandedIds }
    }
    val visiblePatients = remember(state.patients, filters, currentDoctorId, dayLabel, searchIndex, patientFilterIds) {
        filterWardPatients(state.patients, filters, currentDoctorId, dayLabel, emptySet(), searchIndex)
            .filter { patientFilterIds == null || it.id in patientFilterIds }
    }
    val presetCounts = remember(state.patients, currentDoctorId, dayLabel) {
        val summary = com.hos.rushdpatients.domain.patient.WardSummary.from(state.patients, currentDoctorId, dayLabel)
        mapOf(DashboardFilter.ALL to state.patients.size, DashboardFilter.MINE to summary.mine,
            DashboardFilter.URGENT to summary.urgent, DashboardFilter.TASK_PENDING to summary.tasks.pending,
            DashboardFilter.UNASSIGNED to summary.unassigned, DashboardFilter.WARNINGS to summary.warnings,
            DashboardFilter.NEW_ADMISSIONS to summary.newAdmissions)
    }
    val visibleIds = remember(visiblePatients) { visiblePatients.mapTo(mutableSetOf()) { it.id } }

    fun closeDrawer(after: () -> Unit = {}) {
        scope.launch {
            drawerState.close()
            after()
        }
    }

    val patientDestination = viewMode == WardViewMode.ALL || viewMode == WardViewMode.MINE
    val showAddFloatingAction = state.shift != null && !state.isReadOnly &&
        (!patientDestination || state.patients.isNotEmpty())
    val addActionSpace = rememberFloatingActionSpace(showAddFloatingAction)
    val patientScrollPositions = remember(state.groupByMode, state.groupedPatients, visiblePatients, visibleIds, collapsedGroups) {
        val displayed = if (state.groupByMode != GroupByMode.NONE && state.groupedPatients.isNotEmpty()) {
            state.groupedPatients.filter { (it.key ?: "none") !in collapsedGroups }
                .flatMap { group -> group.patients.filter { it.id in visibleIds } }
        } else visiblePatients
        displayed.mapIndexed { index, patient -> patient.id to index }.toMap()
    }
    val listScrolledDown by remember(patientGridState, patientScrollPositions) {
        derivedStateOf {
            val firstPatientId = patientGridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.contentType == "patient" }?.key as? String
            (firstPatientId?.let { patientScrollPositions[it] } ?: -1) >= UiLayout.backToTopPatientThreshold
        }
    }
    val showBackToTop = patientDestination && state.patients.isNotEmpty() && listScrolledDown
    val backToTopActionSpace = rememberFloatingActionSpace(showBackToTop)
    val floatingActionColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = UiOpacity.floatingContainer),
        contentColor = MaterialTheme.colorScheme.onPrimary)
    val roundsPatients = remember(state.groupByMode, state.groupedPatients, visiblePatients, visibleIds) {
        if (state.groupByMode != GroupByMode.NONE && state.groupedPatients.isNotEmpty()) {
            state.groupedPatients.flatMap { group ->
                group.patients.filter { it.id in visibleIds }
            }.distinctBy { it.id }
        } else visiblePatients
    }

    LaunchedEffect(returnTarget, state.patients, state.groupedPatients, visiblePatients, state.loading, state.saving, state.error, patientDestination) {
        val target = returnTarget ?: return@LaunchedEffect
        if (!patientDestination || state.loading || state.saving || state.error != null) return@LaunchedEffect
        val saved = state.patients.firstOrNull { it.id == target.id } ?: return@LaunchedEffect
        // Repository flow can arrive after the success callback. Reveal the saved revision.
        if (target.previousRevision != null && saved.revision <= target.previousRevision) return@LaunchedEffect
        if (visiblePatients.none { it.id == target.id }) {
            returnTarget = null
            scope.launch { snackbarHost.showSnackbar(uiText(R.string.ui_ward_screen_001, saved.name)) }
            return@LaunchedEffect
        }
        val groups = if (state.groupByMode == GroupByMode.NONE) emptyList() else state.groupedPatients.map { group ->
            (group.key ?: "none") to group.patients.filter { it.id in visibleIds }
                .map { it.id }
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
            delay(com.hos.rushdpatients.ui.theme.UiTiming.patientHighlightMillis)
            recentlyEditedId = null
        }
    }
    if (showWardTasks) WardTaskBoard(state.patients, state.doctors,
        snackbarHost = snackbarHost,
        editable = state.canMutate,
        onComplete = { entry ->
            viewModel.setTaskDone(entry.patient, entry.task.id, true) { saved ->
                scope.launch {
                    snackbarHost.currentSnackbarData?.dismiss()
                    val result = snackbarHost.showSnackbar(uiText(R.string.ui_task_completed),
                        actionLabel = uiText(R.string.ui_task_undo))
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.setTaskDone(saved, entry.task.id, false) { }
                    }
                }
            }
        },
        onDismiss = { showWardTasks = false }, onPatient = { id ->
            if (state.canInteract && editor.dialog == null && state.patients.any { it.id == id }) {
                showWardTasks = false
                filtersState.applyPreset(DashboardFilter.ALL)
                val patient = state.patients.first { it.id == id }
                if (state.isReadOnly) {
                    expandedPatientIds = (expandedPatientIds + id).distinct()
                    collapsedPatientIds = collapsedPatientIds - id
                    returnTarget = PatientReturnTarget(id)
                } else editor.editTarget = patient
            }
        })
    BackHandler(enabled = !patientDestination) { viewMode = WardViewMode.ALL }
    LaunchedEffect(viewMode) {
        if (viewMode == WardViewMode.ACTIVITY) viewMode = WardViewMode.ALL
    }

    fun applyDashboardFilter(filter: DashboardFilter) {
        if (filter == DashboardFilter.TASK_PENDING) showWardTasks = true
        else filtersState.applyPreset(filter)
    }

    fun resetPatientFilters() {
        searchQuery = ""
        applyDashboardFilter(DashboardFilter.ALL)
        focusManager.clearFocus()
    }

    com.hos.rushdpatients.ui.components.AppSnackbarEffect(state.snackbar, snackbarHost, viewModel::dismissSnackbar,
        actionLabel = if (state.retryAction != null) uiText(R.string.ui_retry) else null,
        onAction = {
            viewModel.retryLastAction()
        })

    LaunchedEffect(state.isReadOnly) {
        if (state.isReadOnly) {
            editor.showAdd = false
            editor.editTarget = null
            editor.deleteTarget = null
        }
    }

    LaunchedEffect(state.loading, patientIds) {
        if (state.loading) return@LaunchedEffect
        val activeIds = patientIds.toSet()
        expandedPatientIds = expandedPatientIds.filter { it in activeIds }
        collapsedPatientIds = collapsedPatientIds.filter { it in activeIds }
    }

    val patientCardContent: @Composable (Patient, Modifier) -> Unit = { patient, cardModifier ->
        WardPatientItem(patient, cardModifier, state, viewModel, workspace, allCardsExpanded,
            expandedIds, collapsedIds, doctorNames, snackbarHost)
    }

    val wardHeader: @Composable () -> Unit = {

                        WardHeader(state, viewMode, patientDestination, anyCardsExpanded, foldingFeature,
                            onMenu = { scope.launch { drawerState.open() } },
                            onToggleCards = { workspace.setAllPatientsExpanded(!anyCardsExpanded, viewModel::setPatientDetailsExpanded) },
                            showAddAction = !showAddFloatingAction, showDeliveryNotice = false,
                            onAdd = { editor.openAdd(state.patients) }, onSearch = {
                                if (patientDestination && showSearch && searchQuery.isBlank()) {
                                    showSearch = false
                                    requestSearchFocus = false
                                    focusManager.clearFocus()
                                } else {
                                    if (!patientDestination) viewMode = WardViewMode.ALL
                                    showSearch = true
                                    requestSearchFocus = true
                                }
                            }, onWorkflow = {
                                when (state.workflowAction) {
                                    WardWorkflowAction.DELIVERY_HISTORY -> state.shift?.id?.let(navigation.onOpenDeliveryHistory)
                                    WardWorkflowAction.REVIEW_PUBLICATION -> state.shift?.id?.let(onOpenReport)
                                    WardWorkflowAction.DOWNLOAD -> onOpenDataManagement()
                                }
                            })
    }
    val wardStatus: @Composable () -> Unit = {
                WardStatusHeader(
                    state = state, isAdmin = isAdmin, liveProject = viewModel.liveProject,
                    showBackOnline = showBackOnline,
                    fetchReminderEnabled = !state.loading && state.canInteract &&
                        !state.isReadOnly && editor.dialog == null,
                    onOpenDoctors = navigation.onOpenDoctors,
                    onReviewPatients = { state.shift?.id?.let(onOpenReport) },
                    onRetry = viewModel::retryLastAction, onReload = viewModel::reloadWard,
                    onFetch = { showBackOnline = false; confirmLatest = true },
                    onBackToPreview = onBackToPreview,
                    onWorkflow = {
                        when (state.workflowAction) {
                            WardWorkflowAction.DELIVERY_HISTORY -> state.shift?.id?.let(navigation.onOpenDeliveryHistory)
                            WardWorkflowAction.REVIEW_PUBLICATION -> state.shift?.id?.let(onOpenReport)
                            WardWorkflowAction.DOWNLOAD -> onOpenDataManagement()
                        }
                    },
                    header = {},
                    transferActions = {
                        if (viewModel.synchronizationAvailable && (patientDestination || viewMode == WardViewMode.DASHBOARD)) WardTransferBar(
                            fetchEnabled = !state.loading && !state.isReadOnly && !state.syncing && !state.saving &&
                                editor.dialog == null && !confirmLatest,
                            reportEnabled = state.shift != null && !state.isReadOnly && !state.loading && !state.syncing && !state.saving &&
                                editor.dialog == null,
                            onFetch = { confirmLatest = true },
                            uploadEnabled = state.shift != null && (!viewModel.liveProject || online) &&
                                !state.loading && state.canMutate &&
                                editor.dialog == null && !confirmLatest,
                            onUpload = viewModel::reviewPendingPublication,
                            onReport = { state.shift?.id?.let(onOpenReport) })
                    })
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val shortWindow = maxHeight < UiLayout.shortWindowHeight * com.hos.rushdpatients.ui.theme.uiLayoutScale()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            if (drawerContent != null) drawerContent(::closeDrawer) else WardDrawer(
                state = state,
                currentDoctorName = doctorNames[currentDoctorId],
                isAdmin = isAdmin,
                isPermanentAdmin = isAdmin && state.doctors.any { it.id == currentDoctorId && it.isPermanentAdmin && !it.isDeleted },
                online = online,
                onPublishData = { closeDrawer { viewModel.reviewPendingPublication() } },
                onAction = { action -> closeDrawer {
                    navigation.openDrawerAction(action, state.shift?.id, isAdmin,
                        onRecycleBin = { showRecycleBin = true })
                } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                androidx.compose.foundation.layout.Column {
                    wardHeader()
                    if (!shortWindow) wardStatus()
                }
            },
            floatingActionButton = {
                if (showAddFloatingAction) {
                    com.hos.rushdpatients.ui.components.AppFloatingAction(
                        icon = AppIcons.Add, label = uiText(R.string.ui_add_patient),
                        onClick = { editor.openAdd(state.patients) },
                        enabled = !state.loading && state.canInteract &&
                            !editor.showAdd && editor.editTarget == null && editor.copyTarget == null,
                        colors = floatingActionColors, modifier = addActionSpace.measure)
                }
            },
            bottomBar = {
                WardBottomNavigation(expandedWindow, foldingFeature, viewMode, isAdmin,
                    onDestination = { viewMode = it })
            },
            snackbarHost = { if (!showWardTasks) AppSnackbarHost(snackbarHost) }
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            // Draw behind the transparent pill gutters; reserve its height in scroll content instead.
            val navigationClearance = if (expandedWindow) com.hos.rushdpatients.ui.theme.UiSpace.none else padding.calculateBottomPadding()
            val scrollBottomClearance = maxOf(addActionSpace.bottomPadding, backToTopActionSpace.bottomPadding) + navigationClearance
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = if (expandedWindow) padding.calculateBottomPadding() else com.hos.rushdpatients.ui.theme.UiSpace.none
                    ))
            ) {
                if (expandedWindow) {
                    WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                        listFraction = UiLayout.defaultListFraction, onListFractionChange = {},
                        modifier = Modifier.width(UiLayout.navigationRailWidth).fillMaxHeight(), primaryTitle = uiText(R.string.ui_ward_screen_002), primary = {
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
                    allowMediumSupport = true,
                    foldingFeature = foldingFeature,
                    listFraction = listFraction,
                    onListFractionChange = { listFraction = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    primaryTitle = if (patientDestination) uiText(R.string.ui_handover_dashboard_004) else if (viewMode == WardViewMode.ACTIVITY) uiText(R.string.ui_ward_activity_screen_003) else uiText(R.string.ui_ward_screen_003),
                    supportingTitle = uiText(R.string.ui_ward_screen_004),
                    supporting = {
                        Box(Modifier.fillMaxSize().padding(bottom = navigationClearance)) {
                            WardSupportingPane(state, freshnessLabel, online, isAdmin = isAdmin,
                                showSynchronization = viewModel.synchronizationAvailable, onOpenSyncCenter = onOpenDataManagement,
                                onActivity = { viewMode = WardViewMode.ACTIVITY })
                        }
                    },
                    primary = { dualPane ->
                        Box(Modifier.fillMaxSize().appContentEntrance(viewMode)) {
                            if (state.loading && state.shift == null) {
                                com.hos.rushdpatients.ui.components.AppLoadingState(uiText(R.string.ui_ward_screen_006), Modifier.align(Alignment.Center))
                            } else if (state.error != null && state.shift == null) {
                                AppNotice(
                                    message = uiText(R.string.ui_ward_screen_007, state.error.orEmpty()),
                                    kind = NoticeKind.ERROR,
                                    modifier = Modifier.align(Alignment.Center).padding(UiPadding.content),
                                    actionLabel = uiText(R.string.ui_retry),
                                    onAction = viewModel::reloadWard,
                                    actionEnabled = !state.syncing && !state.saving
                                )
                            } else if (viewMode == WardViewMode.DASHBOARD) {
                                HandoverDashboard(
                                    isAdmin = isAdmin,
                                    onReportStatus = { state.shift?.id?.let(onOpenReport) },
                                    reportStatus = if (!viewModel.synchronizationAvailable) null else when {
                                        state.deliveryLogUnavailable -> uiText(R.string.dashboard_report_unknown)
                                        state.unresolvedDeliveries > 0 -> uiText(R.string.dashboard_report_uncertain)
                                        state.synchronization.lastReportAt != null -> uiText(R.string.dashboard_report_sent,
                                            com.hos.rushdpatients.ui.components.uiDateTime(java.time.Instant.ofEpochMilli(
                                                requireNotNull(state.synchronization.lastReportAt))))
                                        else -> uiText(R.string.dashboard_report_not_sent)
                                    },
                                    patients = state.patients,
                                    currentDoctorId = currentDoctorId,
                                    syncStatus = state.syncStatus,
                                    pendingCount = state.synchronization.pendingCount,
                                    online = online, freshnessLabel = freshnessLabel,
                                    dayLabel = dayLabel,
                                    bottomClearance = scrollBottomClearance,
                                    topContent = { if (shortWindow) wardStatus() },
                                    onFilter = ::applyDashboardFilter,
                                    onSync = onOpenDataManagement,
                                    onActivity = {
                                        viewMode = WardViewMode.ACTIVITY
                                    },
                                    modifier = Modifier.padding(UiPadding.screen), showSynchronization = viewModel.synchronizationAvailable
                                )
                            } else if (state.patients.isEmpty()) {
                                EmptyWardPatients(isReadOnly = state.isReadOnly,
                                    busy = state.syncing || state.saving,
                                    bottomClearance = scrollBottomClearance,
                                    onAdd = { editor.openAdd(state.patients) },
                                    topContent = { if (shortWindow) wardStatus() })
                            } else {
                                WardPatientListSection(state, visiblePatients, dualPane, patientGridState,
                                    scrollBottomClearance, collapsedGroups, filtersState, presetCounts,
                                    showSearch = showSearch, requestSearchFocus = requestSearchFocus,
                                    shortWindow = shortWindow, topContent = { if (shortWindow) wardStatus() },
                                    onToggleGroup = { key ->
                                        collapsedGroups = if (key in collapsedGroups) collapsedGroups - key else collapsedGroups + key
                                    }, onResetFilters = ::resetPatientFilters,
                                    onPreset = ::applyDashboardFilter, onOptions = { showFilters = true },
                                    onQueryChange = { searchQuery = it }, onFocusRequested = { requestSearchFocus = false },
                                    patientCard = patientCardContent)
                                if (showBackToTop) Box(
                                    Modifier.align(Alignment.BottomStart)
                                        .padding(start = UiSpacing.largeSection, bottom = navigationClearance + UiSpacing.largeSection)
                                ) {
                                    com.hos.rushdpatients.ui.components.AppFloatingAction(
                                        icon = AppIcons.ArrowUpward, label = uiText(R.string.ui_back_to_top),
                                        onClick = { scope.launch { patientGridState.scrollToItem(0) } },
                                        colors = floatingActionColors, modifier = backToTopActionSpace.measure)
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    }

    if (showSupportingSheet) {
        AppSheet(onDismissRequest = { showSupportingSheet = false }) {
            WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                listFraction = UiLayout.defaultListFraction, onListFractionChange = {},
                modifier = Modifier.fillMaxWidth().appDialogBody(UiDialog.sheetMaxHeight), primary = {
                WardSupportingPane(state, freshnessLabel, online, isAdmin = isAdmin,
                    showSynchronization = viewModel.synchronizationAvailable, onOpenSyncCenter = { showSupportingSheet = false; onOpenDataManagement() },
                    onActivity = { showSupportingSheet = false; viewMode = WardViewMode.ACTIVITY },
                    modifier = Modifier.fillMaxSize())
            })
        }
    }

    if (viewModel.synchronizationAvailable) WardDataReviewDialogs(state, viewModel, confirmLatest,
        onDismissLatest = { confirmLatest = false })

    WardEditorDialogs(state, viewModel, workspace, roundsPatients, snackbarHost)

    if (showFilters) WardViewOptionsDialog(state, viewModel,
        resultSummary = listOfNotNull(uiText(R.string.ui_ward_patient_list_controls_001,
            com.hos.rushdpatients.ui.components.uiNumber(visiblePatients.size), com.hos.rushdpatients.ui.components.uiNumber(state.patients.size)),
            state.groupByMode.takeUnless { it == GroupByMode.NONE }?.uiLabel(uiText)).joinToString(" • "),
        activeFilterLabels = filtersState.labels(uiText),
        filters = filters,
        onFiltersChanged = { transform -> filtersState.update(transform(filtersState.options()))
        }, selectedViewOption = selectedViewOption, viewOptionsScroll = viewOptionsScroll,
        onSelectOption = { selectedViewOption = it; scope.launch { viewOptionsScroll.scrollTo(0) } },
        onShowTasks = { showFilters = false; showWardTasks = true },
        onSetAllExpanded = { workspace.setAllPatientsExpanded(it, viewModel::setPatientDetailsExpanded) }, cardsExpanded = anyCardsExpanded,
        onResetFilters = ::resetPatientFilters,
        onClose = { showFilters = false })

    if (showRecycleBin) {
        androidx.compose.runtime.DisposableEffect(viewModel, state.shift?.id) {
            viewModel.loadRecycleBin()
            onDispose { viewModel.stopRecycleBinObservation() }
        }
        PatientRecycleBinDialog(
            patients = state.deletedPatients,
            loading = state.recycleBinLoading,
            loadFailed = state.recycleBinFailed,
            onRetry = viewModel::loadRecycleBin,
            readOnly = state.isReadOnly || state.saving || state.syncing,
            onRestore = viewModel::restorePatient,
            onDismiss = { showRecycleBin = false }
        )
    }

}
