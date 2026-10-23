package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.domain.report.ReportReadiness

@Composable
internal fun WardSupportingPane(
    state: WardUiState,
    freshnessLabel: String,
    online: Boolean,
    isAdmin: Boolean,
    onReviewReport: () -> Unit,
    onActivity: () -> Unit,
    showSynchronization: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiText = rememberUiStrings()
    val warnings = ReportReadiness.warnings(state.patients)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
        Text(if (isAdmin) uiText(R.string.ui_ward_screen_004) else uiText(R.string.ui_ward_supporting_pane_001), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Text(uiText(R.string.ui_ward_drawer_002, uiNumber(state.patients.size)))
        if (showSynchronization) PatientSyncNotice(state.syncStatus, freshnessLabel, online)
        if (warnings.isEmpty()) Text(uiText(R.string.ui_ward_supporting_pane_002))
        warnings.forEach { AppNotice(it, kind = NoticeKind.WARNING) }
        AppButton(onClick = onReviewReport, enabled = state.shift != null, modifier = Modifier.fillMaxWidth()) {
            Text(uiText(R.string.ui_ward_supporting_pane_003))
        }
        if (isAdmin) {
            AppDivider()
            Text(uiText(R.string.ui_ward_supporting_pane_004), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            state.activityError?.let { AppNotice(it, kind = NoticeKind.ERROR,
                actionLabel = uiText(R.string.ui_ward_supporting_pane_005), onAction = onActivity) }
            val recent = state.recentActivity.filter { it.patientId != null }.take(5)
            if (recent.isEmpty()) Text(uiText(R.string.ui_ward_supporting_pane_006))
            recent.forEach { entry ->
                ActivityEntry(entry, state.patients.firstOrNull { it.id == entry.patientId }?.name)
                AppDivider()
            }
            AppTextButton(onClick = onActivity) { Text(uiText(R.string.ui_ward_supporting_pane_005)) }
        }
        Text(uiText(R.string.ui_ward_supporting_pane_007), style = MaterialTheme.typography.bodySmall)
    }
}
