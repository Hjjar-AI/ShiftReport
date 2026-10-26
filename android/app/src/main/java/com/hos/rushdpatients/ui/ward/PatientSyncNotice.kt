package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.NoticeKind

@Composable
internal fun PatientSyncNotice(
    status: PatientSyncStatus,
    freshnessLabel: String,
    online: Boolean,
    onDetails: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val uiText = rememberUiStrings()
    val kind = patientSyncNoticeKind(status, online)
    val icon = if (!online) AppIcons.WifiOff else when (status) {
        PatientSyncStatus.LOCAL -> AppIcons.Save
        PatientSyncStatus.PENDING -> AppIcons.CloudUpload
        PatientSyncStatus.BACKED_UP -> AppIcons.CloudDone
        PatientSyncStatus.SYNCING -> AppIcons.Sync
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canShowDetails = freshnessLabel != status.arabicLabel(uiText)
    Column {
        AppNotice(message = buildString {
            if (!online) append(uiText(R.string.ui_patient_sync_notice_001))
            append(status.arabicLabel(uiText))
        }, kind = kind, icon = icon, compact = compact,
            actionLabel = when {
                canShowDetails -> if (expanded) uiText(R.string.ui_patient_card_027) else uiText(R.string.ui_patient_sync_notice_003)
                onDetails != null -> uiText(R.string.ui_patient_sync_notice_002)
                else -> null
            }, onAction = when {
                canShowDetails -> ({ expanded = !expanded })
                onDetails != null -> onDetails
                else -> null
            })
        if (expanded) {
            Text(freshnessLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDetails != null) AppTextButton(onClick = onDetails) {
                Text(uiText(R.string.ui_patient_sync_notice_002))
            }
        }
    }

}

internal fun patientSyncNoticeKind(status: PatientSyncStatus, online: Boolean): NoticeKind {
    return when {
        !online || status == PatientSyncStatus.PENDING -> NoticeKind.WARNING
        status == PatientSyncStatus.BACKED_UP -> NoticeKind.SUCCESS
        else -> NoticeKind.INFO
    }
}
