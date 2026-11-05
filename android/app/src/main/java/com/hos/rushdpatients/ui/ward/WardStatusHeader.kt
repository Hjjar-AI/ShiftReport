package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Status presentation around the existing header and reviewed transfer actions. */
@Composable
internal fun WardStatusHeader(
    state: WardUiState, isAdmin: Boolean, liveProject: Boolean,
    showBackOnline: Boolean, fetchReminderEnabled: Boolean,
    onOpenDoctors: () -> Unit, onReviewPatients: () -> Unit,
    onRetry: () -> Unit, onReload: () -> Unit, onFetch: () -> Unit,
    onBackToPreview: (() -> Unit)?,
    header: @Composable () -> Unit, transferActions: @Composable () -> Unit
) {
    val uiText = rememberUiStrings()
    Column {
        header()
        state.syncIssue?.let { issue ->
            val recovery = state.syncRecovery
            val doctorAction = recovery == WardSyncRecovery.PUBLISH_DOCTORS || recovery == WardSyncRecovery.FETCH_DOCTORS
            AppNotice(message = when (recovery) {
                WardSyncRecovery.PUBLISH_DOCTORS -> uiText(R.string.ui_recovery_doctors_publish)
                WardSyncRecovery.FETCH_DOCTORS -> uiText(R.string.ui_recovery_doctors_fetch)
                WardSyncRecovery.PUBLISH_PATIENTS -> uiText(R.string.ui_recovery_patients_publish)
                WardSyncRecovery.SELECT_SHIFT_DOCTORS -> issue
                null -> issue
            } + if (doctorAction && !isAdmin) "\n" + uiText(R.string.ui_recovery_ask_admin) else "",
                kind = NoticeKind.WARNING,
                actionLabel = when {
                    doctorAction && isAdmin -> uiText(R.string.ui_recovery_open_doctors)
                    recovery == WardSyncRecovery.PUBLISH_PATIENTS -> uiText(R.string.ui_recovery_review_patients)
                    recovery == WardSyncRecovery.SELECT_SHIFT_DOCTORS -> uiText(R.string.ui_report_preview_screen_022)
                    recovery == null && state.retryAction != null -> uiText(R.string.ui_retry)
                    else -> null
                },
                onAction = when {
                    doctorAction && isAdmin -> onOpenDoctors
                    recovery == WardSyncRecovery.PUBLISH_PATIENTS -> onReviewPatients
                    recovery == WardSyncRecovery.SELECT_SHIFT_DOCTORS -> onReviewPatients
                    recovery == null && state.retryAction != null -> onRetry
                    else -> null
                }, actionEnabled = !state.syncing && !state.saving)
            if (recovery != null) AppTextButton(onClick = onRetry,
                enabled = !state.syncing && !state.saving) {
                Text(uiText(R.string.ui_recovery_retry_after_fix))
            }
        }
        onBackToPreview?.let { back -> AppTextButton(onClick = back) { Text(uiText(R.string.report_readiness_return)) } }
        if (liveProject && showBackOnline) AppNotice(
            uiText(R.string.back_online_fetch), actionLabel = uiText(R.string.ui_patient_fetch_reminder_003),
            onAction = onFetch,
            actionEnabled = fetchReminderEnabled)
        transferActions()
        if (state.shift != null && state.loading) AppLoadingState(
            uiText(R.string.ui_ward_screen_006),
            size = AppLoadingSize.INLINE)
        if (state.shift != null && state.error != null) AppNotice(
            message = uiText(R.string.ui_ward_refresh_retained, state.error.orEmpty()),
            kind = NoticeKind.ERROR, compact = true,
            actionLabel = uiText(R.string.ui_retry), onAction = onReload,
            actionEnabled = !state.loading && !state.syncing && !state.saving)
        if (state.syncing || state.exportingCsv) AppLoadingState(
            uiText(if (state.syncing) state.syncProgressRes else R.string.ui_progress_export_csv),
            modifier = Modifier.padding(horizontal = UiSpacing.section),
            size = AppLoadingSize.INLINE)
        if (!liveProject) AppNotice(uiText(R.string.ui_demo_navigation_001),
            kind = NoticeKind.WARNING, icon = AppIcons.Info,
            modifier = Modifier.fillMaxWidth().padding(UiPadding.screen))
    }
}
