package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun ReportSendConfirmation(destinations: List<String>, format: String, warningCount: Int,
    enabled: Boolean, unavailableReason: String? = null, onConfirm: () -> Unit, onDismiss: () -> Unit, patientCount: Int = 0, exportPdf: Boolean = false) {
    val uiText = rememberUiStrings()
    AppAlertDialog(onDismissRequest = onDismiss, title = { AppText(uiText(R.string.ui_report_send_confirmation_001)) }, text = {
        Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            unavailableReason?.let { AppNotice(it, kind = NoticeKind.WARNING) }
            AppText(uiText(R.string.report_send_summary, destinations.joinToString("، "), format, uiNumber(patientCount), uiNumber(warningCount)))
            AppText(uiText(R.string.report_send_shared_copy))
            if (exportPdf) AppText(uiText(R.string.report_optional_normal_export))
        }
    }, confirmButton = { AppButton(onClick = { onConfirm() }, enabled = enabled) { AppText(uiText(R.string.ui_report_send_confirmation_008)) } },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText(uiText(R.string.ui_cancel)) } })
}
