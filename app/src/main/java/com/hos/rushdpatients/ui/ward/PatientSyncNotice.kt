package com.hos.rushdpatients.ui.ward

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.data.model.PatientSyncStatus
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.NoticeKind

@Composable
internal fun PatientSyncNotice(
    status: PatientSyncStatus,
    freshnessLabel: String,
    online: Boolean,
    conflictCount: Int = 0,
    onDetails: (() -> Unit)? = null
) {
    val kind = when {
        status == PatientSyncStatus.CONFLICT || conflictCount > 0 -> NoticeKind.ERROR
        !online || status == PatientSyncStatus.PENDING -> NoticeKind.WARNING
        status == PatientSyncStatus.BACKED_UP -> NoticeKind.SUCCESS
        else -> NoticeKind.INFO
    }
    val icon = if (!online) Icons.Filled.WifiOff else when (status) {
        PatientSyncStatus.LOCAL -> Icons.Filled.Save
        PatientSyncStatus.PENDING -> Icons.Filled.CloudUpload
        PatientSyncStatus.BACKED_UP -> Icons.Filled.CloudDone
        PatientSyncStatus.SYNCING -> Icons.Filled.Sync
        PatientSyncStatus.CONFLICT -> Icons.Filled.Warning
    }
    AppNotice(message = buildString {
        if (!online) append("غير متصل · تُعرض البيانات المحلية\n")
        append(freshnessLabel)
        if (conflictCount > 0) append("\n$conflictCount تعارضات تحتاج المراجعة")
    }, kind = kind, icon = icon,
        actionLabel = if (onDetails == null) null else "تفاصيل المزامنة", onAction = onDetails)
}
