package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
internal fun WardTaskBoard(patients: List<Patient>, doctors: List<Doctor>, now: Long,
    onDismiss: () -> Unit, onPatient: (String) -> Unit) {
    var supervisor by remember { mutableStateOf<String?>(null) }
    var resident by remember { mutableStateOf<String?>(null) }
    val names = doctors.associate { it.id to it.fullName }
    val rows = PatientTasks.wardPending(patients, supervisor, resident, now)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(UiSpacing.medium)) {
                Text("مهام الجناح — المهام المعلقة", style = MaterialTheme.typography.titleLarge)
                Text("${rows.size} مهمة · الأقدم موعداً أولاً. الإتمام من ملف المريض بعد المراجعة والحفظ.")
                TaskDoctorFilter("مشرف المريض", doctors.filter { it.clinicalRole == ClinicalRole.SUPERVISOR }, supervisor) { supervisor = it }
                TaskDoctorFilter("مقيم المريض", doctors.filter { it.clinicalRole == ClinicalRole.RESIDENT }, resident) { resident = it }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    if (rows.isEmpty()) item { Text("لا توجد مهام معلقة ضمن التصفية") }
                    items(rows, key = { "${it.patient.id}:${it.task.id}" }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(UiSpacing.small)) {
                                Text(entry.patient.name, style = MaterialTheme.typography.titleSmall)
                                Text(entry.task.description)
                                Text(PatientTasks.statusLabel(entry.task, now) + " · " + PatientTasks.metadata(entry.task, names),
                                    style = MaterialTheme.typography.bodySmall)
                                AppTextButton(onClick = { onPatient(entry.patient.id) }) { Text("فتح ملف المريض") }
                            }
                        }
                    }
                }
                AppTextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    }
}

@Composable
private fun TaskDoctorFilter(label: String, doctors: List<Doctor>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppTextButton(onClick = { expanded = true }) { Text(label + ": " + (doctors.firstOrNull { it.id == selected }?.fullName ?: if (selected == null) "الكل" else "طبيب غير متاح")) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("الكل") }, onClick = { onSelect(null); expanded = false })
            doctors.sortedBy { it.fullName }.forEach { doctor ->
                DropdownMenuItem(text = { Text(doctor.fullName) }, onClick = { onSelect(doctor.id); expanded = false })
            }
        }
    }
}
