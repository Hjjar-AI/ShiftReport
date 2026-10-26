package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing
import java.util.UUID

/** Inline checklist values belong to the patient form and are committed with its Save. */
@Composable
internal fun PatientTaskEditor(
    tasks: List<PatientTask>, enabled: Boolean,
    modifier: Modifier = Modifier, formState: PatientFormState,
    onChange: (List<PatientTask>) -> Unit
) {
    val uiText = rememberUiStrings()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        AppSectionHeader(title = uiText(R.string.ui_patient_task_editor_001, uiNumber(tasks.count { !it.done })), count = tasks.size)
        tasks.forEach { task ->
            key(task.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.done, enabled = enabled, onCheckedChange = { done ->
                        onChange(tasks.map { if (it.id == task.id) it.copy(done = done,
                            completedByDoctorId = null, completedByName = null, completedAtEpochMillis = null) else it })
                    }, modifier = Modifier.sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget).semantics {
                        contentDescription = (if (task.done) uiText(R.string.ui_patient_task_editor_008) else uiText(R.string.ui_patient_task_editor_009)) + task.description
                    })
                    AppTextField(value = task.description, enabled = enabled,
                        onValueChange = { text -> onChange(tasks.map { if (it.id == task.id) it.copy(description = text) else it }) },
                        label = { Text(uiText(R.string.ui_patient_task_editor_016)) }, modifier = Modifier.weight(1f))
                    AppIconButton(label = uiText(R.string.task_remove_named, task.description), icon = AppIcons.Delete,
                        enabled = enabled, onClick = { onChange(tasks.filterNot { it.id == task.id }) })
                }
            }
        }
        AppTextField(value = formState.taskEntry?.description.orEmpty(), enabled = enabled,
            onValueChange = { text ->
                formState.taskEntry = if (text.isEmpty()) null else
                    (formState.taskEntry ?: PatientTask(UUID.randomUUID().toString(), text)).copy(description = text)
            }, label = { Text(uiText(R.string.ui_patient_task_editor_014)) }, modifier = Modifier.fillMaxWidth())
        AppOutlinedButton(enabled = enabled && formState.taskEntry?.description?.isNotBlank() == true,
            onClick = {
                formState.taskEntry?.takeIf { it.description.isNotBlank() }?.let { onChange(tasks + it) }
                formState.taskEntry = null
            }) { Text(uiText(R.string.ui_patient_task_editor_002)) }
        Text(uiText(R.string.ui_patient_task_editor_026))
    }
}
