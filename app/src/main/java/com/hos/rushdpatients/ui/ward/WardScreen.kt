package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.R
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.domain.report.ReportReadiness
import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.sync.ConflictChoice
import com.hos.rushdpatients.ui.theme.LocalClinicalColors
import com.hos.rushdpatients.util.NetworkStatus
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
    val snackbarHost = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val expandedWindow = LocalConfiguration.current.screenWidthDp >= 600
    val clinicalColors = LocalClinicalColors.current
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
    var copyTarget by remember { mutableStateOf<Patient?>(null) }
    var deleteTarget by remember { mutableStateOf<Patient?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showGroupBy by remember { mutableStateOf(false) }
    var showShiftDoctors by remember { mutableStateOf(false) }
    var confirmLatest by remember { mutableStateOf(false) }
    var confirmPrevious by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showRecycleBin by remember { mutableStateOf(false) }
    var wardMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var dataMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var appMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(WardViewMode.ALL) }
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    var priorityOnly by remember { mutableStateOf(false) }
    var warningsOnly by remember { mutableStateOf(false) }
    var unassignedOnly by remember { mutableStateOf(false) }
    var urgentOnly by remember { mutableStateOf(false) }
    var newAdmissionsOnly by remember { mutableStateOf(false) }
    var rolloverDecisions by remember { mutableStateOf(emptyMap<String, RolloverDecision>()) }
    var showActivity by remember { mutableStateOf(false) }
    var showReportSheet by rememberSaveable { mutableStateOf(false) }
    var expandedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pinnedPatientIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var conflictChoices by remember { mutableStateOf(emptyMap<String, ConflictChoice>()) }
    var activityQuery by remember { mutableStateOf("") }
    var activityFilter by remember { mutableStateOf(ActivityFilter.ALL) }

    val visiblePatients = remember(
        state.patients, searchQuery, viewMode, currentDoctorId,
        priorityOnly, warningsOnly, unassignedOnly, urgentOnly, newAdmissionsOnly,
        pinnedPatientIds
    ) {
        state.patients.filter { patient ->
            val matchesOwner = viewMode != WardViewMode.MINE ||
                patient.responsibleResidentId == currentDoctorId ||
                patient.responsibleSpecialistId == currentDoctorId
            matchesOwner && (!priorityOnly || patient.isPriority) &&
                (!warningsOnly || patient.badgeText.isNotBlank()) &&
                (!unassignedOnly || patient.responsibleResidentId == null || patient.responsibleSpecialistId == null) &&
                (!urgentOnly || patient.isPriority ||
                    patient.badgePriority == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH) &&
                (!newAdmissionsOnly || patient.admittanceDays == 0) &&
                ArabicSearchNormalizer.matches(
                searchQuery,
                patient.name,
                patient.admittanceNumber,
                patient.initialDiagnosis,
                patient.treatmentPlan,
                patient.followUp,
                patient.labs
            )
        }.sortedByDescending { it.id in pinnedPatientIds }
    }

    fun closeDrawer(after: () -> Unit = {}) {
        scope.launch {
            drawerState.close()
            after()
        }
    }

    fun applyDashboardFilter(filter: DashboardFilter) {
        priorityOnly = false
        warningsOnly = false
        unassignedOnly = false
        urgentOnly = false
        newAdmissionsOnly = false
        viewMode = WardViewMode.ALL
        when (filter) {
            DashboardFilter.ALL -> Unit
            DashboardFilter.MINE -> viewMode = WardViewMode.MINE
            DashboardFilter.URGENT -> urgentOnly = true
            DashboardFilter.WARNINGS -> warningsOnly = true
            DashboardFilter.UNASSIGNED -> unassignedOnly = true
            DashboardFilter.NEW_ADMISSIONS -> newAdmissionsOnly = true
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
    }

    LaunchedEffect(state.rolloverReviewPatients, editTarget) {
        if (editTarget == null) editTarget = state.rolloverReviewPatients.firstOrNull()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = state.hospitalName.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (state.hospitalName.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = buildString {
                            append(doctorNames[currentDoctorId] ?: "مستخدم غير محدد")
                            append(if (isAdmin) " · مدير" else " · عضو الفريق")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.shift?.date ?: "—"} · ${state.patients.size} مريض · ${state.syncStatus.arabicLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.mergeConflicts.isNotEmpty()) {
                        Text(
                            "${state.mergeConflicts.size} تعارض يحتاج إجراء",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Divider()
                DrawerSubmenuHeader(
                    label = "المناوبة والمرضى",
                    expanded = wardMenuExpanded,
                    onClick = {
                        wardMenuExpanded = !wardMenuExpanded
                        if (wardMenuExpanded) {
                            dataMenuExpanded = false
                            appMenuExpanded = false
                        }
                    }
                )
                if (wardMenuExpanded) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
                        label = { Text("جميع المرضى") },
                        selected = viewMode == WardViewMode.ALL &&
                            !priorityOnly && !warningsOnly && !unassignedOnly &&
                            !urgentOnly && !newAdmissionsOnly && searchQuery.isBlank(),
                        onClick = { closeDrawer { applyDashboardFilter(DashboardFilter.ALL) } },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                        label = { Text("لوحة تسليم المناوبة") },
                        selected = viewMode == WardViewMode.DASHBOARD,
                        onClick = { closeDrawer { viewMode = WardViewMode.DASHBOARD } },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("مرضاي") },
                        selected = viewMode == WardViewMode.MINE,
                        onClick = { closeDrawer { viewMode = WardViewMode.MINE } },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                        label = { Text("بحث وتصفية وترتيب") },
                        selected = priorityOnly || warningsOnly || unassignedOnly || urgentOnly ||
                            newAdmissionsOnly || searchQuery.isNotBlank(),
                        onClick = { closeDrawer { showFilters = true } },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Sort, contentDescription = null) },
                        label = { Text("ترتيب المرضى") },
                        selected = false,
                        onClick = { if (!state.isReadOnly) closeDrawer { showSort = true } },
                        modifier = Modifier.padding(horizontal = 22.dp),
                        colors = disabledDrawerColors(state.isReadOnly)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
                        label = { Text("تجميع المرضى") },
                        selected = state.groupByMode != GroupByMode.NONE,
                        onClick = { closeDrawer { showGroupBy = true } },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                        label = { Text("أطباء المناوبة") },
                        selected = false,
                        onClick = { if (!state.isReadOnly) closeDrawer { showShiftDoctors = true } },
                        modifier = Modifier.padding(horizontal = 22.dp),
                        colors = disabledDrawerColors(state.isReadOnly)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DrawerSubmenuHeader(
                    label = "التقارير والبيانات",
                    expanded = dataMenuExpanded,
                    onClick = {
                        dataMenuExpanded = !dataMenuExpanded
                        if (dataMenuExpanded) {
                            wardMenuExpanded = false
                            appMenuExpanded = false
                        }
                    }
                )
                if (dataMenuExpanded) {
                    if (isAdmin) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                            label = { Text("لوحة المدير") },
                            selected = false,
                            onClick = { closeDrawer { onOpenAdmin() } },
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                            label = { Text("استيراد بيانات من ملف") },
                            selected = false,
                            onClick = { closeDrawer { onOpenVbaImport() } },
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                    }
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("مركز النشاط والسجل") },
                        selected = false,
                        onClick = {
                            closeDrawer {
                                viewModel.loadRecentActivity()
                                showActivity = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                        label = { Text("معاينة وإرسال التقرير") },
                        selected = false,
                        onClick = {
                            val id = state.shift?.id ?: return@NavigationDrawerItem
                            closeDrawer { onOpenReport(id) }
                        },
                        modifier = Modifier.padding(horizontal = 22.dp),
                        colors = disabledDrawerColors(state.shift == null)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                        label = { Text("حفظ بيانات المرضى CSV") },
                        selected = false,
                        onClick = { closeDrawer { viewModel.exportCsv() } },
                        modifier = Modifier.padding(horizontal = 22.dp),
                        colors = disabledDrawerColors(state.exportingCsv)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Recycling, contentDescription = null) },
                        label = { Text("سلة المحذوفات") },
                        selected = false,
                        onClick = {
                            closeDrawer {
                                viewModel.loadRecycleBin()
                                showRecycleBin = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DrawerSubmenuHeader(
                    label = "التطبيق",
                    expanded = appMenuExpanded,
                    onClick = {
                        appMenuExpanded = !appMenuExpanded
                        if (appMenuExpanded) {
                            wardMenuExpanded = false
                            dataMenuExpanded = false
                        }
                    }
                )
                if (appMenuExpanded) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("الإعدادات") },
                        selected = false,
                        onClick = { closeDrawer { onOpenSettings() } },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        label = { Text("حول التطبيق") },
                        selected = false,
                        onClick = { closeDrawer { onOpenAbout() } },
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
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
                                text = "${state.shift?.date ?: "—"} · ${state.patients.size} مريض · $freshnessLabel",
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
                        if (!state.isReadOnly) {
                            IconButton(onClick = { showAdd = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "إضافة مريض")
                            }
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Filled.Search, contentDescription = "بحث عن مريض")
                        }
                        if (state.syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                state.shift?.let { shift ->
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showReportSheet = true
                        },
                        icon = { Icon(Icons.Filled.Send, contentDescription = null) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    if (reportReadinessIssueCount == 0) {
                                        "التقرير جاهز للإرسال"
                                    } else {
                                        "مراجعة وإرسال التقرير"
                                    }
                                )
                                if (reportReadinessIssueCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) { Text(reportReadinessIssueCount.toString()) }
                                }
                            }
                        },
                        expanded = true,
                        modifier = Modifier.semantics {
                            stateDescription = if (reportReadinessIssueCount == 0) {
                                "التقرير جاهز"
                            } else {
                                "$reportReadinessIssueCount ملاحظات جاهزية"
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            bottomBar = {
                if (!expandedWindow) {
                    PrimaryNavigationBar(
                        viewMode = viewMode,
                        onPatients = { viewMode = WardViewMode.ALL },
                        onDashboard = { viewMode = WardViewMode.DASHBOARD },
                        onActivity = {
                            viewModel.loadRecentActivity()
                            showActivity = true
                        }
                    )
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
                    PrimaryNavigationRail(
                        viewMode = viewMode,
                        onPatients = { viewMode = WardViewMode.ALL },
                        onDashboard = { viewMode = WardViewMode.DASHBOARD },
                        onActivity = {
                            viewModel.loadRecentActivity()
                            showActivity = true
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (state.error != null) {
                    EmptyState(
                        title = "تعذر تحميل بيانات الوردية",
                        subtitle = state.error.orEmpty()
                    )
                } else if (viewMode == WardViewMode.DASHBOARD) {
                    HandoverDashboard(
                        patients = state.patients,
                        currentDoctorId = currentDoctorId,
                        syncStatus = state.syncStatus,
                        onFilter = ::applyDashboardFilter,
                        onSync = { confirmLatest = true },
                        onActivity = {
                            viewModel.loadRecentActivity()
                            showActivity = true
                        },
                        modifier = Modifier.padding(12.dp)
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
                        columns = GridCells.Adaptive(minSize = 360.dp),
                        state = patientGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${visiblePatients.size} مريض • تاريخ المناوبة: " +
                                            (state.shift?.date ?: ""),
                                    modifier = Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val syncColors = when (state.syncStatus) {
                                    PatientSyncStatus.LOCAL -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                                    PatientSyncStatus.PENDING -> clinicalColors.pendingContainer to clinicalColors.onPendingContainer
                                    PatientSyncStatus.BACKED_UP -> clinicalColors.successContainer to clinicalColors.onSuccessContainer
                                    PatientSyncStatus.SYNCING -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                                    PatientSyncStatus.CONFLICT -> clinicalColors.urgentContainer to clinicalColors.onUrgentContainer
                                }
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            snackbarHost.showSnackbar(
                                                state.lastOperation ?: state.syncStatus.arabicLabel
                                            )
                                        }
                                    },
                                    label = { Text(state.syncStatus.arabicLabel) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (state.syncStatus) {
                                                PatientSyncStatus.LOCAL -> Icons.Filled.Save
                                                PatientSyncStatus.PENDING -> Icons.Filled.CloudUpload
                                                PatientSyncStatus.BACKED_UP -> Icons.Filled.CloudDone
                                                PatientSyncStatus.SYNCING -> Icons.Filled.Sync
                                                PatientSyncStatus.CONFLICT -> Icons.Filled.Warning
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = syncColors.first,
                                        labelColor = syncColors.second,
                                        leadingIconContentColor = syncColors.second
                                    ),
                                    modifier = Modifier.semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    }
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TextButton(
                                        onClick = { confirmLatest = true },
                                        enabled = !state.syncing
                                    ) { Text("مزامنة") }
                                    TextButton(
                                        onClick = { confirmPrevious = true },
                                        enabled = !state.syncing
                                    ) { Text("نسخة سابقة") }
                                    TextButton(onClick = { showFilters = true }) { Text("تصفية") }
                                    TextButton(onClick = viewModel::togglePatientDetails) {
                                        Text(if (state.patientDetailsExpanded) "طيّ" else "تفاصيل")
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = viewMode == WardViewMode.ALL && !urgentOnly &&
                                            !unassignedOnly && !warningsOnly && !priorityOnly &&
                                            !newAdmissionsOnly && searchQuery.isBlank(),
                                        onClick = { applyDashboardFilter(DashboardFilter.ALL) },
                                        label = { Text("الكل") }
                                    )
                                    FilterChip(
                                        selected = viewMode == WardViewMode.MINE,
                                        onClick = { applyDashboardFilter(DashboardFilter.MINE) },
                                        label = { Text("مرضاي") }
                                    )
                                    FilterChip(
                                        selected = urgentOnly,
                                        onClick = { applyDashboardFilter(DashboardFilter.URGENT) },
                                        label = { Text("بحاجة لانتباه") }
                                    )
                                    FilterChip(
                                        selected = unassignedOnly,
                                        onClick = { applyDashboardFilter(DashboardFilter.UNASSIGNED) },
                                        label = { Text("غير معيّن") }
                                    )
                                    FilterChip(
                                        selected = state.compactCards,
                                        onClick = { viewModel.setCompactCards(!state.compactCards) },
                                        label = { Text(if (state.compactCards) "كثافة مضغوطة" else "كثافة مريحة") }
                                    )
                                }
                                if (!NetworkStatus.isOnline(context)) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics { liveRegion = LiveRegionMode.Polite },
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Filled.WifiOff, contentDescription = null)
                                            Text("غير متصل · تعرض آخر بيانات محلية محفوظة · $freshnessLabel")
                                        }
                                    }
                                }
                                if (state.showSyncHint) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "اضغط مطولاً على زر المزامنة لاختيار واحدة من آخر ثلاث مناوبات.",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            TextButton(onClick = viewModel::dismissSyncHint) {
                                                Text("فهمت")
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
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "نسخة محفوظة — للعرض والاستعادة فقط",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                                if (showSearch) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        label = { Text("بحث بالاسم أو رقم القبول الحالي أو المحتوى الطبي") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (priorityOnly || warningsOnly || unassignedOnly || urgentOnly ||
                                    newAdmissionsOnly || searchQuery.isNotBlank()
                                ) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        expanded = state.patientDetailsExpanded || patient.id in expandedPatientIds,
                                        twoColumn = state.twoColumn,
                                        doctorNames = doctorNames,
                                        readOnly = state.isReadOnly,
                                        onClick = {
                                            viewModel.loadRecentActivity()
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
                                        onExpandToggle = {
                                            expandedPatientIds = if (patient.id in expandedPatientIds) {
                                                expandedPatientIds - patient.id
                                            } else expandedPatientIds + patient.id
                                        },
                                        compact = state.compactCards
                                    )
                                }
                            }
                        } else {
                            gridItems(visiblePatients, key = { it.id }) { patient ->
                                PatientCard(
                                    patient = patient,
                                    expanded = state.patientDetailsExpanded || patient.id in expandedPatientIds,
                                    twoColumn = state.twoColumn,
                                    doctorNames = doctorNames,
                                    readOnly = state.isReadOnly,
                                    onClick = {
                                        viewModel.loadRecentActivity()
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
                                    onExpandToggle = {
                                        expandedPatientIds = if (patient.id in expandedPatientIds) {
                                            expandedPatientIds - patient.id
                                        } else expandedPatientIds + patient.id
                                    },
                                    compact = state.compactCards
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showReportSheet) {
        ModalBottomSheet(onDismissRequest = { showReportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("إرسال تقرير المناوبة", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${state.patients.size} مريض · $freshnessLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (reportReadinessIssueCount == 0) {
                        clinicalColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        if (reportReadinessIssueCount == 0) {
                            "التقرير جاهز للمراجعة والإرسال"
                        } else {
                            "$reportReadinessIssueCount ملاحظات تحتاج المراجعة قبل الإرسال"
                        },
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (state.mergeConflicts.isNotEmpty()) {
                    Text(
                        "يتضمن العدد تعارضات مزامنة يجب حسمها داخل شاشة التقرير.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = {
                        val shiftId = state.shift?.id ?: return@Button
                        showReportSheet = false
                        onOpenReport(shiftId)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Text(
                        if (reportReadinessIssueCount == 0) "متابعة سريعة للإرسال" else "مراجعة الملاحظات",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                TextButton(
                    onClick = { showReportSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إغلاق") }
            }
        }
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

    state.patients.firstOrNull { it.id == detailsTargetId }?.let { patient ->
        PatientDetailsScreen(
            patient = patient,
            doctorNames = doctorNames,
            activity = state.recentActivity.filter { it.patientId == patient.id },
            readOnly = state.isReadOnly,
            onEdit = {
                detailsTargetId = null
                editTarget = patient
            },
            onCopy = {
                detailsTargetId = null
                copyTarget = patient
            },
            onPriorityChange = { viewModel.setPriority(patient, it) },
            onDismiss = { detailsTargetId = null }
        )
    }

    if (state.rolloverPatients.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRollover,
            title = { Text("ترحيل مرضى المناوبة السابقة") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(state.rolloverPatients, key = { it.id }) { patient ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(patient.name)
                                Text(patient.followUp, style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RolloverDecision.entries.forEach { decision ->
                                        FilterChip(
                                            selected = rolloverDecisions[patient.id] == decision,
                                            onClick = {
                                                rolloverDecisions = rolloverDecisions + (patient.id to decision)
                                            },
                                            label = { Text(decision.arabicLabel) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.applyRollover(rolloverDecisions) }) {
                    val count = rolloverDecisions.values.count {
                        it in setOf(RolloverDecision.CONTINUE, RolloverDecision.CONTINUE_AND_EDIT, RolloverDecision.REASSIGN)
                    }
                    Text("تنفيذ القرارات ($count مستمر)")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissRollover) { Text("تخطي") } }
        )
    }

    if (showActivity) {
        val filteredActivity = state.recentActivity.filter { entry ->
            val matchesType = when (activityFilter) {
                ActivityFilter.ALL -> true
                ActivityFilter.PATIENTS -> entry.patientId != null || entry.action.startsWith("patient_")
                ActivityFilter.SYNC -> entry.action.contains("csv") || entry.action.contains("sync") || entry.action.contains("report")
                ActivityFilter.ACCESS -> entry.action == AppConstants.AUDIT_LOGIN || entry.action == AppConstants.AUDIT_LOGOUT
                ActivityFilter.PUBLICATIONS -> false
            }
            val query = activityQuery.trim()
            matchesType && (query.isEmpty() || listOf(
                entry.actorName.orEmpty(), entry.action, entry.detail,
                entry.beforeValue.orEmpty(), entry.afterValue.orEmpty()
            ).any { it.contains(query, ignoreCase = true) })
        }
        AlertDialog(
            onDismissRequest = { showActivity = false },
            title = { Text("مركز النشاط") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = activityQuery,
                        onValueChange = { activityQuery = it },
                        label = { Text("بحث بالمريض أو الطبيب أو العملية") },
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActivityFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = activityFilter == filter,
                                onClick = { activityFilter = filter },
                                label = { Text(filter.arabicLabel) }
                            )
                        }
                    }
                    if (activityFilter == ActivityFilter.PUBLICATIONS) {
                        if (state.publications.isEmpty()) Text("لا توجد منشورات مسجلة")
                        else LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                            items(state.publications, key = { it.messageId }) { publication ->
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text(if (publication.forced) "نشر إجباري" else "نشر عادي")
                                    Text("Snapshot: ${publication.snapshotId}", style = MaterialTheme.typography.bodySmall)
                                    Text("Parent: ${publication.parentSnapshotId ?: "—"}", style = MaterialTheme.typography.bodySmall)
                                    Text("Device: ${publication.deviceId}", style = MaterialTheme.typography.bodySmall)
                                    Text("Telegram message: ${publication.messageId}", style = MaterialTheme.typography.bodySmall)
                                }
                                Divider()
                            }
                        }
                    } else if (filteredActivity.isEmpty()) Text("لا يوجد نشاط مطابق")
                    else LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filteredActivity, key = { it.id }) { entry ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(entry.actorName ?: "النظام", style = MaterialTheme.typography.labelLarge)
                            Text(entry.detail.ifBlank { entry.action })
                            Text(
                                java.time.Instant.ofEpochMilli(entry.atEpochMillis)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            entry.beforeValue?.let { Text("قبل: $it", style = MaterialTheme.typography.bodySmall) }
                            entry.afterValue?.let { Text("بعد: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        Divider()
                    }
                }
                }
            },
            confirmButton = { TextButton(onClick = { showActivity = false }) { Text("إغلاق") } }
        )
    }

    if (state.mergeConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMergeConflicts,
            title = { Text("حل تعارضات المزامنة") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.mergeConflicts, key = { it.key }) { conflict ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(conflict.patientName, style = MaterialTheme.typography.titleSmall)
                                Text(conflict.fieldLabel, style = MaterialTheme.typography.labelLarge)
                                Text("هذا الجهاز: ${conflict.localValue}")
                                Text("النسخة المنشورة: ${conflict.remoteValue}")
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = conflictChoices[conflict.key] == ConflictChoice.LOCAL,
                                        onClick = { conflictChoices = conflictChoices + (conflict.key to ConflictChoice.LOCAL) },
                                        label = { Text("استخدام المحلي") }
                                    )
                                    FilterChip(
                                        selected = conflictChoices[conflict.key] == ConflictChoice.REMOTE,
                                        onClick = { conflictChoices = conflictChoices + (conflict.key to ConflictChoice.REMOTE) },
                                        label = { Text("استخدام المنشور") }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.resolveMergeConflicts(conflictChoices) },
                    enabled = !state.resolvingConflicts &&
                        state.mergeConflicts.all { it.key in conflictChoices }
                ) { Text(if (state.resolvingConflicts) "جارٍ الدمج…" else "دمج ونشر") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMergeConflicts) { Text("إلغاء") }
            }
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
        AlertDialog(
            onDismissRequest = viewModel::dismissSnapshotPicker,
            title = { Text("اختيار مناوبة محفوظة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("المناوبات الموجودة في آخر ملف CSV تم تنزيله:")
                    state.snapshotChoices.forEachIndexed { index, option ->
                        TextButton(
                            onClick = { viewModel.selectDownloadedSnapshot(option.shiftId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${index + 1}. ${option.date} — ${option.patientCount} مريض",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissSnapshotPicker) { Text("إلغاء") }
            }
        )
    }

    editTarget?.let { p ->
        EditPatientDialog(
            patient = p,
            doctors = state.doctors,
            saving = state.saving,
            onConfirm = {
                viewModel.updatePatient(it) {
                    viewModel.consumeRolloverReview(p.id)
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
            onApply = {
                viewModel.applySort(it)
                showSort = false
            },
            onDismiss = { showSort = false }
        )
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("بحث وتصفية وترتيب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
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
                    TextButton(onClick = { showFilters = false; showSort = true }) { Text("خيارات الترتيب") }
                    TextButton(onClick = { showFilters = false; showGroupBy = true }) { Text("خيارات التجميع") }
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false; viewMode = WardViewMode.ALL }) { Text("تطبيق") } },
            dismissButton = {
                TextButton(onClick = {
                    searchQuery = ""; priorityOnly = false; warningsOnly = false; unassignedOnly = false
                    urgentOnly = false; newAdmissionsOnly = false
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
            max = 3,
            saving = state.saving,
            onConfirm = {
                viewModel.setShiftDoctors(it) { showShiftDoctors = false }
            },
            onDismiss = { showShiftDoctors = false }
        )
    }

    if (showRecycleBin) {
        AlertDialog(
            onDismissRequest = { showRecycleBin = false },
            title = { Text("سلة المحذوفات") },
            text = {
                if (state.deletedPatients.isEmpty()) {
                    Text("لا يوجد مرضى محذوفون في هذه المناوبة")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.deletedPatients, key = { it.id }) { patient ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(patient.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        patient.admittanceNumber,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (!state.isReadOnly) {
                                    TextButton(onClick = { viewModel.restorePatient(patient) }) {
                                        Text("استعادة")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecycleBin = false }) { Text("إغلاق") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onRemove,
        label = { Text("$label ×") }
    )
}

@Composable
private fun PrimaryNavigationBar(
    viewMode: WardViewMode,
    onPatients: () -> Unit,
    onDashboard: () -> Unit,
    onActivity: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = viewMode != WardViewMode.DASHBOARD,
            onClick = onPatients,
            icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
            label = { Text("المرضى") }
        )
        NavigationBarItem(
            selected = viewMode == WardViewMode.DASHBOARD,
            onClick = onDashboard,
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
            label = { Text("اللوحة") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onActivity,
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text("النشاط") }
        )
    }
}

@Composable
private fun PrimaryNavigationRail(
    viewMode: WardViewMode,
    onPatients: () -> Unit,
    onDashboard: () -> Unit,
    onActivity: () -> Unit
) {
    NavigationRail {
        NavigationRailItem(
            selected = viewMode != WardViewMode.DASHBOARD,
            onClick = onPatients,
            icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
            label = { Text("المرضى") }
        )
        NavigationRailItem(
            selected = viewMode == WardViewMode.DASHBOARD,
            onClick = onDashboard,
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
            label = { Text("اللوحة") }
        )
        NavigationRailItem(
            selected = false,
            onClick = onActivity,
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text("النشاط") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(name: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onToggle, onLongClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$name · $count",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DrawerSubmenuHeader(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        selected = false,
        onClick = onClick,
        badge = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "طي $label" else "فتح $label"
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun disabledDrawerColors(disabled: Boolean) =
    if (disabled) {
        NavigationDrawerItemDefaults.colors(
            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    } else {
        NavigationDrawerItemDefaults.colors()
    }

private enum class WardViewMode { DASHBOARD, ALL, MINE }
private enum class DashboardFilter { ALL, MINE, URGENT, WARNINGS, UNASSIGNED, NEW_ADMISSIONS }
private enum class ActivityFilter(val arabicLabel: String) {
    ALL("الكل"), PATIENTS("المرضى"), SYNC("المزامنة"), ACCESS("الدخول"),
    PUBLICATIONS("سجل النشر")
}

@Composable
private fun HandoverDashboard(
    patients: List<Patient>,
    currentDoctorId: String,
    syncStatus: PatientSyncStatus,
    onFilter: (DashboardFilter) -> Unit,
    onSync: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mine = patients.count {
        it.responsibleResidentId == currentDoctorId || it.responsibleSpecialistId == currentDoctorId
    }
    val urgent = patients.count {
        it.isPriority || it.badgePriority == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH
    }
    val warnings = patients.count { it.badgeText.isNotBlank() }
    val unassigned = patients.count {
        it.responsibleResidentId == null || it.responsibleSpecialistId == null
    }
    val newAdmissions = patients.count { it.admittanceDays == 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("لوحة تسليم المناوبة", style = MaterialTheme.typography.headlineSmall)
        Text(
            "ملخص سريع قبل مراجعة المرضى أو نشر التقرير",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSync) { Text("مزامنة") }
            TextButton(onClick = onActivity) { Text("مركز النشاط") }
            TextButton(onClick = { onFilter(DashboardFilter.ALL) }) { Text("قائمة المرضى") }
        }
        DashboardMetric("إجمالي المرضى", patients.size, MaterialTheme.colorScheme.primaryContainer) { onFilter(DashboardFilter.ALL) }
        DashboardMetric("مرضاي", mine, MaterialTheme.colorScheme.secondaryContainer) { onFilter(DashboardFilter.MINE) }
        DashboardMetric("عاجل أو أولوية", urgent, MaterialTheme.colorScheme.errorContainer) { onFilter(DashboardFilter.URGENT) }
        DashboardMetric("شارات فعالة", warnings, MaterialTheme.colorScheme.tertiaryContainer) { onFilter(DashboardFilter.WARNINGS) }
        DashboardMetric("بحاجة إلى تعيين طبيب", unassigned, MaterialTheme.colorScheme.surfaceVariant) { onFilter(DashboardFilter.UNASSIGNED) }
        DashboardMetric("دخول اليوم", newAdmissions, MaterialTheme.colorScheme.primaryContainer) { onFilter(DashboardFilter.NEW_ADMISSIONS) }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("مركز النشاط", style = MaterialTheme.typography.titleMedium)
                Text("حالة البيانات: ${syncStatus.arabicLabel}")
                Text("استخدم معاينة التقرير لمراجعة تغييرات المناوبة قبل النشر")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardMetric(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
