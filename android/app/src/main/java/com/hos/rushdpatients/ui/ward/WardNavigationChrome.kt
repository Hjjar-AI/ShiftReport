package com.hos.rushdpatients.ui.ward

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
    onMenu: () -> Unit, onToggleCards: () -> Unit, onAdd: () -> Unit,
    onSearch: () -> Unit, onWorkflow: () -> Unit
) {
    Column {
    WardToolbarLayout(foldingFeature) {
        AppCompactTopBar(title = {
            Text(when (viewMode) {
                WardViewMode.ALL, WardViewMode.MINE -> "المرضى · ${uiNumber(state.patients.size)}"
                WardViewMode.DASHBOARD -> "تسليم المناوبة"
                WardViewMode.ACTIVITY -> "النشاط"
            }, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            state.shift?.date?.let { Text(uiNumber(it), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }, navigation = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "القائمة")
            }
        }, actions = {
            if (patientDestination && state.patients.isNotEmpty()) {
                IconButton(onClick = onToggleCards) {
                    Icon(if (anyCardsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (anyCardsExpanded) "طي جميع بطاقات المرضى" else "توسيع جميع بطاقات المرضى")
                }
            }
            if (!state.isReadOnly && (!patientDestination || state.patients.isNotEmpty())) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "إضافة مريض")
                }
            }
            if (!patientDestination || state.patients.isNotEmpty()) IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "بحث عن مريض") }
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
