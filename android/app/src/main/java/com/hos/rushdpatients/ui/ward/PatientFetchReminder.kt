package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/** Advisory only: fetching still uses the existing confirmation and local-edit guards. */
@Composable
internal fun PatientFetchReminder(enabled: Boolean, onFetch: () -> Unit, onDismiss: () -> Unit) {
    val uiText = rememberUiStrings()
    com.hos.rushdpatients.ui.components.AppNotice(
        message = uiText(R.string.ui_patient_fetch_reminder_002),
        kind = com.hos.rushdpatients.ui.components.NoticeKind.ACCENT,
        icon = AppIcons.CloudDownload,
        actionLabel = uiText(R.string.ui_patient_fetch_reminder_003),
        onAction = onFetch, actionEnabled = enabled, compact = true,
        onDismiss = onDismiss, dismissLabel = uiText(R.string.ui_patient_fetch_reminder_004))
}
