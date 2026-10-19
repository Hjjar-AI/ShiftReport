package com.hos.rushdpatients.ui.doctors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
internal fun TeamManagementDialog(doctors: List<Doctor>, busy: Boolean, onDismiss: () -> Unit,
    onSave: (List<Doctor>, Map<String, String?>) -> Unit) {
    val expected = remember { doctors.toList() }
    val residents = expected.filter { it.clinicalRole == ClinicalRole.RESIDENT && !it.isDeleted }.sortedBy { it.fullName }
    val supervisors = expected.filter { it.clinicalRole == ClinicalRole.SUPERVISOR && !it.isDeleted }.sortedBy { it.fullName }
    var assignments by remember { mutableStateOf(residents.associate { it.id to it.teamSupervisorId }) }
    var reviewing by remember { mutableStateOf(false) }
    val changes = assignments.filter { (id, supervisor) -> expected.first { it.id == id }.teamSupervisorId != supervisor }
    fun name(id: String?) = expected.firstOrNull { it.id == id }?.fullName ?: "دون فريق"
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(UiSpacing.medium)) {
                Text("إدارة الفرق معاً", style = MaterialTheme.typography.titleLarge)
                Text("تعيينات المرضى والتغطية الفردية تبقى كما هي. راجع جميع تغييرات العضوية قبل الحفظ والنشر.")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    (supervisors.map { it.id } + listOf<String?>(null)).forEach { id ->
                        Text(name(id) + ": " + residents.filter { assignments[it.id] == id }.joinToString("، ") { it.fullName }.ifBlank { "دون مقيمين" },
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    residents.forEach { resident ->
                        key(resident.id) {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                AppTextButton(onClick = { expanded = true }, enabled = !busy) {
                                    Text(resident.fullName + " ← " + name(assignments[resident.id]))
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(text = { Text("دون فريق") }, onClick = { assignments = assignments + (resident.id to null); expanded = false }, enabled = !busy)
                                    supervisors.forEach { supervisor ->
                                        DropdownMenuItem(text = { Text(supervisor.fullName) }, onClick = {
                                            assignments = assignments + (resident.id to supervisor.id); expanded = false
                                        }, enabled = !busy)
                                    }
                                }
                            }
                        }
                    }
                    if (residents.isEmpty()) Text("لا يوجد مقيمون في السجل")
                }
                AppButton(onClick = { reviewing = true }, enabled = !busy && changes.isNotEmpty()) { Text("مراجعة ${changes.size} تغييرات") }
                AppTextButton(onClick = onDismiss, enabled = !busy) { Text("إلغاء") }
            }
        }
    }
    if (reviewing) AlertDialog(onDismissRequest = { if (!busy) reviewing = false }, title = { Text("مراجعة عضوية الفرق") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            changes.forEach { (id, next) -> Text(name(id) + "\n" + name(expected.first { it.id == id }.teamSupervisorId) + " ← " + name(next)) }
        } }, confirmButton = { AppButton(onClick = { onSave(expected, changes) }, enabled = !busy) { Text("حفظ ونشر تغييرات الفرق") } },
        dismissButton = { AppTextButton(onClick = { reviewing = false }, enabled = !busy) { Text("عودة للتحرير") } })
}
