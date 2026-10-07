package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onRemove,
        label = { Text("$label ×", style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@Composable
internal fun PrimaryNavigationBar(
    viewMode: WardViewMode,
    isAdmin: Boolean,
    onPatients: () -> Unit,
    onDashboard: () -> Unit,
    onActivity: () -> Unit
) {
    val items = listOf(
        Triple("المرضى", Icons.Filled.ViewAgenda, onPatients),
        Triple("اللوحة", Icons.Filled.Dashboard, onDashboard)
    ) + if (isAdmin) listOf(Triple("النشاط", Icons.Filled.History, onActivity)) else emptyList()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
            items.forEachIndexed { index, (label, icon, action) ->
                val selected = when (index) {
                    0 -> viewMode == WardViewMode.ALL || viewMode == WardViewMode.MINE
                    1 -> viewMode == WardViewMode.DASHBOARD
                    else -> viewMode == WardViewMode.ACTIVITY
                }
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = action)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Text(label, Modifier.padding(start = 6.dp), color = color,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun PrimaryNavigationRail(
    viewMode: WardViewMode,
    isAdmin: Boolean,
    onPatients: () -> Unit,
    onDashboard: () -> Unit,
    onActivity: () -> Unit
) {
    NavigationRail {
        NavigationRailItem(
            selected = viewMode == WardViewMode.ALL || viewMode == WardViewMode.MINE,
            onClick = onPatients,
            icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
            label = { Text("المرضى") }
        )
        NavigationRailItem(
            selected = viewMode == WardViewMode.DASHBOARD,
            onClick = onDashboard,
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
            label = { Text("اللوحة") }
        )
        if (isAdmin) NavigationRailItem(
            selected = viewMode == WardViewMode.ACTIVITY,
            onClick = onActivity,
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text("النشاط") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupHeader(name: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onToggle, onLongClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$name · $count",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun DrawerSubmenuHeader(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    CompactDrawerItem(
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        selected = false,
        onClick = onClick,
        badge = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "طي $label" else "فتح $label"
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

/** Compact visual row with a full 48dp minimum touch target and wrapping text. */
@Composable
internal fun CompactDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val contentColor = (if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface).let { if (enabled) it else it.copy(alpha = 0.38f) }
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = contentColor
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            icon?.invoke()
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Box(Modifier.weight(1f)) { label() }
            }
            badge?.invoke()
        }
    }
}

internal enum class WardViewMode { DASHBOARD, ALL, MINE, ACTIVITY }

