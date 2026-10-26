package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppMenuItem
import com.hos.rushdpatients.ui.components.AppMenu
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.LocalClinicalColors
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun WardTaskBoard(patients: List<Patient>, doctors: List<Doctor>,
    onDismiss: () -> Unit, onPatient: (String) -> Unit,
    editable: Boolean = false, snackbarHost: SnackbarHostState,
    onComplete: (com.hos.rushdpatients.domain.task.WardTaskEntry) -> Unit) {
    val uiText = rememberUiStrings()
    var supervisor by rememberSaveable { mutableStateOf<String?>(null) }
    var resident by rememberSaveable { mutableStateOf<String?>(null) }
    val supervisors = remember(doctors) { doctors.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.SUPERVISOR } }
    val residents = remember(doctors) { doctors.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.RESIDENT } }
    val rows = remember(patients, supervisor, resident) {
        PatientTasks.wardPending(patients, supervisor, resident)
    }
    val completed = remember { mutableStateMapOf<String, com.hos.rushdpatients.domain.task.WardTaskEntry>() }
    var previousPending by remember { mutableStateOf(rows) }
    LaunchedEffect(patients, supervisor, resident) {
        val current = patients.associateBy { it.id }
        previousPending.forEach { old ->
            val patient = current[old.patient.id]
            val task = patient?.tasks?.firstOrNull { it.id == old.task.id }
            if (patient != null && task?.done == true)
                completed["${patient.id}:${task.id}"] = com.hos.rushdpatients.domain.task.WardTaskEntry(patient, task)
        }
        completed.keys.toList().forEach { key ->
            val old = completed.getValue(key)
            val patient = current[old.patient.id]
            if (patient == null || patient.tasks.none { it.id == old.task.id && it.done } ||
                (supervisor != null && patient.responsibleSpecialistId != supervisor) ||
                (resident != null && patient.responsibleResidentId != resident)) completed.remove(key)
        }
        previousPending = rows
    }
    completed.keys.toList().forEach { taskKey ->
        key(taskKey) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(2_000L); completed.remove(taskKey) }
        }
    }
    val visible = (rows + completed.values).groupBy { it.patient.id }
    val groups = patients.mapNotNull { patient -> visible[patient.id]?.let { patient to it } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(Modifier.safeDrawingPadding(), snackbarHost = { AppSnackbarHost(snackbarHost) }) { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    AppCompactTopBar(title = { Text(uiText(R.string.ui_ward_view_options_dialog_004), style = MaterialTheme.typography.titleLarge) },
                        navigation = { AppIconButton(label = uiText(R.string.ui_ward_task_board_001),
                            icon = AppIcons.Close,
                            onClick = onDismiss) })
                    Column(Modifier.padding(horizontal = UiSpacing.screen)) {
                        Text(uiPlural(R.plurals.ward_task_count, rows.size) + uiText(R.string.ui_ward_task_board_002),
                            style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                            TaskDoctorFilter(uiText(R.string.ui_ward_task_board_003), supervisors, supervisor) { supervisor = it }
                            TaskDoctorFilter(uiText(R.string.ui_ward_task_board_004), residents, resident) { resident = it }
                        }
                    }
                    LazyColumn(Modifier.weight(1f).padding(horizontal = UiSpacing.screen),
                        contentPadding = PaddingValues(bottom = UiSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        if (groups.isEmpty()) item { EmptyState(uiText(R.string.ui_ward_task_board_006), icon = AppIcons.Check) }
                        items(groups, key = { it.first.id }) { (patient, entries) ->
                            AppCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                    AppTextButton(onClick = { onPatient(patient.id) }) { Text(patient.displayName) }
                                    entries.sortedBy { task -> patient.tasks.indexOfFirst { it.id == task.task.id } }.forEach { entry ->
                                        key(entry.task.id) { WardTaskRow(entry, editable, onComplete) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDoctorFilter(label: String, doctors: List<Doctor>, selected: String?, onSelect: (String?) -> Unit) {
    val uiText = rememberUiStrings()
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppFilterChip(selected = selected != null, onClick = { expanded = true },
            label = { Text(label + ": " + (doctors.firstOrNull { it.id == selected }?.fullName ?: if (selected == null) uiText(R.string.ui_all) else uiText(R.string.ui_ward_task_board_011))) })
        AppMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppMenuItem(text = { Text(uiText(R.string.ui_all)) }, onClick = { onSelect(null); expanded = false })
            doctors.sortedBy { it.fullName }.forEach { doctor ->
                AppMenuItem(text = { Text(doctor.fullName) }, onClick = { onSelect(doctor.id); expanded = false })
            }
        }
    }
}

@Composable
internal fun WardTaskRow(entry: com.hos.rushdpatients.domain.task.WardTaskEntry,
    editable: Boolean = false, onComplete: (com.hos.rushdpatients.domain.task.WardTaskEntry) -> Unit = {}) {
    val uiText = rememberUiStrings()
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = entry.task.done, enabled = editable && !entry.task.done,
            onCheckedChange = { onComplete(entry) },
            modifier = Modifier.sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget)
                .semantics { contentDescription = uiText(R.string.ui_patient_task_editor_009) + entry.task.description })
        Text(entry.task.description, Modifier.weight(1f),
            textDecoration = if (entry.task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
    }
}
