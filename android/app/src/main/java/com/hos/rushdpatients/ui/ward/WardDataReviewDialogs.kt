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
    confirmLatest: Boolean, confirmPrevious: Boolean,
    onDismissLatest: () -> Unit, onDismissPrevious: () -> Unit) {
    val uiText = rememberUiStrings()
    if (state.rolloverPatients.isNotEmpty()) {
        RolloverReviewDialog(
            patients = state.rolloverPatients,
            saving = state.saving,
            sourceLabel = state.rolloverSourceLabel,
            onConfirm = viewModel::applyRollover,
            onDismiss = viewModel::dismissRollover
        )
    }

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
                        dismissLatest(); viewModel.reviewPublishAndFetch()
                    }) { androidx.compose.material3.Text(uiText(R.string.sync_publish_fetch)) }
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
            title = { androidx.compose.material3.Text(uiText(R.string.sync_publish_fetch)) },
            text = { androidx.compose.material3.Text(text, androidx.compose.ui.Modifier.appDialogBody().verticalScroll(rememberScrollState())) },
            confirmButton = { com.hos.rushdpatients.ui.components.AppButton(onClick = viewModel::confirmPublishAndFetch) {
                androidx.compose.material3.Text(uiText(R.string.sync_publish_fetch_confirm))
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

    if (confirmPrevious) {
        ConfirmDialog(
            title = uiText(R.string.ui_ward_data_review_dialogs_004),
            message = uiText(R.string.ui_ward_data_review_dialogs_005),
            confirmText = uiText(R.string.ui_ward_data_review_dialogs_006),
            onConfirm = {
                onDismissPrevious()
                viewModel.bringPreviousData()
            },
            onDismiss = { onDismissPrevious() }
        )
    }

    if (state.showSnapshotPicker) {
        ShiftSnapshotPickerDialog(
            options = state.snapshotChoices,
            onSelect = viewModel::selectDownloadedSnapshot,
            onDismiss = viewModel::dismissSnapshotPicker
        )
    }
}
