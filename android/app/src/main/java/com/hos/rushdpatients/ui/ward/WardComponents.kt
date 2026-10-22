package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiTypography
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiOpacity
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
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
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

private enum class WardDestination(
    @StringRes val label: Int, val outlined: ImageVector, val filled: ImageVector
) {
    PATIENTS(R.string.ui_patients, Icons.Outlined.ViewAgenda, Icons.Filled.ViewAgenda),
    DASHBOARD(R.string.ui_ward_components_002, Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    ACTIVITY(R.string.ui_ward_navigation_chrome_003, Icons.Outlined.History, Icons.Filled.History);

    fun selected(view: WardViewMode): Boolean = when (this) {
        PATIENTS -> view == WardViewMode.ALL || view == WardViewMode.MINE
        DASHBOARD -> view == WardViewMode.DASHBOARD
        ACTIVITY -> view == WardViewMode.ACTIVITY
    }
}

private fun wardDestinations(isAdmin: Boolean) = WardDestination.values().filter {
    isAdmin || it != WardDestination.ACTIVITY
}

@Composable
internal fun PrimaryNavigationBar(
    viewMode: WardViewMode, isAdmin: Boolean,
    onPatients: () -> Unit, onDashboard: () -> Unit, onActivity: () -> Unit
) {
    val uiText = rememberUiStrings()
    val destinations = wardDestinations(isAdmin)
    val actions = listOf(onPatients, onDashboard, onActivity)
    AppTabBar(items = destinations.map { AppTabItem(uiText(it.label), it.outlined, it.filled) },
        selectedIndex = destinations.indexOfFirst { it.selected(viewMode) },
        onSelect = { actions[destinations[it].ordinal]() })
}

@Composable
internal fun PrimaryNavigationRail(
    viewMode: WardViewMode, isAdmin: Boolean,
    onPatients: () -> Unit, onDashboard: () -> Unit, onActivity: () -> Unit
) {
    val uiText = rememberUiStrings()
    val actions = listOf(onPatients, onDashboard, onActivity)
    NavigationRail {
        wardDestinations(isAdmin).forEach { destination ->
            val selected = destination.selected(viewMode)
            NavigationRailItem(selected = selected, onClick = actions[destination.ordinal],
                icon = { Icon(if (selected) destination.filled else destination.outlined, contentDescription = null) },
                label = { Text(uiText(destination.label)) })
        }
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
    val uiText = rememberUiStrings()
    CompactDrawerItem(
        label = { Text(label, fontWeight = UiTypography.emphasizedWeight) },
        selected = false,
        onClick = onClick,
        badge = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) uiText(R.string.ui_ward_drawer_030, label) else uiText(R.string.ui_ward_drawer_031, label)
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
        else MaterialTheme.colorScheme.onSurface).let { if (enabled) it else it.copy(alpha = UiOpacity.disabledContent) }
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
