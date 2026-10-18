package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppTabBar
import com.hos.rushdpatients.ui.components.AppTabItem
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
        modifier = Modifier.heightIn(min = UiSpacing.touchTarget)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onRemove,
        label = { Text("$label ×", style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.heightIn(min = UiSpacing.touchTarget)
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
    val selectedIndex = when (viewMode) { WardViewMode.ALL, WardViewMode.MINE -> 0; WardViewMode.DASHBOARD -> 1; else -> 2 }
    AppTabBar(items = items.map { AppTabItem(it.first, it.second) }, selectedIndex = selectedIndex,
        onSelect = { items[it].third() })
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
    AppSectionHeader(title = name, count = count, expanded = !collapsed, onToggle = onToggle,
        modifier = Modifier.background(MaterialTheme.colorScheme.background))
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
        modifier = Modifier.padding(horizontal = UiSpacing.small)
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
        modifier = modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget).semantics { this.selected = selected },
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = contentColor
    ) {
        Row(Modifier.padding(UiPadding.content), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
            icon?.invoke()
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Box(Modifier.weight(1f)) { label() }
            }
            badge?.invoke()
        }
    }
}

internal enum class WardViewMode { DASHBOARD, ALL, MINE, ACTIVITY }
