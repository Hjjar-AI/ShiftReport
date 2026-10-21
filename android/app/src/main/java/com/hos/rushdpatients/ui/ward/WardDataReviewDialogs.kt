package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.runtime.Composable
import com.hos.rushdpatients.ui.components.ConfirmDialog

@Composable
internal fun WardDataReviewDialogs(state: WardUiState, viewModel: WardViewModel,
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

    if (confirmLatest) {
        ConfirmDialog(
            title = uiText(R.string.ui_ward_data_review_dialogs_001),
            message = uiText(R.string.ui_ward_data_review_dialogs_002),
            confirmText = uiText(R.string.ui_ward_data_review_dialogs_003),
            onConfirm = {
                onDismissLatest()
                viewModel.bringLatestData()
            },
            onDismiss = { onDismissLatest() }
        )
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
