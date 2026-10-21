package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun ReportSendConfirmation(destinations: List<String>, format: String, warningCount: Int,
    enabled: Boolean, unavailableReason: String? = null, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val uiText = rememberUiStrings()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(uiText(R.string.ui_report_send_confirmation_001)) }, text = {
        Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            unavailableReason?.let { AppNotice(it, kind = NoticeKind.WARNING) }
            Text(uiText(R.string.ui_report_send_confirmation_002, format))
            Text(uiText(R.string.ui_report_send_confirmation_003))
            destinations.forEach { Text("• $it") }
            Text(uiText(R.string.ui_report_send_confirmation_004))
            AppNotice(uiText(R.string.ui_report_send_confirmation_005), kind = NoticeKind.WARNING)
            Text(uiText(R.string.ui_report_send_confirmation_006))
            if (warningCount > 0) AppNotice(uiText(R.string.ui_report_send_confirmation_007, uiNumber(warningCount)), kind = NoticeKind.WARNING)
        }
    }, confirmButton = { AppButton(onClick = { onConfirm(true) }, enabled = enabled) { Text(uiText(R.string.ui_report_send_confirmation_008)) } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_cancel)) } })
}
