package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.Composable
import com.hos.rushdpatients.ui.components.ConfirmDialog

@Composable
internal fun WardDataReviewDialogs(state: WardUiState, viewModel: WardViewModel,
    confirmLatest: Boolean, confirmPrevious: Boolean,
    onDismissLatest: () -> Unit, onDismissPrevious: () -> Unit) {
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
            title = "جلب أحدث نسخة",
            message = "ستُستبدل بيانات المرضى المحلية بأحدث نسخة منشورة. راجع تعديلاتك المحلية قبل الجلب.",
            confirmText = "جلب النسخة",
            onConfirm = {
                onDismissLatest()
                viewModel.bringLatestData()
            },
            onDismiss = { onDismissLatest() }
        )
    }

    if (confirmPrevious) {
        ConfirmDialog(
            title = "جلب نسخة سابقة",
            message = "ستُستبدل البيانات المحلية بالنسخة التي سبقت أحدث نشر. تبقى النسخة المنشورة على تليجرام كما هي.",
            confirmText = "جلب النسخة السابقة",
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
