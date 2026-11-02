package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Shared target and label; editing and completion policies stay with the caller. */
@Composable
internal fun PatientTaskCheckbox(
    checked: Boolean,
    description: String,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiText = rememberUiStrings()
    Checkbox(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = modifier.sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget)
            .semantics {
                contentDescription = uiText(if (checked) R.string.ui_patient_task_editor_008
                    else R.string.ui_patient_task_editor_009) + description
            }
    )
}
