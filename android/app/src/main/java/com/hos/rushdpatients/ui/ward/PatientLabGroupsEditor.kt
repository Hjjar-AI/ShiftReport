package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.PatientLabGroup
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppFieldPair
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.UiSpacing
import java.time.LocalDate

@Composable
internal fun PatientLabGroupsEditor(
    groups: List<PatientLabGroup>, textStyle: TextStyle, enabled: Boolean,
    onChange: (List<PatientLabGroup>) -> Unit
) {
    val uiText = rememberUiStrings()
    var removing by remember { mutableStateOf<String?>(null) }
    fun update(group: PatientLabGroup) = onChange(groups.map { if (it.id == group.id) group else it })
    fun move(index: Int, destination: Int) {
        onChange(groups.toMutableList().apply { add(destination, removeAt(index)) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppSectionHeader(uiText(R.string.ui_labs))
        groups.forEachIndexed { index, group -> key(group.id) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f),
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(UiSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    val groupLabel = uiText(R.string.lab_group_number, index + 1)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(groupLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        AppIconButton(label = uiText(R.string.lab_group_up) + " • " + groupLabel,
                            icon = AppIcons.ArrowUpward, enabled = enabled && index > 0,
                            onClick = { move(index, index - 1) })
                        AppIconButton(label = uiText(R.string.lab_group_down) + " • " + groupLabel,
                            icon = AppIcons.ArrowDownward, enabled = enabled && index < groups.lastIndex,
                            onClick = { move(index, index + 1) })
                        AppIconButton(label = uiText(R.string.lab_group_remove) + " • " + groupLabel,
                            icon = AppIcons.Delete, enabled = enabled, onClick = { removing = group.id })
                    }
                    MultilineField(group.results, { update(group.copy(results = it)) },
                        uiText(R.string.lab_group_results), textStyle)
                    AppFieldPair(
                        first = { fieldModifier ->
                            DateField(value = group.date?.let(LocalDate::parse),
                                onValueChange = { update(group.copy(date = it.toString())) },
                                label = uiText(R.string.lab_group_date), supportingText = null, enabled = enabled,
                                onClear = { update(group.copy(date = null)) }, modifier = fieldModifier)
                        },
                        second = { fieldModifier ->
                            AppTextField(value = group.title, onValueChange = { update(group.copy(title = it)) },
                                label = { Text(uiText(R.string.lab_group_title)) }, enabled = enabled,
                                modifier = fieldModifier, textStyle = textStyle)
                        }
                    )

                }
            }
        } }
        AppOutlinedButton(enabled = enabled, onClick = { onChange(groups + PatientLabGroup(id = java.util.UUID.randomUUID().toString())) }) {
            Text(uiText(R.string.lab_groups_add))
        }
    }
    if (removing != null) ConfirmDialog(title = uiText(R.string.lab_group_remove),
        message = uiText(R.string.lab_group_remove_message), destructive = true,
        onConfirm = { onChange(groups.filterNot { it.id == removing }); removing = null },
        onDismiss = { removing = null })
}
