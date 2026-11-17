package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    complete: Boolean? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    summary: String? = null,
    icon: ImageVector? = null
) {
    val uiText = rememberUiStrings()
    val interaction = if (onToggle != null) Modifier.heightIn(min = UiSpacing.touchTarget)
        .clickable(role = Role.Button, onClick = onToggle) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().then(interaction).padding(UiPadding.compact)
            .semantics(mergeDescendants = true) {
                heading()

            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.screen)
    ) {
        icon?.let { Icon(it, contentDescription = null,
            modifier = Modifier.size(com.hos.rushdpatients.ui.theme.UiSize.iconMedium), tint = titleColor) }
        if (summary == null) {
            AppText(title, modifier = Modifier.weight(1f), role = AppTextRole.HEADING, color = titleColor)
        } else {
            FlowRow(modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                AppText(title, role = AppTextRole.HEADING, color = titleColor)
                AppText(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        count?.let { AppText(uiNumber(it), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (complete == true) Icon(AppIcons.Check, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        if (expanded != null) Icon(if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppSection(
    title: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    AppSectionContainer(modifier = modifier, colors = colors,
        header = { AppSectionHeader(title, titleColor = titleColor) }, content = content)
}
