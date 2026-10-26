package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.theme.UiTypography
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

private enum class WardDestination(
    @StringRes val label: Int, val outlined: ImageVector, val filled: ImageVector
) {
    PATIENTS(R.string.ui_patients, AppIcons.OutlinedViewAgenda, AppIcons.ViewAgenda),
    DASHBOARD(R.string.ui_ward_components_002, AppIcons.OutlinedDashboard, AppIcons.Dashboard);

    fun selected(view: WardViewMode): Boolean = when (this) {
        PATIENTS -> view == WardViewMode.ALL || view == WardViewMode.MINE || view == WardViewMode.ACTIVITY
        DASHBOARD -> view == WardViewMode.DASHBOARD
    }
}

@Suppress("UNUSED_PARAMETER")
private fun wardDestinations(isAdmin: Boolean) = WardDestination.values().toList()

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
                imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
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
    com.hos.rushdpatients.ui.components.AppSelectableSurface(selected = selected, onClick = onClick,
        enabled = enabled, modifier = modifier.fillMaxWidth()) {
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
