package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    complete: Boolean? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.primary
) {
    val interaction = if (onToggle != null) Modifier.heightIn(min = UiSpacing.touchTarget)
        .clickable(role = Role.Button, onClick = onToggle) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().then(interaction).padding(UiPadding.compact)
            .semantics {
                heading()
                if (expanded != null) stateDescription = if (expanded) "موسع" else "مطوي"
                else if (complete == true) stateDescription = "مكتمل"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            color = titleColor)
        count?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (complete == true) Icon(Icons.Filled.Check, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        if (expanded != null) Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppSection(
    title: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.fillMaxWidth().padding(UiPadding.content),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppSectionHeader(title, titleColor = titleColor)
            content()
        }
    }
}
