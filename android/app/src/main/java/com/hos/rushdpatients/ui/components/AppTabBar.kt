package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiTypography
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

data class AppTabItem(
    val label: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = icon
)

/** Compact destination dock; the whole labelled item is a selectable touch target. */
@Composable
fun AppTabBar(items: List<AppTabItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding()
        .padding(horizontal = UiSpacing.section, vertical = UiSpacing.tiny), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.widthIn(max = UiLayout.navigationBarMaxWidth).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            border = UiBorder.stroke(), shadowElevation = com.hos.rushdpatients.ui.theme.LocalUiStyle.current.navigationElevation) {
            Row(Modifier.padding(UiSpacing.tiny).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val iconScale by animateFloatAsState(if (selected) 1.05f else 1f,
                        tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.indicatorMillis), label = "tabIconScale")
                    val indicator by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.indicatorMillis), label = "navigationIndicator")
                    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(Modifier.weight(1f).heightIn(min = UiSpacing.touchTarget)
                        .clip(MaterialTheme.shapes.medium)
                        .background(indicator)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(vertical = UiSpacing.tiny, horizontal = UiSpacing.small),
                        contentAlignment = Alignment.Center) {
                        (if (selected) item.selectedIcon else item.icon)?.let { icon ->
                            Icon(icon, contentDescription = null,
                                tint = color.copy(alpha = if (selected) 0.10f else 0.07f),
                                modifier = Modifier.size(UiSize.iconXl).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                        }
                        Text(item.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = color,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) UiTypography.emphasizedWeight else FontWeight.Medium)
                    }
                }
            }
        }
    }
}
