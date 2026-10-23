package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import com.hos.rushdpatients.ui.components.noticeColors
import com.hos.rushdpatients.ui.theme.UiSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
    Column(Modifier.fillMaxWidth().animateContentSize(tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.expandMillis))) {
        if (!state.deliveryLogUnavailable && state.unresolvedDeliveries == 0) {
            Row(Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget).padding(horizontal = UiSpacing.screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                val colors = noticeColors(patientSyncNoticeKind(state.syncStatus, online = true))
                Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                    color = colors.container, contentColor = colors.content) {
                    Row(Modifier.padding(UiSpacing.small), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        Icon(when (state.syncStatus) {
                            PatientSyncStatus.LOCAL -> AppIcons.Save
                            PatientSyncStatus.PENDING -> AppIcons.CloudUpload
                            PatientSyncStatus.BACKED_UP -> AppIcons.CloudDone
                            PatientSyncStatus.SYNCING -> AppIcons.Sync
                        }, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
                        Text(if (state.syncStatus == PatientSyncStatus.BACKED_UP) uiText(R.string.ui_ward_workflow_strip_001)
                            else state.syncStatus.arabicLabel(uiText), style = MaterialTheme.typography.labelMedium)
                    }
                }
                AppTextButton(onClick = onNext, enabled = state.shift != null && !state.syncing && !state.saving) {
                    Text(state.workflowAction.label(uiText))
                }
                AppIconButton(label = if (details) uiText(R.string.ui_ward_workflow_strip_004) else uiText(R.string.ui_patient_sync_notice_002),
            icon = if (details) AppIcons.ExpandLess else AppIcons.ExpandMore,
            onClick = { details = !details },
            modifier = Modifier.semantics {
                    stateDescription = if (details) uiText(R.string.ui_ward_workflow_strip_002) else uiText(R.string.ui_ward_workflow_strip_003)
                })
            }
            if (details) Text(state.freshnessLabel(uiText), Modifier.padding(horizontal = UiSpacing.screen),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Delivery uncertainty stays in the header; routine publication status stays in the list. */
@Composable
internal fun WardDeliveryNotice(state: WardUiState, onNext: () -> Unit) {
    val uiText = rememberUiStrings()
    if (state.deliveryLogUnavailable || state.unresolvedDeliveries > 0) {
        AppNotice(
            message = if (state.deliveryLogUnavailable) uiText(R.string.ui_ward_workflow_strip_005)
                else uiText(R.string.ui_ward_workflow_strip_006, uiNumber(state.unresolvedDeliveries)),
            kind = NoticeKind.WARNING, actionLabel = uiText(R.string.ui_ward_workflow_strip_007),
            onAction = onNext, actionEnabled = !state.syncing && !state.saving && state.shift != null,
            compact = true)
    }
}
