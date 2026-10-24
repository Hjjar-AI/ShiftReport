package com.hos.rushdpatients.ui.ward

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.sync.KnownShiftState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class WardWorkflowAction(@StringRes private val labelRes: Int) {
    DELIVERY_HISTORY(R.string.ui_ward_synchronization_006),
    REVIEW_PUBLICATION(R.string.ui_ward_synchronization_007),
    DOWNLOAD(R.string.ui_ward_drawer_013);

    fun label(uiText: UiStrings): String = uiText(labelRes)
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

    fun description(uiText: UiStrings, syncing: Boolean, currentMessage: Long?): String = buildString {
        append(status(syncing).arabicLabel(uiText))
        if (selectedSnapshot == KnownShiftState.UNKNOWN) append(uiText(R.string.ui_ward_synchronization_001))
        append(uiText(R.string.ui_ward_synchronization_002))
        append(publishedAt?.let(::formatTime) ?: uiText(R.string.ui_unknown))
        append(uiText(R.string.ui_ward_synchronization_004))
        append(downloadedAt?.let {
            formatTime(it) + if (downloadedMessage != null && downloadedMessage == currentMessage) "" else uiText(R.string.ui_ward_synchronization_005)
        } ?: uiText(R.string.ui_unknown))
    }

    private fun formatTime(value: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
}
