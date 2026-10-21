package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppCompactTopBar
import com.hos.rushdpatients.ui.components.uiNumber
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WardHeader(
    state: WardUiState, viewMode: WardViewMode, patientDestination: Boolean,
    anyCardsExpanded: Boolean, foldingFeature: androidx.window.layout.FoldingFeature?,
    showAddAction: Boolean,
    onMenu: () -> Unit, onToggleCards: () -> Unit, onAdd: () -> Unit,
    onSearch: () -> Unit, onWorkflow: () -> Unit
) {
    val uiText = rememberUiStrings()
    Column {
    WardToolbarLayout(foldingFeature) {
        AppCompactTopBar(title = {
            Text(when (viewMode) {
                WardViewMode.ALL, WardViewMode.MINE -> uiText(R.string.ui_ward_navigation_chrome_001, uiNumber(state.patients.size))
                WardViewMode.DASHBOARD -> uiText(R.string.ui_ward_navigation_chrome_002)
                WardViewMode.ACTIVITY -> uiText(R.string.ui_ward_navigation_chrome_003)
            }, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            state.shift?.date?.let { Text(uiNumber(it), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }, navigation = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = uiText(R.string.ui_ward_navigation_chrome_004))
            }
        }, actions = {
            if (patientDestination && state.patients.isNotEmpty()) {
                IconButton(onClick = onToggleCards) {
                    Icon(if (anyCardsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (anyCardsExpanded) uiText(R.string.ui_ward_navigation_chrome_005) else uiText(R.string.ui_ward_navigation_chrome_006))
                }
            }
            if (showAddAction && !state.isReadOnly && (!patientDestination || state.patients.isNotEmpty())) {
                IconButton(onClick = onAdd, enabled = state.shift != null &&
                    !state.loading && !state.saving && !state.syncing) {
                    Icon(Icons.Filled.Add, contentDescription = uiText(R.string.ui_add_patient))
                }
            }
            if (!patientDestination || state.patients.isNotEmpty()) IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = uiText(R.string.ui_ward_navigation_chrome_007)) }
        })
    }
    if (state.syncing || state.deliveryLogUnavailable || state.unresolvedDeliveries > 0) WardWorkflowStrip(state, onNext = onWorkflow)
    }
}

@Composable
internal fun WardBottomNavigation(expandedWindow: Boolean,
    foldingFeature: androidx.window.layout.FoldingFeature?, viewMode: WardViewMode,
    isAdmin: Boolean, onDestination: (WardViewMode) -> Unit) {
    if (!expandedWindow) {
        WardToolbarLayout(foldingFeature) {
            PrimaryNavigationBar(viewMode = viewMode, isAdmin = isAdmin,
                onPatients = { onDestination(WardViewMode.ALL) },
                onDashboard = { onDestination(WardViewMode.DASHBOARD) },
                onActivity = { onDestination(WardViewMode.ACTIVITY) })
        }
    }
}
