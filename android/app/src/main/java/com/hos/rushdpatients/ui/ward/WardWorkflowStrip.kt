package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
internal fun WardWorkflowStrip(state: WardUiState, onNext: () -> Unit) {
    val uiText = rememberUiStrings()
    var details by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (!state.deliveryLogUnavailable && state.unresolvedDeliveries == 0) {
            Row(Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget).padding(horizontal = UiSpacing.screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(if (state.syncStatus == PatientSyncStatus.BACKED_UP) uiText(R.string.ui_ward_workflow_strip_001)
                    else state.syncStatus.arabicLabel(uiText),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppTextButton(onClick = onNext, enabled = state.shift != null && !state.syncing && !state.saving) {
                    Text(state.workflowAction.label(uiText))
                }
                IconButton(onClick = { details = !details }, modifier = Modifier.semantics {
                    stateDescription = if (details) uiText(R.string.ui_ward_workflow_strip_002) else uiText(R.string.ui_ward_workflow_strip_003)
                }) {
                    Icon(if (details) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (details) uiText(R.string.ui_ward_workflow_strip_004) else uiText(R.string.ui_patient_sync_notice_002))
                }
            }
            if (details) Text(state.freshnessLabel(uiText), Modifier.padding(horizontal = UiSpacing.screen),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.deliveryLogUnavailable || state.unresolvedDeliveries > 0) {
            AppNotice(
                message = if (state.deliveryLogUnavailable) uiText(R.string.ui_ward_workflow_strip_005)
                    else uiText(R.string.ui_ward_workflow_strip_006, uiNumber(state.unresolvedDeliveries)),
                kind = NoticeKind.WARNING, actionLabel = uiText(R.string.ui_ward_workflow_strip_007),
                onAction = if (!state.syncing && !state.saving && state.shift != null) onNext else null,
                compact = true)
        }
    }
}
