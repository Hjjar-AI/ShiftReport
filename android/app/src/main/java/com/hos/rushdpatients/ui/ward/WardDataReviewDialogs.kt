package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.runtime.Composable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.ConfirmDialog

@Composable
internal fun WardDataReviewDialogs(state: WardUiState, viewModel: WardController,
    confirmLatest: Boolean,
    onDismissLatest: () -> Unit) {
    val uiText = rememberUiStrings()
    val dismissLatest = { onDismissLatest(); viewModel.dismissFetchReview() }
    val pending = state.fetchChoiceRequired || state.synchronization.pending ||
        state.synchronization.selectedSnapshot == com.hos.rushdpatients.sync.KnownShiftState.CHANGED
    if (confirmLatest && !pending) ConfirmDialog(
        title = uiText(R.string.ui_ward_data_review_dialogs_001), message = uiText(R.string.ui_ward_data_review_dialogs_002),
        confirmText = uiText(R.string.ui_ward_data_review_dialogs_003),
        onConfirm = { dismissLatest(); viewModel.bringLatestData() }, onDismiss = dismissLatest)
    if ((confirmLatest || state.fetchChoiceRequired) && pending) {
        com.hos.rushdpatients.ui.components.AppAlertDialog(
            onDismissRequest = dismissLatest,
            title = { androidx.compose.material3.Text(uiText(R.string.ui_ward_data_review_dialogs_001)) },
            text = { androidx.compose.material3.Text(uiText(R.string.sync_fetch_choice_body)) },
            confirmButton = {
                androidx.compose.foundation.layout.Column {
                    com.hos.rushdpatients.ui.components.AppButton(onClick = {
                        dismissLatest(); viewModel.reviewPendingPublication()
                    }) { androidx.compose.material3.Text(uiText(R.string.sync_review_publish)) }
                    com.hos.rushdpatients.ui.components.AppTextButton(onClick = {
                        dismissLatest(); viewModel.replaceAndFetch()
                    }) { androidx.compose.material3.Text(uiText(R.string.sync_replace_fetch)) }
                }
            },
            dismissButton = { com.hos.rushdpatients.ui.components.AppTextButton(onClick = dismissLatest) {
                androidx.compose.material3.Text(uiText(R.string.ui_cancel))
            } }
        )
    }
    state.fetchReviewText?.let { text ->
        com.hos.rushdpatients.ui.components.AppAlertDialog(
            onDismissRequest = viewModel::dismissFetchReview,
            title = { androidx.compose.material3.Text(uiText(R.string.sync_review_publish)) },
            text = { androidx.compose.material3.Text(text, androidx.compose.ui.Modifier.appDialogBody().verticalScroll(rememberScrollState())) },
            confirmButton = { com.hos.rushdpatients.ui.components.AppButton(onClick = viewModel::confirmPendingPublication) {
                androidx.compose.material3.Text(uiText(R.string.sync_publish_confirm))
            } },
            dismissButton = { com.hos.rushdpatients.ui.components.AppTextButton(onClick = viewModel::dismissFetchReview) {
                androidx.compose.material3.Text(uiText(R.string.ui_cancel))
            } })
    }
    if (state.olderRemoteConfirmation) {
        ConfirmDialog(title = uiText(R.string.sync_older_admin_title), message = state.syncIssue.orEmpty(),
            confirmText = uiText(R.string.sync_older_admin_confirm), onConfirm = {
                viewModel.dismissFetchReview(); viewModel.replaceAndFetch(allowOlder = true)
            }, onDismiss = viewModel::dismissFetchReview)
    }

}
