package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppPickerField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.data.model.TaskPriority
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.ui.theme.UiSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PatientTaskEditor(
    tasks: List<PatientTask>, doctors: List<Doctor>, enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (List<PatientTask>) -> Unit
) {
    var editing by remember { mutableStateOf<PatientTask?>(null) }
    var adding by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60_000) }
    }
    val ordered = PatientTasks.orderedForDisplay(tasks, now)
    val completed = ordered.filter { it.done }
    val names = doctors.associate { it.id to it.fullName }
    val pending = tasks.count { !it.done }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppSectionHeader(title = "مهام المريض · ${uiNumber(pending)} معلقة", count = tasks.size,
                modifier = Modifier.weight(1f))
            AppOutlinedButton(onClick = { adding = true }, enabled = enabled) { Text("إضافة مهمة") }
        }
        val taskCard: @Composable (PatientTask) -> Unit = { task ->
            TaskDraftRow(task = task, names = names, enabled = enabled, nowEpochMillis = now,
                onEdit = { editing = task },
                onToggleDone = {
                    onChange(tasks.map {
                        if (it.id != task.id) it else it.copy(done = !it.done,
                            completedByDoctorId = null, completedByName = null, completedAtEpochMillis = null)
                    })
                },
                onDelete = { onChange(tasks.filterNot { it.id == task.id }) })
        }
        ordered.filterNot { it.done }.forEach { taskCard(it) }
        if (completed.isNotEmpty()) {
            AppSectionHeader(title = "المكتملة", count = completed.size,
                expanded = completedExpanded, onToggle = { completedExpanded = !completedExpanded })
            if (completedExpanded) completed.forEach { taskCard(it) }
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
            if (task.done) completedExpanded = true
        }
    )
}

@Composable
private fun TaskDraftRow(
    task: PatientTask, names: Map<String, String>, enabled: Boolean, nowEpochMillis: Long,
    onEdit: () -> Unit, onToggleDone: () -> Unit, onDelete: () -> Unit
) {
    var menu by remember(task.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(UiPadding.content)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.description, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "خيارات المهمة: ${task.description}")
                    }
                    DropdownMenu(expanded = menu && enabled, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("تعديل") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(PatientTasks.statusLabel(task, nowEpochMillis), style = MaterialTheme.typography.labelMedium,
                color = if (PatientTasks.overdue(task, nowEpochMillis)) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            val metadata = PatientTasks.metadata(task, names, includeUnassigned = false, includeRoutinePriority = false)
            if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppOutlinedButton(onClick = onToggleDone, enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = (if (task.done) "إعادة فتح المهمة: " else "تحديد كمكتملة: ") + task.description
                    stateDescription = if (task.done) "مكتملة" else "معلقة"
                }) { Text(if (task.done) "إعادة فتح المهمة" else "تحديد كمكتملة") }
        }
    }
}

private enum class TaskDeadline(val label: String) { NONE("بلا موعد"), TIME("تاريخ ووقت"), SHIFT("نهاية مناوبة") }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    var time by remember(initial) { mutableStateOf(due?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(18, 0)) }
    val context = LocalContext.current
    var error by remember(initial) { mutableStateOf<String?>(null) }
    val initialValues = remember(initial) { listOf(description, owner, priority, deadline, date, time) }
    var confirmDiscard by remember(initial) { mutableStateOf(false) }
    val requestDismiss: () -> Unit = {
        if (enabled) {
            if (listOf(description, owner, priority, deadline, date, time) != initialValues) confirmDiscard = true
            else onDismiss()
        }
    }
    if (!confirmDiscard) AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (initial == null) "مهمة جديدة" else "تعديل المهمة", Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppTextField(description, { description = it; error = null }, label = { Text("وصف المهمة") },
                    modifier = Modifier.fillMaxWidth(), enabled = enabled, isError = error != null, errorMessage = error)
                SectionTitle("موعد المهمة")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    TaskDeadline.entries.forEach { option ->
                        FilterChip(selected = deadline == option, onClick = { deadline = option; error = null },
                            label = { Text(option.label) }, enabled = enabled)
                    }
                }
                if (deadline != TaskDeadline.NONE) {
                    DateField(date, { date = it }, if (deadline == TaskDeadline.SHIFT) "تاريخ المناوبة" else "تاريخ الاستحقاق",
                        if (deadline == TaskDeadline.SHIFT) "نهاية المناوبة الساعة 08:30 في اليوم التالي" else null, enabled = enabled)
                }
                if (deadline == TaskDeadline.TIME) {
                    AppPickerField(value = time.toString(), label = "وقت الاستحقاق", icon = Icons.Filled.Schedule,
                        valueTextDirection = TextDirection.Ltr, onClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            time = LocalTime.of(hour, minute)
                            error = null
                        }, time.hour, time.minute, true).show()
                    }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                }
                SectionTitle("تفاصيل إضافية")
                DoctorDropdown(label = "مسؤول المهمة (اختياري)", undefinedLabel = "غير معيّنة", selectedId = owner,
                    doctors = doctors, onSelected = { owner = it }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
                SectionTitle("الأولوية (اختياري)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    TaskPriority.entries.forEach { option ->
                        FilterChip(selected = priority == option, onClick = { priority = option },
                            label = { Text(option.arabicLabel) }, enabled = enabled)
                    }
                }
                Text("اعتماد المهمة يضيفها إلى المسودة فقط؛ اضغط «حفظ» في صفحة المريض لحفظها.",
                    style = MaterialTheme.typography.bodySmall)
                error?.let { AppNotice(it, kind = NoticeKind.ERROR) }
            }
        },
        confirmButton = {
            AppButton(enabled = enabled && description.isNotBlank(), onClick = {
                val dueAt = when (deadline) {
                    TaskDeadline.NONE -> null
                    TaskDeadline.SHIFT -> ShiftDate.endOf(date).toEpochMilli()
                    TaskDeadline.TIME -> date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                onConfirm((initial ?: PatientTask(UUID.randomUUID().toString(), description.trim())).copy(
                    description = description.trim(), ownerDoctorId = owner,
                    ownerName = doctors.firstOrNull { it.id == owner }?.fullName, priority = priority,
                    dueAtEpochMillis = dueAt, dueShiftDate = date.toString().takeIf { deadline == TaskDeadline.SHIFT }
                ))
            }) { Text("اعتماد المهمة في المسودة") }
        },
        dismissButton = { AppTextButton(onClick = requestDismiss, enabled = enabled) { Text("إلغاء") } }
    )
    if (confirmDiscard) ConfirmDialog(title = "تجاهل التغييرات؟",
        message = "لم تُعتمد تعديلات المهمة في مسودة المريض.", confirmText = "تجاهل والخروج",
        dismissText = "متابعة التحرير", destructive = true,
        onConfirm = { confirmDiscard = false; onDismiss() }, onDismiss = { confirmDiscard = false })
}
