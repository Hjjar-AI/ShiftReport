package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.AppValueComparison
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun StaleEditReviewDialog(
    differences: List<Triple<String, String, String>>,
    available: Boolean,
    onKeepDraft: () -> Unit,
    onUseSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.ui_stale_edit_review_dialog_001)) },
        text = {
            Column(
                Modifier.fillMaxWidth().appDialogBody(UiDialog.reviewBodyMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                Text(if (available)
                    uiText(R.string.ui_stale_edit_review_dialog_002)
                else uiText(R.string.ui_stale_edit_review_dialog_003))
                differences.forEach { (label, draft, saved) ->
                    AppValueComparison(title = label, firstLabel = uiText(R.string.ui_stale_edit_review_dialog_004), firstValue = saved,
                        secondLabel = uiText(R.string.ui_stale_edit_review_dialog_005), secondValue = draft)
                }
                if (available && differences.isEmpty()) Text(uiText(R.string.ui_stale_edit_review_dialog_006))
                AppTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(uiText(R.string.ui_stale_edit_review_dialog_007))
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onKeepDraft, enabled = available) { Text(uiText(R.string.ui_stale_edit_review_dialog_008)) }
        },
        dismissButton = {
            AppTextButton(onClick = onUseSaved, enabled = available) { Text(uiText(R.string.ui_stale_edit_review_dialog_009)) }
        }
    )
}
