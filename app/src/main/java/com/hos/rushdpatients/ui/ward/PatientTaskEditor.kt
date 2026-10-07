package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.data.model.TaskPriority
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.util.ShiftDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatientTaskEditor(
    tasks: List<PatientTask>, doctors: List<Doctor>, enabled: Boolean,
    onChange: (List<PatientTask>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PatientTask?>(null) }
    var adding by remember { mutableStateOf(false) }
    val names = doctors.associate { it.id to it.fullName }
    val pending = tasks.count { !it.done }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("مهام المريض: $pending معلقة / ${tasks.size} إجمالي · " + if (expanded) "طي" else "عرض")
            }
            TextButton(onClick = { adding = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إضافة مهمة")
            }
        }
        if (expanded) tasks.forEach { task ->
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(8.dp)) {
                    Text(PatientTasks.summary(listOf(task), names))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { editing = task }, enabled = enabled,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "تعديل المهمة: ${task.description}" }) { Text("تعديل") }
                        TextButton(onClick = {
                            onChange(tasks.map {
                                if (it.id != task.id) it else it.copy(done = !it.done,
                                    completedByDoctorId = null, completedByName = null, completedAtEpochMillis = null)
                            })
                        }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = (if (task.done) "إعادة فتح المهمة: " else "تحديد كمكتملة: ") + task.description
                        }) { Text(if (task.done) "إعادة فتح المهمة" else "تحديد كمكتملة") }
                        TextButton(onClick = { onChange(tasks.filterNot { it.id == task.id }) }, enabled = enabled,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "حذف المهمة: ${task.description}" }) {
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Text("تُحفظ تغييرات المهام مع حفظ المريض؛ يسجل التطبيق منفّذ الإتمام ووقته عند الحفظ.",
            style = MaterialTheme.typography.bodySmall)
    }
    if (adding || editing != null) TaskDraftDialog(
        initial = editing, doctors = doctors.filterNot { it.isDeleted }, enabled = enabled,
        onDismiss = { adding = false; editing = null },
        onConfirm = { task ->
            onChange(if (adding) tasks + task else tasks.map { if (it.id == task.id) task else it })
            adding = false
            editing = null
            expanded = true
        }
    )
}

private enum class TaskDeadline(val label: String) { NONE("بلا موعد"), TIME("تاريخ ووقت"), SHIFT("نهاية مناوبة") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDraftDialog(
    initial: PatientTask?, doctors: List<Doctor>, enabled: Boolean,
    onDismiss: () -> Unit, onConfirm: (PatientTask) -> Unit
) {
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var owner by remember(initial) { mutableStateOf(initial?.ownerDoctorId) }
    var priority by remember(initial) { mutableStateOf(initial?.priority ?: TaskPriority.NORMAL) }
    var deadline by remember(initial) { mutableStateOf(when {
        initial?.dueShiftDate != null -> TaskDeadline.SHIFT
        initial?.dueAtEpochMillis != null -> TaskDeadline.TIME
        else -> TaskDeadline.NONE
    }) }
    val due = initial?.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    var date by remember(initial) { mutableStateOf(initial?.dueShiftDate?.let(LocalDate::parse) ?: due?.toLocalDate() ?: ShiftDate.current()) }
    var time by remember(initial) { mutableStateOf(due?.toLocalTime()?.withSecond(0)?.withNano(0)?.toString() ?: "18:00") }
    var error by remember(initial) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text(if (initial == null) "مهمة جديدة" else "تعديل المهمة", Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(description, { description = it; error = null }, label = { Text("وصف المهمة") },
                    modifier = Modifier.fillMaxWidth(), enabled = enabled)
                DoctorDropdown(label = "مسؤول المهمة", undefinedLabel = "غير معيّنة", selectedId = owner,
                    doctors = doctors, onSelected = { owner = it }, modifier = Modifier.fillMaxWidth())
                Text("الأولوية")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskPriority.entries.forEach { option ->
                        FilterChip(selected = priority == option, onClick = { priority = option },
                            label = { Text(option.arabicLabel) }, enabled = enabled)
                    }
                }
                Text("الاستحقاق")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskDeadline.entries.forEach { option ->
                        FilterChip(selected = deadline == option, onClick = { deadline = option; error = null },
                            label = { Text(option.label) }, enabled = enabled)
                    }
                }
                if (deadline != TaskDeadline.NONE) {
                    DateField(date, { date = it }, if (deadline == TaskDeadline.SHIFT) "تاريخ المناوبة" else "تاريخ الاستحقاق",
                        if (deadline == TaskDeadline.SHIFT) "نهاية المناوبة الساعة 08:30 في اليوم التالي" else null)
                }
                if (deadline == TaskDeadline.TIME) {
                    OutlinedTextField(time, { time = it; error = null }, label = { Text("الوقت (HH:mm، بنظام 24 ساعة)") },
                        singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = enabled && description.isNotBlank(), onClick = {
                val normalizedTime = time.map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
                val parsedTime = runCatching { LocalTime.parse(normalizedTime) }.getOrNull()
                if (deadline == TaskDeadline.TIME && parsedTime == null) {
                    error = "أدخل وقتاً صالحاً مثل 18:30"
                } else {
                    val dueAt = when (deadline) {
                        TaskDeadline.NONE -> null
                        TaskDeadline.SHIFT -> ShiftDate.endOf(date).toEpochMilli()
                        TaskDeadline.TIME -> date.atTime(requireNotNull(parsedTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    onConfirm((initial ?: PatientTask(UUID.randomUUID().toString(), description.trim())).copy(
                        description = description.trim(), ownerDoctorId = owner,
                        ownerName = doctors.firstOrNull { it.id == owner }?.fullName, priority = priority,
                        dueAtEpochMillis = dueAt, dueShiftDate = date.toString().takeIf { deadline == TaskDeadline.SHIFT }
                    ))
                }
            }) { Text("اعتماد في المسودة") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text("إلغاء") } }
    )
}
