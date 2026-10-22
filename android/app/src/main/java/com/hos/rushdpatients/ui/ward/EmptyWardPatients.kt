package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.components.AppDestinationCard
import com.hos.rushdpatients.ui.theme.UiSpacing

/** One manual-entry action, then clearly explained sources; mutations stay caller-owned. */
@Composable
internal fun EmptyWardPatients(isReadOnly: Boolean, busy: Boolean, canCopy: Boolean,
    bottomClearance: Dp, onAdd: () -> Unit, onFetch: () -> Unit, onCopyPrevious: () -> Unit,
    showSources: Boolean = true, topContent: @Composable () -> Unit = {}) {
    val uiText = rememberUiStrings()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = com.hos.rushdpatients.ui.theme.UiLayout.emptyWardMaxWidth)
            .fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = UiSpacing.screenGutter, vertical = UiSpacing.section)
            .padding(bottom = bottomClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
            topContent()
            EmptyState(
                title = if (isReadOnly) uiText(R.string.ui_empty_ward_patients_001) else uiText(R.string.ui_empty_ward_patients_002),
                subtitle = if (isReadOnly) uiText(R.string.ui_empty_ward_patients_003) else uiText(R.string.ui_empty_ward_patients_004),
                icon = AppIcons.ViewAgenda,
                actionLabel = if (isReadOnly) null else uiText(R.string.ui_empty_ward_patients_005),
                onAction = if (isReadOnly) null else onAdd, actionEnabled = !busy)
            if (!isReadOnly && showSources) {
                Text(uiText(R.string.ui_empty_ward_patients_006), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(uiText(R.string.ui_empty_ward_patients_007), modifier = Modifier.fillMaxWidth()
                    .padding(top = UiSpacing.screen).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall)
                AppDestinationCard(title = uiText(R.string.ui_empty_ward_patients_008), icon = AppIcons.CloudDownload,
                        summary = uiText(R.string.ui_empty_ward_patients_009),
                        enabled = !busy, onClick = onFetch)
                AppDestinationCard(title = uiText(R.string.ui_empty_ward_patients_010), icon = AppIcons.History,
                        summary = uiText(R.string.ui_empty_ward_patients_011),
                        enabled = !busy && canCopy, onClick = onCopyPrevious)
            }
        }
    }
}
