package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiElevation
import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

data class AppTabItem(val label: String, val icon: ImageVector? = null)

@Composable
fun AppTabBar(items: List<AppTabItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding()
        .padding(horizontal = UiSpacing.section, vertical = UiSpacing.small), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.widthIn(max = UiLayout.navigationBarMaxWidth).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            border = UiBorder.stroke(), shadowElevation = UiElevation.navigation) {
            Row(Modifier.padding(horizontal = UiSpacing.tiny).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(Modifier.weight(1f).heightIn(min = UiSpacing.touchTarget).clip(MaterialTheme.shapes.extraLarge)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = UiSpacing.small), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        item.icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(UiSize.iconMedium)) }
                        Text(item.label, Modifier.weight(1f, fill = false).padding(start = if (item.icon == null) UiSpacing.micro else UiSpacing.small),
                            textAlign = TextAlign.Center, color = color, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
