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
internal fun WardWorkflowStrip(state: WardUiState, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.small)) {
            Text(state.freshnessLabel, style = MaterialTheme.typography.labelSmall)
            if (state.deliveryLogUnavailable || state.unresolvedDeliveries > 0) {
                com.hos.rushdpatients.ui.components.AppNotice(
                    message = if (state.deliveryLogUnavailable) "تعذر قراءة سجل التسليم؛ تحقق منه قبل إعادة الإرسال"
                        else "${uiNumber(state.unresolvedDeliveries)} محاولات تسليم غير محسومة؛ افحص تليجرام قبل الإعادة",
                    kind = com.hos.rushdpatients.ui.components.NoticeKind.WARNING,
                    actionLabel = "مراجعة سجل التسليم",
                    onAction = if (!state.syncing && !state.saving && state.shift != null) onNext else null, compact = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                if (!state.deliveryLogUnavailable && state.unresolvedDeliveries == 0) AppTextButton(onClick = onNext, enabled = state.shift != null && !state.syncing && !state.saving) { Text(state.workflowAction.label) }
            }
        }
    }
}
