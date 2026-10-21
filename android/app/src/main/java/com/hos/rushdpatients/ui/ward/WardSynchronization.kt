package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.sync.KnownShiftState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class WardWorkflowAction(val label: String) {
    DELIVERY_HISTORY("سجل التسليم"),
    REVIEW_PUBLICATION("مراجعة ونشر"),
    DOWNLOAD("جلب البيانات")
}

/** Device publication metadata and selected-shift download metadata have different scopes. */
data class WardSynchronization(
    val pending: Boolean = false,
    val selectedSnapshot: KnownShiftState = KnownShiftState.UNKNOWN,
    val lastSynchronizedAt: Long? = null,
    val publishedAt: Long? = null,
    val downloadedAt: Long? = null,
    val downloadedMessage: Long? = null,
    val unresolvedDeliveries: Int = 0,
    val deliveryLogUnavailable: Boolean = false
) {
    fun status(syncing: Boolean): PatientSyncStatus = when {
        syncing -> PatientSyncStatus.SYNCING
        selectedSnapshot == KnownShiftState.CHANGED -> PatientSyncStatus.PENDING
        selectedSnapshot == KnownShiftState.MATCHED -> PatientSyncStatus.BACKED_UP
        else -> PatientSyncStatus.LOCAL
    }

    internal fun nextAction(readOnly: Boolean): WardWorkflowAction = when {
        deliveryLogUnavailable || unresolvedDeliveries > 0 -> WardWorkflowAction.DELIVERY_HISTORY
        (selectedSnapshot == KnownShiftState.CHANGED || (selectedSnapshot == KnownShiftState.UNKNOWN && pending)) && !readOnly -> WardWorkflowAction.REVIEW_PUBLICATION
        else -> WardWorkflowAction.DOWNLOAD
    }

    fun description(syncing: Boolean, currentMessage: Long?): String = buildString {
        append(status(syncing).arabicLabel)
        if (selectedSnapshot == KnownShiftState.UNKNOWN) append(" · حالة النشر غير معروفة")
        append("\nآخر نشر على هذا الجهاز: ")
        append(publishedAt?.let(::formatTime) ?: "غير معروف")
        append(" · آخر جلب: ")
        append(downloadedAt?.let {
            formatTime(it) + if (downloadedMessage != null && downloadedMessage == currentMessage) "" else " (نسخة سابقة)"
        } ?: "غير معروف")
    }

    private fun formatTime(value: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
}
