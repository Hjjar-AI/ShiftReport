package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WardWorkflowStrip(state: WardUiState, onNext: () -> Unit, onTasks: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.small)) {
            val deliveryNotice = when {
                state.deliveryLogUnavailable -> " · سجل التسليم غير متاح"
                state.unresolvedDeliveries > 0 -> " · تسليم غير مؤكد: ${uiNumber(state.unresolvedDeliveries)}"
                else -> ""
            }
            Text(state.freshnessLabel + deliveryNotice,
                style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppTextButton(onClick = onNext, enabled = state.shift != null && !state.syncing && !state.saving) { Text(state.workflowAction.label) }
                AppTextButton(onClick = onTasks, enabled = !state.loading && !state.saving && !state.syncing) { Text("مهام الجناح") }
            }
        }
    }
}
