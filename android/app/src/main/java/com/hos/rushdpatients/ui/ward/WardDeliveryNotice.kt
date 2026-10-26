package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.Composable
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.components.uiNumber

/** Delivery uncertainty remains visible above the ward shortcuts. */
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
