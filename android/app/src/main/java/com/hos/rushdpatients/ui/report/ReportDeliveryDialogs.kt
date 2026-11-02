package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.report.DeliveryResolution
import com.hos.rushdpatients.domain.report.DeliveryStatus
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Receipt decisions and their exact-artifact checks remain with the workspace/controller. */
@Composable
internal fun ReportDeliveryLogDialog(
    records: List<ReportDeliveryRecord>, busy: Boolean,
    onResolveDelivery: (ReportDeliveryRecord, DeliveryResolution) -> Unit,
    onAllowCopy: (ReportDeliveryRecord) -> Unit, onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText(R.string.ui_report_preview_screen_081)) }, text = {
        Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            Text(uiText(R.string.ui_report_preview_screen_082))
            if (records.isEmpty()) Text(uiText(R.string.ui_report_preview_screen_083))
            records.asReversed().forEach { row ->
                val label = when (row.status) { DeliveryStatus.UNKNOWN_COPY_ALLOWED -> uiText(R.string.delivery_uncertain_copy_allowed); DeliveryStatus.UNKNOWN -> uiText(R.string.ui_report_preview_screen_084); DeliveryStatus.SENT -> uiText(R.string.ui_report_preview_screen_085); else -> uiText(R.string.ui_report_preview_screen_086) }
                Text("${row.label} • ${row.format} • $label")
                Text(java.time.Instant.ofEpochMilli(row.at).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    style = MaterialTheme.typography.bodySmall)
                if (row.status == DeliveryStatus.UNKNOWN || row.status == DeliveryStatus.UNKNOWN_COPY_ALLOWED) {
                    DeliveryQuestion(row, !busy, onResolveDelivery)
                } else if (row.status == DeliveryStatus.SENT) {
                    Text(uiText(R.string.ui_report_preview_screen_092), style = MaterialTheme.typography.bodySmall)
                    AppOutlinedButton(onClick = { onAllowCopy(row) }, enabled = !busy) {
                        Text(uiText(R.string.ui_report_preview_screen_089))
                    }
                }
            }
        }
    }, confirmButton = { AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_close)) } })
}

@Composable
internal fun ReportDeliveryDecisionDialog(
    row: ReportDeliveryRecord, decision: DeliveryResolution, busy: Boolean,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    val instruction = when (decision) {
        DeliveryResolution.RECEIVED -> uiText(R.string.ui_report_preview_screen_090)
        DeliveryResolution.NOT_RECEIVED -> uiText(R.string.ui_report_preview_screen_091)
        DeliveryResolution.ALLOW_COPY -> uiText(R.string.ui_report_preview_screen_092)
    }
    AppAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText(R.string.ui_report_preview_screen_093)) },
        text = { Text(row.label + "\n" + instruction) },
        confirmButton = { AppButton(onClick = onConfirm, enabled = !busy) { Text(uiText(R.string.ui_report_preview_screen_094)) } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_cancel)) } })
}

@Composable
internal fun DeliveryQuestion(row: ReportDeliveryRecord, enabled: Boolean,
    resolve: (ReportDeliveryRecord, DeliveryResolution) -> Unit) {
    val uiText = rememberUiStrings()
    Text(uiText(R.string.delivery_inline_question))
    com.hos.rushdpatients.ui.components.AppButton(style = com.hos.rushdpatients.ui.components.AppButtonStyle.TONAL, onClick = { resolve(row, DeliveryResolution.RECEIVED) }, enabled = enabled) { Text(uiText(R.string.delivery_received_explicit)) }
    com.hos.rushdpatients.ui.components.AppButton(style = com.hos.rushdpatients.ui.components.AppButtonStyle.TONAL, onClick = { resolve(row, DeliveryResolution.NOT_RECEIVED) }, enabled = enabled) { Text(uiText(R.string.delivery_not_received_explicit)) }
    if (row.status == DeliveryStatus.UNKNOWN) {
        com.hos.rushdpatients.ui.components.AppButton(style = com.hos.rushdpatients.ui.components.AppButtonStyle.TONAL, onClick = { resolve(row, DeliveryResolution.ALLOW_COPY) }, enabled = enabled) { Text(uiText(R.string.delivery_allow_copy_explicit)) }
    }
}
