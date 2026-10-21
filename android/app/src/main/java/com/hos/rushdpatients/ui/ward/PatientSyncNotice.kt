package com.hos.rushdpatients.ui.ward

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    val kind = patientSyncNoticeKind(status, online)
    val icon = if (!online) Icons.Filled.WifiOff else when (status) {
        PatientSyncStatus.LOCAL -> Icons.Filled.Save
        PatientSyncStatus.PENDING -> Icons.Filled.CloudUpload
        PatientSyncStatus.BACKED_UP -> Icons.Filled.CloudDone
        PatientSyncStatus.SYNCING -> Icons.Filled.Sync
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canShowDetails = freshnessLabel != status.arabicLabel
    Column {
        AppNotice(message = buildString {
            if (!online) append("غير متصل · تُعرض البيانات المحلية\n")
            append(status.arabicLabel)
        }, kind = kind, icon = icon, compact = compact,
            actionLabel = when {
                onDetails != null -> "تفاصيل البيانات"
                canShowDetails -> if (expanded) "طي التفاصيل" else "التفاصيل"
                else -> null
            }, onAction = when {
                onDetails != null -> onDetails
                canShowDetails -> ({ expanded = !expanded })
                else -> null
            })
        if (expanded) Text(freshnessLabel, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

}

internal fun patientSyncNoticeKind(status: PatientSyncStatus, online: Boolean): NoticeKind {
    return when {
        !online || status == PatientSyncStatus.PENDING -> NoticeKind.WARNING
        status == PatientSyncStatus.BACKED_UP -> NoticeKind.SUCCESS
        else -> NoticeKind.INFO
    }
}
