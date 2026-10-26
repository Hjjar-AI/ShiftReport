package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppDivider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.components.uiLabel
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.patientBadgeColors

/** Add and Edit share one optional group backed by the existing retained form draft. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatientOptionalFields(
    formState: PatientFormState, saving: Boolean, autoStyle: TextStyle,
    modifier: Modifier = Modifier, entryOptionsSummary: String? = null,
    entryOptions: @Composable () -> Unit = {}
) {
    val uiText = rememberUiStrings()
    var tasks by formState::tasks
    var isPriority by formState::isPriority
    var badges by formState::badges
    var badgeDraftText by formState::badgeDraftText
    var badgeDraftPriority by formState::badgeDraftPriority
    val summary = listOfNotNull(
        uiText(R.string.ui_patient_optional_summary,
            uiNumber(tasks.size), uiNumber(badges.size + if (badgeDraftText.isNotBlank()) 1 else 0)),
        uiText(R.string.ui_patient_form_dialog_022).takeIf { isPriority }, entryOptionsSummary
    ).joinToString(" · ")
    AppSectionHeader(
        title = uiText(R.string.ui_patient_optional_section), summary = summary,
        expanded = formState.extrasExpanded,
        onToggle = { if (!saving) formState.extrasExpanded = !formState.extrasExpanded },
        modifier = modifier
    )
    if (formState.extrasExpanded) {
        PatientTaskEditor(tasks, !saving, formState = formState) { tasks = it }
        AppDivider()
        AppSectionHeader(uiText(R.string.ui_patient_form_dialog_021))
        AppToggleRow(title = uiText(R.string.ui_patient_form_dialog_022), checked = isPriority,
            onCheckedChange = { isPriority = it }, enabled = !saving)
        badges.forEachIndexed { index, badge ->
            val badgeColors = patientBadgeColors(badge.priority)
            Surface(modifier = Modifier.fillMaxWidth(), color = badgeColors.container,
                contentColor = badgeColors.content, shape = MaterialTheme.shapes.small) {
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(start = UiSpacing.small, top = UiSpacing.micro, bottom = UiSpacing.micro),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(buildString {
                        append(badge.text)
                        badge.priority?.let { append(" · ").append(it.uiLabel(uiText)) }
                    }, modifier = Modifier.weight(1f))
                    AppIconButton(label = uiText(R.string.ui_patient_form_dialog_027),
                        icon = AppIcons.Close,
                        onClick = { badges = badges.filterIndexed { i, _ -> i != index } },
                        enabled = !saving)
                }
            }
        }
        AppTextField(
            value = badgeDraftText, onValueChange = { badgeDraftText = it.take(80) },
            label = { Text(uiText(R.string.ui_patient_form_dialog_028)) },
            supportingText = { Text(uiText(R.string.ui_patient_form_dialog_029)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = autoStyle
        )
        Text(uiText(R.string.ui_patient_form_dialog_030), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            AppFilterChip(enabled = !saving, selected = badgeDraftPriority == null,
                onClick = { badgeDraftPriority = null },
                label = { Text(uiText(R.string.ui_patient_form_dialog_031)) })
            PatientBadgePriority.entries.forEach { level ->
                AppFilterChip(enabled = !saving, selected = badgeDraftPriority == level,
                    onClick = { badgeDraftPriority = level }, label = { Text(level.uiLabel(uiText)) })
            }
        }
        AppOutlinedButton(
            onClick = {
                val text = badgeDraftText.trim()
                if (text.isNotEmpty()) {
                    badges = badges + PatientBadge(text, badgeDraftPriority)
                    badgeDraftText = ""
                    badgeDraftPriority = null
                }
            }, enabled = !saving && badgeDraftText.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text(uiText(R.string.ui_patient_form_dialog_032)) }

        entryOptions()
    }
}
