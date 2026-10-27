package com.hos.rushdpatients.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.sync.PermanentAdminTransferReview
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun TransferAdminDialog(candidates: List<Doctor>, saving: Boolean,
    onConfirm: (Doctor) -> Unit, onDismiss: () -> Unit) {
    val uiText = rememberUiStrings()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = candidates.firstOrNull { it.id == selectedId }
    AppAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiText(R.string.admin_transfer_title)) },
        text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(uiText(R.string.admin_transfer_description))
                if (candidates.isEmpty()) Text(uiText(R.string.admin_transfer_no_candidates))
                candidates.forEach { doctor ->
                    AppChoiceRow(label = uiText(R.string.admin_transfer_candidate, doctor.fullName, doctor.telegramId.toString()), selected = selectedId == doctor.id,
                        onClick = { selectedId = doctor.id }, enabled = !saving)
                }
            }
        },
        confirmButton = {
            AppButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null && !saving) {
                Text(uiText(R.string.admin_transfer_review))
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !saving) { Text(uiText(R.string.ui_cancel)) } }
    )
}

@Composable
fun TransferAdminReviewDialog(review: PermanentAdminTransferReview, saving: Boolean,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val uiText = rememberUiStrings()
    AppAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiText(R.string.admin_transfer_title)) },
        text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(uiText(R.string.admin_transfer_review_body, review.outgoingName, review.recipientName, review.recipientTelegramId.toString()))
                Text(uiText(R.string.admin_transfer_client_notice))
            }
        },
        confirmButton = { AppButton(onClick = onConfirm, enabled = !saving) { Text(uiText(R.string.admin_transfer_confirm)) } },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !saving) { Text(uiText(R.string.ui_cancel)) } }
    )
}
