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
    val dismissLatest = { onDismissLatest(); viewModel.dismissPublicationReview() }
    if (confirmLatest || state.fetchChoiceRequired) ConfirmDialog(
        title = uiText(R.string.ui_ward_data_review_dialogs_001), message = uiText(R.string.ui_ward_data_review_dialogs_002),
        confirmText = uiText(R.string.sync_replace_fetch),
        onConfirm = { dismissLatest(); viewModel.bringLatestData() }, onDismiss = dismissLatest)
    state.publicationReviewText?.let { text ->
        com.hos.rushdpatients.ui.components.AppAlertDialog(
            onDismissRequest = viewModel::dismissPublicationReview,
            title = { androidx.compose.material3.Text(uiText(R.string.sync_upload_data_only)) },
            text = { androidx.compose.material3.Text(uiText(R.string.sync_upload_data_only_hint) + "\n\n" + text,
                androidx.compose.ui.Modifier.appDialogBody().verticalScroll(rememberScrollState())) },
            confirmButton = { com.hos.rushdpatients.ui.components.AppButton(onClick = viewModel::confirmPendingPublication) {
                androidx.compose.material3.Text(uiText(R.string.sync_publish_confirm))
            } },
            dismissButton = { com.hos.rushdpatients.ui.components.AppTextButton(onClick = viewModel::dismissPublicationReview) {
                androidx.compose.material3.Text(uiText(R.string.ui_cancel))
            } })
    }

}
