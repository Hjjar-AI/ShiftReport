package com.hos.rushdpatients.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import com.hos.rushdpatients.ui.theme.UiElevation
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
            border = UiBorder.stroke(), shadowElevation = UiElevation.navigation) {
            Row(Modifier.padding(UiSpacing.small).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val indicator by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(160), label = "navigationIndicator")
                    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(1f).heightIn(min = UiSpacing.touchTarget)
                        .clip(MaterialTheme.shapes.medium)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(vertical = UiSpacing.small, horizontal = UiSpacing.small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        (if (selected) item.selectedIcon else item.icon)?.let { icon ->
                            Box(Modifier.clip(MaterialTheme.shapes.extraLarge).background(indicator)
                                .padding(horizontal = UiSpacing.section, vertical = UiSpacing.tiny),
                                contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else color,
                                    modifier = Modifier.size(UiSize.icon))
                            }
                        }
                        Text(item.label, textAlign = TextAlign.Center, color = color,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}
