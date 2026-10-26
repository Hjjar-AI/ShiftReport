package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
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
@Suppress("UNUSED_PARAMETER")
internal fun WardSupportingPane(
    state: WardUiState,
    freshnessLabel: String,
    online: Boolean,
    isAdmin: Boolean,
    onActivity: () -> Unit,
    showSynchronization: Boolean = true,
    onOpenSyncCenter: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiText = rememberUiStrings()
    val warnings = com.hos.rushdpatients.ui.components.readinessWarnings(uiText, state.patients)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
        Text(uiText(R.string.ui_ward_supporting_pane_001), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Text(uiText(R.string.ui_ward_drawer_002, uiNumber(state.patients.size)))
        if (showSynchronization) PatientSyncNotice(state.syncStatus, freshnessLabel, online, onDetails = onOpenSyncCenter)
        if (warnings.isEmpty()) Text(uiText(R.string.ui_ward_supporting_pane_002))
        warnings.forEach { AppNotice(it, kind = NoticeKind.WARNING) }
        Text(uiText(R.string.ui_ward_supporting_pane_007), style = MaterialTheme.typography.bodySmall)
    }
}
