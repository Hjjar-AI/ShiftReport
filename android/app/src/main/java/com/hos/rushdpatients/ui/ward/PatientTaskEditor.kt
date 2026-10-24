package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppMenuItem
import com.hos.rushdpatients.ui.components.AppMenu
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.AppIconButton
import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppPickerField
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
    formState: PatientFormState,
    onChange: (List<PatientTask>) -> Unit
) {
    val uiText = rememberUiStrings()
    var completedExpanded by remember { mutableStateOf(false) }
    val now = LocalWardTaskNow.current
    val ordered = PatientTasks.orderedForDisplay(tasks, now)
    val completed = ordered.filter { it.done }
    val names = doctors.associate { it.id to it.fullName }
    val pending = tasks.count { !it.done }
    Column(modifier = modifier.animateContentSize(tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.expandMillis)), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppSectionHeader(title = uiText(R.string.ui_patient_task_editor_001, uiNumber(pending)), count = tasks.size,
                modifier = Modifier.weight(1f))
            AppOutlinedButton(onClick = { formState.taskDraft = TaskDraftState(null) }, enabled = enabled) { Text(uiText(R.string.ui_patient_task_editor_002)) }
        }
        val taskCard: @Composable (PatientTask) -> Unit = { task ->
            TaskDraftRow(task = task, names = names, enabled = enabled, nowEpochMillis = now,
                onEdit = { formState.taskDraft = TaskDraftState(task) },
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
            AppSectionHeader(title = uiText(R.string.ui_patient_task_editor_003), count = completed.size,
                expanded = completedExpanded, onToggle = { completedExpanded = !completedExpanded })
            if (completedExpanded) completed.forEach { taskCard(it) }
        }
        Text(uiText(R.string.ui_patient_task_editor_004),
            style = MaterialTheme.typography.bodySmall)
    }
    formState.taskDraft?.let { draft ->
        TaskDraftDialog(state = draft, enabled = enabled,
            onDismiss = { formState.taskDraft = null },
            onConfirm = { task ->
                onChange(if (draft.initial == null) tasks + task else tasks.map { if (it.id == task.id) task else it })
                formState.taskDraft = null
                if (task.done) completedExpanded = true
            })
    }

}

@Composable
private fun TaskDraftRow(
    task: PatientTask, names: Map<String, String>, enabled: Boolean, nowEpochMillis: Long,
    onEdit: () -> Unit, onToggleDone: () -> Unit, onDelete: () -> Unit
) {
    val uiText = rememberUiStrings()
    var menu by remember(task.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(UiPadding.content)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = task.done, onCheckedChange = { onToggleDone() }, enabled = enabled,
                    modifier = Modifier.sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget).semantics {
                        contentDescription = (if (task.done) uiText(R.string.ui_patient_task_editor_008) else uiText(R.string.ui_patient_task_editor_009)) + task.description
                    })
                Text(task.description, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Box {
                    AppIconButton(label = uiText(R.string.ui_patient_task_editor_005, task.description),
            icon = AppIcons.MoreVert,
            onClick = { menu = true },
            enabled = enabled)
                    AppMenu(expanded = menu && enabled, onDismissRequest = { menu = false }) {
                        AppMenuItem(text = { Text(uiText(R.string.ui_patient_task_editor_006)) }, onClick = { menu = false; onEdit() })
                        AppMenuItem(text = { Text(uiText(R.string.ui_patient_task_editor_007), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(PatientTasks.statusLabel(task, nowEpochMillis), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val metadata = PatientTasks.metadata(task, names, includeUnassigned = false, includeRoutinePriority = false)
            if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TaskDraftDialog(
    state: TaskDraftState, enabled: Boolean,
    onDismiss: () -> Unit, onConfirm: (PatientTask) -> Unit
) {
    val uiText = rememberUiStrings()
    var description by state::description
    var confirmDiscard by state::confirmDiscard
    val initial = state.initial
    val requestDismiss: () -> Unit = {
        if (enabled) {
            if (listOf(description) != state.initialValues) confirmDiscard = true
            else onDismiss()
        }
    }
    if (!confirmDiscard) AppAlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (initial == null) uiText(R.string.ui_patient_task_editor_014) else uiText(R.string.ui_patient_task_editor_015), Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppTextField(description, { description = it }, label = { Text(uiText(R.string.ui_patient_task_editor_016)) },
                    modifier = Modifier.fillMaxWidth(), enabled = enabled)
                Text(uiText(R.string.ui_patient_task_editor_026),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            AppButton(enabled = enabled && description.isNotBlank(), onClick = {
                onConfirm((initial ?: PatientTask(UUID.randomUUID().toString(), description.trim())).copy(
                    description = description.trim()
                ))
            }) { Text(uiText(R.string.ui_patient_task_editor_027)) }
        },
        dismissButton = { AppTextButton(onClick = requestDismiss, enabled = enabled) { Text(uiText(R.string.ui_cancel)) } }
    )
    if (confirmDiscard) ConfirmDialog(title = uiText(R.string.ui_patient_form_dialog_039),
        message = uiText(R.string.ui_patient_task_editor_028), confirmText = uiText(R.string.ui_patient_task_editor_029),
        dismissText = uiText(R.string.ui_patient_task_editor_030), destructive = true,
        onConfirm = { confirmDiscard = false; onDismiss() }, onDismiss = { confirmDiscard = false })
}

/** Nested task entry belongs to the patient draft, including unfinished text. */
internal class TaskDraftState(val initial: PatientTask?) {
    var description by mutableStateOf(initial?.description.orEmpty())
    val initialValues = listOf(description)
    var confirmDiscard by mutableStateOf(false)
}
