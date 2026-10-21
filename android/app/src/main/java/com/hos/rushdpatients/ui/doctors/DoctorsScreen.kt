package com.hos.rushdpatients.ui.doctors

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.rememberFloatingActionSpace
import com.hos.rushdpatients.ui.components.AppFieldPair
import androidx.compose.foundation.layout.PaddingValues
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.components.AppLoadingState
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.AppTopBar
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
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf(false) }
    var registryAction by remember { mutableStateOf<String?>(null) }
    var registryReview by remember { mutableStateOf<List<Doctor>>(emptyList()) }
    var pendingDetails by rememberSaveable { mutableStateOf(false) }
    var showTeams by remember { mutableStateOf(false) }
    var transferExpanded by rememberSaveable { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            AppTopBar(title = uiText(R.string.ui_doctors_screen_001), onBack = onBack)
        },
        floatingActionButton = {
            if (!busy) ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                modifier = actionSpace.measure,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(uiText(R.string.ui_add_edit_doctor_dialog_001)) }
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
                state.loading && state.doctors.isEmpty() -> AppLoadingState(uiText(R.string.ui_login_screen_001), Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = actionSpace.bottomPadding),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = UiSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    if (state.loading || state.importing || state.exporting) item {
                        AppLoadingState(when {
                            state.importing -> uiText(R.string.ui_doctors_screen_002)
                            state.exporting -> uiText(R.string.ui_doctors_screen_003)
                            else -> uiText(R.string.ui_doctors_screen_004)
                        })
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                            AppOutlinedButton(onClick = { registryAction = "fetch" },
                                enabled = !busy && state.pendingRegistrySummary == null) { Text(uiText(R.string.ui_doctors_screen_005)) }
                            AppOutlinedButton(onClick = { registryReview = state.doctors.toList(); registryAction = "publish" }, enabled = !busy) { Text(uiText(R.string.ui_doctors_screen_006)) }
                        }
                        state.pendingRegistrySummary?.let { summary ->
                            com.hos.rushdpatients.ui.components.AppNotice(uiText(R.string.ui_doctors_screen_007),
                                actionLabel = if (pendingDetails) uiText(R.string.ui_doctors_screen_008) else uiText(R.string.ui_doctors_screen_009),
                                onAction = { pendingDetails = !pendingDetails })
                            if (pendingDetails) Text(summary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        AppOutlinedButton(onClick = { showTeams = true }, enabled = !busy) { Text(uiText(R.string.ui_team_management_dialog_002)) }
                    }
                    item {
                        AppSearchField(query = query, onQueryChange = { query = it }, label = uiText(R.string.ui_doctors_screen_010))
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                            DoctorListFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label(uiText)) }
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
                                        .semantics { stateDescription = if (transferExpanded) uiText(R.string.ui_doctors_screen_011) else uiText(R.string.ui_doctors_screen_012) }
                                ) {
                                    Text(uiText(R.string.ui_doctors_screen_013), modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Icon(if (transferExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null)
                                }
                                if (transferExpanded) {
                                    Text(
                                        uiText(R.string.ui_doctors_screen_014),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AppFieldPair(
                                        first = { fieldModifier ->
                                            AppOutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) },
                                                enabled = !busy, modifier = fieldModifier) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                                Text(uiText(R.string.ui_doctors_screen_015), Modifier.padding(start = UiSpacing.small))
                                            }
                                        },
                                        second = { fieldModifier ->
                                            AppOutlinedButton(onClick = viewModel::prepareDoctorExport,
                                                enabled = !busy, modifier = fieldModifier) {
                                                Icon(Icons.Default.Save, contentDescription = null)
                                                Text(uiText(R.string.ui_doctors_screen_016), Modifier.padding(start = UiSpacing.small))
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
                                title = if (state.doctors.isEmpty()) uiText(R.string.ui_doctors_screen_017) else uiText(R.string.ui_doctors_screen_018),
                                subtitle = if (state.doctors.isEmpty()) {
                                    uiText(R.string.ui_doctors_screen_019)
                                } else {
                                    uiText(R.string.ui_doctors_screen_020)
                                },
                                actionLabel = if (state.doctors.isNotEmpty()) uiText(R.string.ui_ward_activity_screen_008) else null,
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
            title = uiText(R.string.ui_doctors_screen_021),
            message = uiText(R.string.ui_doctors_screen_022, d.fullName),
            confirmText = uiText(R.string.ui_doctors_screen_023),
            destructive = true,
            onConfirm = {
                viewModel.deleteDoctor(d) { deleteTarget = null }
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (state.importing) {
        AlertDialog(onDismissRequest = {}, title = { Text(uiText(R.string.ui_doctors_screen_024)) },
            text = { AppLoadingState(uiText(R.string.ui_doctors_screen_025)) },
            confirmButton = {})
    }
    state.importError?.let { message ->
        AlertDialog(onDismissRequest = viewModel::dismissImportError,
            title = { Text(uiText(R.string.ui_doctors_screen_026)) }, text = { Text(message) },
            confirmButton = { AppTextButton(onClick = viewModel::dismissImportError) { Text(uiText(R.string.ui_settings_screen_006)) } })
    }

    registryAction?.let { action ->
        AlertDialog(onDismissRequest = { registryAction = null },
            title = { Text(if (action == "publish") uiText(R.string.ui_doctors_screen_006) else uiText(R.string.ui_doctors_screen_005)) },
            text = { Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(if (action == "publish") uiText(R.string.ui_doctors_screen_027)
                    else uiText(R.string.ui_doctors_screen_028))
                if (action == "publish") {
                    state.pendingRegistrySummary?.let { Text(it) }
                    Text(registryReview.joinToString("\n") { it.fullName })
                    if (registryReview != state.doctors) Text(uiText(R.string.ui_doctors_screen_029))
                }
            } }, confirmButton = { AppTextButton(enabled = !busy && (action != "publish" || registryReview == state.doctors), onClick = {
                registryAction = null
                if (action == "publish") viewModel.publishDoctors(registryReview) else viewModel.fetchDoctors()
            }) { Text(if (action == "publish") uiText(R.string.ui_doctors_screen_030) else uiText(R.string.ui_doctors_screen_031)) } },
            dismissButton = { AppTextButton(onClick = { registryAction = null }) { Text(uiText(R.string.ui_cancel)) } })
    }
    state.importPreview?.takeUnless { state.importing }?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDoctorImport,
            title = { Text(uiText(R.string.ui_doctors_screen_032)) },
            text = {
                Column(modifier = Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(uiText(R.string.ui_doctors_screen_033, preview.activeCount))
                    listOf(uiText(R.string.ui_doctors_screen_034) to preview.added, uiText(R.string.ui_doctors_screen_035) to preview.changed,
                        uiText(R.string.ui_doctors_screen_036) to preview.unchanged,
                        uiText(R.string.ui_doctors_screen_037) to preview.retained).forEach { (label, names) ->
                        Text("$label (${names.size}): " + names.joinToString(uiText(R.string.ui_patient_card_002)).ifBlank { uiText(R.string.ui_doctors_screen_038) })
                    }
                    Text(uiText(R.string.ui_doctors_screen_039, preview.adminCount))
                    Text(uiText(R.string.ui_doctors_screen_040, preview.deletedCount))
                    if (preview.newWithoutPinCount > 0) {
                        Text(
                            uiText(R.string.ui_doctors_screen_041, preview.newWithoutPinCount),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        uiText(R.string.ui_doctors_screen_042),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                AppTextButton(onClick = viewModel::confirmDoctorImport, enabled = !busy) { Text(uiText(R.string.ui_doctors_screen_043)) }
            },
            dismissButton = {
                AppTextButton(onClick = viewModel::dismissDoctorImport, enabled = !busy) { Text(uiText(R.string.ui_cancel)) }
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
    val uiText = rememberUiStrings()
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
                    doctor.isPermanentAdmin -> uiText(R.string.ui_doctors_screen_044)
                    doctor.isAdmin -> uiText(R.string.ui_ward_drawer_004)
                    else -> uiText(R.string.ui_doctors_screen_045)
                }
                Text(
                    doctor.clinicalRole.uiLabel(uiText),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (doctor.clinicalRole == com.hos.rushdpatients.data.model.ClinicalRole.RESIDENT) {
                    Text(uiText(R.string.ui_doctors_screen_046) + (teamSupervisorName ?: uiText(R.string.ui_team_management_dialog_001)), style = MaterialTheme.typography.bodySmall)
                }
                Text(uiText(R.string.ui_doctors_screen_047, accessText), style = MaterialTheme.typography.bodySmall)
                doctor.telegramId?.let {
                    Text(uiText(R.string.ui_doctors_screen_048, it), style = MaterialTheme.typography.bodySmall)
                }
                doctor.customTitle?.let {
                    Text(uiText(R.string.ui_doctors_screen_049, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = uiText(R.string.ui_doctors_screen_050, doctor.fullName))
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = uiText(R.string.ui_doctors_screen_051, doctor.fullName))
            }
        }
    }
}

private enum class DoctorListFilter(@StringRes private val labelRes: Int) {
    ALL(R.string.ui_all),
    RESIDENTS(R.string.ui_doctors_screen_052),
    SUPERVISORS(R.string.ui_doctor_choice_groups_001),
    ADMINS(R.string.ui_doctors_screen_053);

    fun label(uiText: UiStrings): String = uiText(labelRes)
}
