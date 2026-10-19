package com.hos.rushdpatients.ui.ward

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
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
    AppNotice(message = buildString {
        if (!online) append("غير متصل · تُعرض البيانات المحلية\n")
        append(freshnessLabel)
    }, kind = kind, icon = icon, compact = compact,
        actionLabel = if (onDetails == null) null else "تفاصيل المزامنة", onAction = onDetails)
}

internal fun patientSyncNoticeKind(status: PatientSyncStatus, online: Boolean): NoticeKind {
    return when {
        !online || status == PatientSyncStatus.PENDING -> NoticeKind.WARNING
        status == PatientSyncStatus.BACKED_UP -> NoticeKind.SUCCESS
        else -> NoticeKind.INFO
    }
}
