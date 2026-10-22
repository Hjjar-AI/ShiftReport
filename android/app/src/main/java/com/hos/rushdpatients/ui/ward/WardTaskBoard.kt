package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun WardTaskBoard(patients: List<Patient>, doctors: List<Doctor>, now: Long,
    onDismiss: () -> Unit, onPatient: (String) -> Unit, currentDoctorId: String? = null) {
    val uiText = rememberUiStrings()
    var supervisor by remember { mutableStateOf<String?>(null) }
    var resident by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf(false) }
    val names = remember(doctors) { doctors.associate { it.id to it.fullName } }
    val supervisors = remember(doctors) { doctors.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.SUPERVISOR } }
    val residents = remember(doctors) { doctors.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.RESIDENT } }
    val rows = remember(patients, supervisor, resident, now, mine, currentDoctorId) {
        PatientTasks.wardPending(patients, supervisor, resident, now)
            .filter { !mine || it.task.ownerDoctorId == currentDoctorId }
    }
    val zone = ZoneId.systemDefault()
    val groups = remember(rows, now, zone) {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        rows.groupBy { entry ->
            when {
                PatientTasks.overdue(entry.task, now) -> 0
                entry.task.dueAtEpochMillis == null -> 3
                Instant.ofEpochMilli(checkNotNull(entry.task.dueAtEpochMillis)).atZone(zone).toLocalDate() == today -> 1
                else -> 2
            }
        }.toSortedMap()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                AppCompactTopBar(title = { Text(uiText(R.string.ui_ward_view_options_dialog_004), style = MaterialTheme.typography.titleLarge) },
                    navigation = { AppIconButton(label = uiText(R.string.ui_ward_task_board_001),
            icon = Icons.Filled.Close,
            onClick = onDismiss) })
                Column(Modifier.padding(horizontal = UiSpacing.screen)) {
                    Text(uiPlural(R.plurals.ward_task_count, rows.size) + uiText(R.string.ui_ward_task_board_002),
                        style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        TaskDoctorFilter(uiText(R.string.ui_ward_task_board_003), supervisors, supervisor) { supervisor = it }
                        TaskDoctorFilter(uiText(R.string.ui_ward_task_board_004), residents, resident) { resident = it }
                        if (currentDoctorId != null) FilterChip(selected = mine, onClick = { mine = !mine }, label = { Text(uiText(R.string.ui_ward_task_board_005)) })
                    }
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = UiSpacing.screen),
                    contentPadding = PaddingValues(bottom = UiSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    if (rows.isEmpty()) item { Text(uiText(R.string.ui_ward_task_board_006)) }
                    groups.forEach { (group, entries) ->
                        stickyHeader(key = "task-group-$group") {
                            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                                Text(listOf(uiText(R.string.ui_ward_task_board_007), uiText(R.string.ui_ward_task_board_008), uiText(R.string.ui_ward_task_board_009), uiText(R.string.ui_ward_task_board_010))[group] + " · " + uiNumber(entries.size),
                                    Modifier.padding(vertical = UiSpacing.screen), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        items(entries, key = { "${it.patient.id}:${it.task.id}" }) { entry ->
                            WardTaskRow(entry, names, now, onPatient)
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
        FilterChip(selected = selected != null, onClick = { expanded = true },
            label = { Text(label + ": " + (doctors.firstOrNull { it.id == selected }?.fullName ?: if (selected == null) uiText(R.string.ui_all) else uiText(R.string.ui_ward_task_board_011))) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(uiText(R.string.ui_all)) }, onClick = { onSelect(null); expanded = false })
            doctors.sortedBy { it.fullName }.forEach { doctor ->
                DropdownMenuItem(text = { Text(doctor.fullName) }, onClick = { onSelect(doctor.id); expanded = false })
            }
        }
    }
}

@Composable
internal fun WardTaskRow(entry: com.hos.rushdpatients.domain.task.WardTaskEntry,
    names: Map<String, String>, now: Long, onPatient: (String) -> Unit) {
    val uiText = rememberUiStrings()
    val overdue = PatientTasks.overdue(entry.task, now)
    val clinical = LocalClinicalColors.current
    AppCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (overdue) clinical.urgentContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (overdue) clinical.onUrgentContainer else MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            Text(entry.patient.name, style = MaterialTheme.typography.titleSmall)
            Text(entry.task.description)
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                if (overdue) Icon(Icons.Filled.Warning, contentDescription = null)
                Text(PatientTasks.statusLabel(entry.task, now) + " · " + uiNumber(PatientTasks.metadata(entry.task, names)),
                    style = MaterialTheme.typography.bodySmall)
            }
            AppTextButton(onClick = { onPatient(entry.patient.id) }) { Text(uiText(R.string.ui_ward_task_board_012)) }
        }
    }
}
