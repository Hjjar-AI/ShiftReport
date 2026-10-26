package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppTextButton

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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    val detailStateHolder = rememberSaveableStateHolder()
    var listFraction by rememberSaveable { mutableStateOf(UiLayout.defaultListFraction) }
    var showSupportingSheet by overlays::showSupportingSheet
    val configuration = LocalConfiguration.current
    val expandedWindow = configuration.screenWidthDp >= UiLayout.expandedWindowWidthDp &&
        configuration.screenHeightDp >= UiLayout.expandedWindowMinHeightDp
    val freshnessLabel = state.freshnessLabel(uiText)
    val doctorNames = remember(state.doctors) {
        state.doctors.associate { it.id to it.fullName }
    }
    val patientGridState = rememberLazyGridState()
    val uiPreferences = com.hos.rushdpatients.ui.theme.LocalUiPreferences.current
    val editor = viewModel.editor
    var detailsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnTarget by remember { mutableStateOf<PatientReturnTarget?>(null) }
    var recentlyEditedId by remember { mutableStateOf<String?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var confirmLatest by overlays::confirmLatest
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var showRecycleBin by overlays::showRecycleBin
    var collapsedGroups by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showFilters by overlays::showFilters
    var selectedViewOption by rememberSaveable { mutableStateOf(WardViewOption.FILTERS) }
    val viewOptionsScroll = rememberScrollState()
    var expandedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var collapsedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showWardTasks by overlays::showWardTasks
    var animatedPatientId by remember { mutableStateOf<String?>(null) }
    fun setAllPatientsExpanded(expand: Boolean) {
        animatedPatientId = null
        val ids = patientIds
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

    val filters = filtersState.options()
    val searchIndex = remember(state.patients) { wardSearchIndex(state.patients) }
    val expandedIds = remember(expandedPatientIds) { expandedPatientIds.toSet() }
    val collapsedIds = remember(collapsedPatientIds) { collapsedPatientIds.toSet() }
    val anyCardsExpanded = remember(state.patients, state.patientDetailsExpanded, expandedIds, collapsedIds) {
        state.patients.any { patient -> if (state.patientDetailsExpanded) patient.id !in collapsedIds else patient.id in expandedIds }
    }
    fun toggleAllCards() = setAllPatientsExpanded(!anyCardsExpanded)
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
    val selectedPatient = state.patients.firstOrNull { it.id == detailsTargetId }
    val showAddFloatingAction = state.shift != null && !state.isReadOnly &&
        (!patientDestination || selectedPatient == null) &&
        (!patientDestination || state.patients.isNotEmpty())
    val addActionSpace = rememberFloatingActionSpace(showAddFloatingAction)
    val roundsPatients = remember(state.groupByMode, state.groupedPatients, visiblePatients, visibleIds) {
        if (state.groupByMode != GroupByMode.NONE && state.groupedPatients.isNotEmpty()) {
            state.groupedPatients.flatMap { group ->
                group.patients.filter { it.id in visibleIds }
            }.distinctBy { it.id }
        } else visiblePatients
    }
    val roundsNavigation = detailsTargetId?.let { patientRoundsNavigation(roundsPatients.map { it.id }, it) }

    LaunchedEffect(returnTarget, state.patients, state.groupedPatients, visiblePatients, state.loading, state.saving, state.error, patientDestination) {
        val target = returnTarget ?: return@LaunchedEffect
        if (!patientDestination || state.loading || state.saving || state.error != null) return@LaunchedEffect
        val saved = state.patients.firstOrNull { it.id == target.id } ?: return@LaunchedEffect
        // Repository flow can arrive after the success callback. Reveal the saved revision.
        if (saved.revision <= target.previousRevision) return@LaunchedEffect
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
            delay(1800)
            recentlyEditedId = null
        }
    }
    if (showWardTasks) WardTaskBoard(state.patients, state.doctors,
        snackbarHost = snackbarHost,
        editable = !state.isReadOnly && !state.saving && !state.syncing,
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
            if (!state.saving && !state.syncing && !editor.showAdd && editor.editTarget == null && editor.copyTarget == null && state.patients.any { it.id == id }) {
                showWardTasks = false
                filtersState.applyPreset(DashboardFilter.ALL)
                detailsTargetId = id
            }
        })
    val detailContent: @Composable (Boolean) -> Unit = { embedded ->
        if (selectedPatient != null && editor.editTarget == null && editor.copyTarget == null) {
            detailStateHolder.SaveableStateProvider("patient-details") {
                PatientDetailsScreen(
                    patient = selectedPatient, doctorNames = doctorNames,
                    readOnly = state.isReadOnly,
                    onEdit = { editor.editTarget = selectedPatient },
                    onCopy = { editor.copyTarget = selectedPatient },
                    onPriorityChange = { viewModel.setPriority(selectedPatient, it) },
                    onDismiss = { detailsTargetId = null }, embedded = embedded, foldingFeature = foldingFeature,
                    roundsNavigation = roundsNavigation,
                    navigationEnabled = !state.saving && !editor.showAdd,
                    onNavigateToPatient = { id ->
                        if (!state.saving && !editor.showAdd && editor.editTarget == null && editor.copyTarget == null && roundsPatients.any { it.id == id }) {
                            detailsTargetId = id
                        }
                    }
                )
            }
        }
    }
    BackHandler(enabled = !patientDestination) { viewMode = WardViewMode.ALL }
    BackHandler(enabled = patientDestination && detailsTargetId != null && editor.editTarget == null && editor.copyTarget == null) {
        detailsTargetId = null
    }
    LaunchedEffect(viewMode) {
        if (viewMode == WardViewMode.ACTIVITY) viewMode = WardViewMode.ALL
    }
    LaunchedEffect(state.loading, state.shift?.id, patientIds) {
        if (!state.loading && selectedPatient == null) detailsTargetId = null
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

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            val result = snackbarHost.showSnackbar(
                message = it,
                actionLabel = if (state.retryAction != null) uiText(R.string.ui_retry) else null
            )
            viewModel.dismissSnackbar()
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.retryLastAction()
            }
        }
    }

    LaunchedEffect(state.isReadOnly) {
        if (state.isReadOnly) {
            editor.showAdd = false
            editor.editTarget = null
            editor.deleteTarget = null
            showSort = false
        }
    }

    LaunchedEffect(state.loading, patientIds) {
        if (state.loading) return@LaunchedEffect
        val activeIds = patientIds.toSet()
        expandedPatientIds = expandedPatientIds.filter { it in activeIds }
        collapsedPatientIds = collapsedPatientIds.filter { it in activeIds }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            if (drawerContent != null) drawerContent(::closeDrawer) else WardDrawer(
                state = state,
                currentDoctorName = doctorNames[currentDoctorId],
                isAdmin = isAdmin,
                isPermanentAdmin = isAdmin && state.doctors.any { it.id == currentDoctorId && it.isPermanentAdmin && !it.isDeleted },
                online = online,
                onAction = { action -> closeDrawer {
                    navigation.openDrawerAction(action, state.shift?.id, isAdmin,
                        onRecycleBin = { showRecycleBin = true })
                } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    state.syncIssue?.let { issue ->
                        val recovery = state.syncRecovery
                        val doctorAction = recovery == WardSyncRecovery.PUBLISH_DOCTORS || recovery == WardSyncRecovery.FETCH_DOCTORS
                        AppNotice(message = when (recovery) {
                            WardSyncRecovery.PUBLISH_DOCTORS -> uiText(R.string.ui_recovery_doctors_publish)
                            WardSyncRecovery.FETCH_DOCTORS -> uiText(R.string.ui_recovery_doctors_fetch)
                            WardSyncRecovery.PUBLISH_PATIENTS -> uiText(R.string.ui_recovery_patients_publish)
                            null -> issue
                        } + if (doctorAction && !isAdmin) "\n" + uiText(R.string.ui_recovery_ask_admin) else "",
                            kind = NoticeKind.WARNING,
                            actionLabel = when {
                                doctorAction && isAdmin -> uiText(R.string.ui_recovery_open_doctors)
                                recovery == WardSyncRecovery.PUBLISH_PATIENTS -> uiText(R.string.ui_recovery_review_patients)
                                recovery == null && state.retryAction != null -> uiText(R.string.ui_retry)
                                else -> null
                            },
                            onAction = when {
                                doctorAction && isAdmin -> navigation.onOpenDoctors
                                recovery == WardSyncRecovery.PUBLISH_PATIENTS -> ({ state.shift?.id?.let(onOpenReport); Unit })
                                recovery == null && state.retryAction != null -> viewModel::retryLastAction
                                else -> null
                            }, actionEnabled = !state.syncing && !state.saving)
                        if (recovery != null) AppTextButton(onClick = viewModel::retryLastAction,
                            enabled = !state.syncing && !state.saving) {
                            Text(uiText(R.string.ui_recovery_retry_after_fix))
                        }
                    }
                WardHeader(state, viewMode, patientDestination, anyCardsExpanded, foldingFeature,
                    onMenu = { scope.launch { drawerState.open() } }, onToggleCards = ::toggleAllCards,
                    showAddAction = !showAddFloatingAction,
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
                            WardWorkflowAction.DELIVERY_HISTORY -> state.shift?.id?.let(onOpenReport)
                            WardWorkflowAction.REVIEW_PUBLICATION -> state.shift?.id?.let(onOpenReport)
                            WardWorkflowAction.DOWNLOAD -> onOpenDataManagement()
                        }
                    })
                onBackToPreview?.let { back -> AppTextButton(onClick = back) { Text(uiText(R.string.report_readiness_return)) } }
                if (viewModel.liveProject && showBackOnline) AppNotice(
                    uiText(R.string.back_online_fetch), actionLabel = uiText(R.string.ui_patient_fetch_reminder_003),
                    onAction = { showBackOnline = false; confirmLatest = true },
                    actionEnabled = !state.loading && !state.saving && !state.syncing && !state.isReadOnly && editor.dialog == null && detailsTargetId == null)
                if (viewModel.synchronizationAvailable && patientDestination) WardTransferBar(
                    fetchEnabled = !state.loading && !state.isReadOnly && !state.syncing && !state.saving &&
                        editor.dialog == null && detailsTargetId == null && !confirmLatest,
                    reportEnabled = state.shift != null && !state.isReadOnly && !state.loading && !state.syncing && !state.saving &&
                        editor.dialog == null && detailsTargetId == null,
                    onFetch = { confirmLatest = true },
                    onReport = { state.shift?.id?.let(onOpenReport) })
                if (state.shift != null && state.loading) com.hos.rushdpatients.ui.components.AppLoadingState(
                    uiText(R.string.ui_ward_screen_006),
                    size = com.hos.rushdpatients.ui.components.AppLoadingSize.INLINE)
                if (state.shift != null && state.error != null) AppNotice(
                    message = uiText(R.string.ui_ward_refresh_retained, state.error.orEmpty()),
                    kind = NoticeKind.ERROR, compact = true,
                    actionLabel = uiText(R.string.ui_retry), onAction = viewModel::reloadWard,
                    actionEnabled = !state.loading && !state.syncing && !state.saving)
                if (state.syncing || state.exportingCsv) com.hos.rushdpatients.ui.components.AppLoadingState(
                    uiText(if (state.syncing) state.syncProgressRes else R.string.ui_progress_export_csv),
                    modifier = Modifier.padding(horizontal = UiSpacing.section),
                    size = com.hos.rushdpatients.ui.components.AppLoadingSize.INLINE)
                if (!viewModel.liveProject) AppNotice(uiText(R.string.ui_demo_navigation_001),
                    kind = NoticeKind.WARNING, icon = AppIcons.Info,
                    modifier = Modifier.fillMaxWidth().padding(UiPadding.screen))
                }
            },
            floatingActionButton = {
                if (showAddFloatingAction) {
                    AppButton(
                        onClick = { editor.openAdd(state.patients) },
                        enabled = !state.loading && !state.saving && !state.syncing &&
                            !editor.showAdd && editor.editTarget == null && editor.copyTarget == null,
                        contentPadding = UiPadding.content,
                        modifier = addActionSpace.measure
                            .sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget)
                            .semantics { contentDescription = uiText(R.string.ui_add_patient) }
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
                        if (!uiPreferences.compactAddAction) Text(uiText(R.string.ui_add_patient), Modifier.padding(start = UiSpacing.small))
                    }
                }
            },
            bottomBar = {
                // Read scroll-driven visibility in the dock scope, not the clinical workspace.
                val navigationVisible = rememberWardNavigationVisible(patientGridState,
                    uiPreferences.autoHideNavigation && patientDestination &&
                        !expandedWindow && state.patients.isNotEmpty(),
                    state.shift?.id to viewMode)
                var navigationHeightPx by remember { mutableStateOf(0) }
                val navigationShift by animateFloatAsState(if (navigationVisible) 0f else 1f,
                    animationSpec = tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.placementMillis),
                    label = "wardNavigationShift")
                Box(Modifier.onSizeChanged { navigationHeightPx = it.height }
                    .graphicsLayer { translationY = navigationShift * navigationHeightPx }
                    .then(if (navigationVisible) Modifier else Modifier.clearAndSetSemantics { })) {
                    WardBottomNavigation(expandedWindow, foldingFeature, viewMode, isAdmin,
                        onDestination = { viewMode = it })
                }
            },
            snackbarHost = { if (!showWardTasks) AppSnackbarHost(snackbarHost) }
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            // Draw behind the transparent pill gutters; reserve its height in scroll content instead.
            val navigationClearance = if (expandedWindow) com.hos.rushdpatients.ui.theme.UiSpace.none else padding.calculateBottomPadding()
            val scrollBottomClearance = addActionSpace.bottomPadding + navigationClearance
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
                    allowMediumSupport = selectedPatient == null,
                    foldingFeature = foldingFeature,
                    listFraction = listFraction,
                    onListFractionChange = { listFraction = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    primaryTitle = if (patientDestination) uiText(R.string.ui_handover_dashboard_004) else if (viewMode == WardViewMode.ACTIVITY) uiText(R.string.ui_ward_activity_screen_003) else uiText(R.string.ui_ward_screen_003),
                    supportingTitle = if (selectedPatient == null) uiText(R.string.ui_ward_screen_004) else uiText(R.string.ui_ward_screen_005),
                    supporting = {
                        Box(Modifier.fillMaxSize().padding(bottom = navigationClearance)) {
                            if (selectedPatient != null && editor.editTarget == null && editor.copyTarget == null) detailContent(true)
                            else WardSupportingPane(state, freshnessLabel, online, isAdmin = isAdmin,
                                showSynchronization = viewModel.synchronizationAvailable, onOpenSyncCenter = onOpenDataManagement,
                                onActivity = { viewMode = WardViewMode.ACTIVITY })
                        }
                    },
                    compactOverlay = { if (patientDestination) detailContent(false) },
                    primary = { dualPane ->
                    Box(Modifier.fillMaxSize()) {
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
                                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(
                                            requireNotNull(state.synchronization.lastReportAt)).atZone(java.time.ZoneId.systemDefault())))
                                    else -> uiText(R.string.dashboard_report_not_sent)
                                },
                                patients = state.patients,
                                currentDoctorId = currentDoctorId,
                                syncStatus = state.syncStatus,
                                pendingCount = state.synchronization.pendingCount,
                            online = online, freshnessLabel = freshnessLabel,
                                dayLabel = dayLabel,
                                bottomClearance = scrollBottomClearance,
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
                                topContent = {})
                        } else {
                            WardPatientGrid(state, visiblePatients, dualPane, patientGridState,
                                scrollBottomClearance, emptyList(), collapsedGroups,
                                onToggleGroup = { key ->
                                    collapsedGroups = if (key in collapsedGroups) collapsedGroups - key else collapsedGroups + key
                                }, onResetFilters = ::resetPatientFilters,
                                controlsContent = {
                                    if (state.isReadOnly) AppNotice(uiText(R.string.ui_ward_screen_013), icon = AppIcons.Lock, compact = true)
                                    androidx.compose.material3.Surface {
                                        Column(Modifier.padding(horizontal = UiSpacing.screenGutter)) {
                                            val activeFilters = filtersState.labels(uiText)
                                            WardPatientListControls(
                                                visibleCount = visiblePatients.size, totalCount = state.patients.size, presetCounts = presetCounts,
                                                groupingLabel = state.groupByMode.takeUnless { it == GroupByMode.NONE }?.uiLabel(uiText),
                                                activeFilters = activeFilters, selectedPreset = filtersState.selectedPreset,
                                                onPreset = ::applyDashboardFilter,
                                                onOptions = { showFilters = true }, onReset = ::resetPatientFilters,
                                                showSearch = showSearch, query = searchQuery,
                                                onQueryChange = { searchQuery = it }, requestFocus = requestSearchFocus,
                                                onFocusRequested = { requestSearchFocus = false }
                                            )
                                        }
                                    }
                                },
                                headerContent = {}, patientCard = { patient, cardModifier ->
                                    PatientCard(
        modifier = cardModifier,
        state = PatientCardState(patient = patient,
            expanded = if (state.patientDetailsExpanded) patient.id !in collapsedIds else patient.id in expandedIds,
            animateExpansion = animatedPatientId == patient.id,
            twoColumn = state.twoColumn,
            doctorNames = doctorNames,
            readOnly = state.isReadOnly || state.saving || state.syncing,
            selected = detailsTargetId == patient.id,
            recentlyEdited = recentlyEditedId == patient.id,
            pinned = false,
            compact = state.compactCards),
        actions = remember(patient, state.patientDetailsExpanded, state.isReadOnly, state.saving, state.syncing, viewModel, editor, haptics) {
            PatientCardActions(
            onOpen = {
                                                                                        detailsTargetId = patient.id
                                                                                    },
            onLongPress = {
                if (!state.isReadOnly && !state.saving && !state.syncing && editor.dialog == null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    editor.editTarget = patient
                }
            },
            onEdit = {
                if (!state.isReadOnly && !state.saving && !state.syncing && editor.dialog == null) {
                    editor.editTarget = patient
                }
            },
            onDelete = { editor.deleteTarget = patient },
            onCopy = { editor.copyTarget = patient },
            onPriorityChange = { priority ->
                viewModel.setPriority(patient, priority) {
                    scope.launch {
                        snackbarHost.currentSnackbarData?.dismiss()
                        if (snackbarHost.showSnackbar(uiText(R.string.priority_changed),
                                actionLabel = uiText(R.string.ui_task_undo)) == androidx.compose.material3.SnackbarResult.ActionPerformed)
                            viewModel.setPriority(patient, patient.isPriority)
                    }
                }
            },
            onPinToggle = {
                if (!state.isReadOnly && !state.saving && !state.syncing && editor.dialog == null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.movePatientToTop(patient)
                }
            },
            onExpandToggle = { toggleCard(patient.id) }
        )
        }
    )
                                })
                        }
                    }
                    }
                )
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

    if (editor.showAdd) {
        val insertionPatients = editor.insertionPatients
        AddPatientDialog(
            doctors = state.doctors,
            draft = state.patientDraft,
            stateStore = editor.addForm,
            draftWarning = state.draftSaveIssue?.takeIf { it.patientId == null }?.message,
            saving = state.saving,
            position = editor.insertionPosition,
            positionLimit = insertionPatients.size + 1,
            onPositionChange = { value -> editor.insertionPosition = value.filter { it.isDigit() }.take(3) },
            onDraftChanged = viewModel::saveDraft,
            onConfirm = {
                val requested = editor.insertionPosition.map { digit -> Character.digit(digit, 10) }.joinToString("").toIntOrNull()
                if (editor.insertionPosition.isBlank() || requested != null) {
                    viewModel.addPatient(it, requested) { editor.showAdd = false }
                }
            },
            onDismiss = { editor.showAdd = false }
        )
    }

    if (viewModel.synchronizationAvailable) WardDataReviewDialogs(state, viewModel, confirmLatest,
        onDismissLatest = { confirmLatest = false })

    fun saveEdited(p: Patient, draft: Patient, onUnavailable: (Patient?) -> Unit, advance: Boolean) {
        // Capture the displayed successor before saving can change filters/order.
        val nextId = if (advance) patientRoundsNavigation(roundsPatients.map { it.id }, p.id)?.nextId else null
        viewModel.updatePatient(draft, onUnavailable) {
            val current = viewModel.state.value
            val next = nextId?.takeIf { !current.isReadOnly && current.shift?.id == state.shift?.id }
                ?.let { id -> current.patients.firstOrNull { it.id == id && !it.isDeleted } }
            if (next != null) {
                returnTarget = null
                editor.editTarget = next
                if (detailsTargetId != null) detailsTargetId = next.id
            } else {
                returnTarget = PatientReturnTarget(draft.id, draft.revision)
                editor.editTarget = null
            }
        }
    }
    WardPatientDialogs(state, editor.editTarget, editor.copyTarget, editor.deleteTarget, roundsPatients.map { it.id },
        onLoadEditDraft = viewModel::loadEditDraft, onEditDraftChanged = viewModel::saveEditDraft,
        onDiscardEditDraft = viewModel::discardEditDraft,
        editSession = editor.editSession, copyInitial = editor.copyInitial, copyForm = editor.copyForm,
        onSaveEdited = ::saveEdited,
        onDismissEdit = { editor.editTarget = null },
        onCopyConfirmed = { patient -> viewModel.addPatient(patient) { editor.copyTarget = null } },
        onDismissCopy = { editor.copyTarget = null }, onDeleteConfirmed = { p ->
            viewModel.deletePatient(p) { deletedPatient ->
                editor.deleteTarget = null
                scope.launch {
                    val result = snackbarHost.showSnackbar(
                        message = uiText(R.string.ui_ward_screen_014, p.name),
                        actionLabel = uiText(R.string.ui_ward_screen_015)
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.restorePatient(deletedPatient)
                    }
                }
            }
        }, onDismissDelete = { editor.deleteTarget = null })

    if (showFilters) WardViewOptionsDialog(state, viewModel,
        resultSummary = listOfNotNull(uiText(R.string.ui_ward_patient_list_controls_001,
            com.hos.rushdpatients.ui.components.uiNumber(visiblePatients.size), com.hos.rushdpatients.ui.components.uiNumber(state.patients.size)),
            state.groupByMode.takeUnless { it == GroupByMode.NONE }?.uiLabel(uiText)).joinToString(" · "),
        activeFilterLabels = filtersState.labels(uiText),
        filters = filters,
        onFiltersChanged = { transform -> filtersState.update(transform(filtersState.options()))
        }, selectedViewOption = selectedViewOption, viewOptionsScroll = viewOptionsScroll,
        showSort = showSort, onSortExpandedChange = { showSort = it },
        onSelectOption = { selectedViewOption = it; scope.launch { viewOptionsScroll.scrollTo(0) } },
        onShowTasks = { showFilters = false; showWardTasks = true },
        onSetAllExpanded = ::setAllPatientsExpanded, onResetFilters = ::resetPatientFilters,
        onClose = { showFilters = false; showSort = false })

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
