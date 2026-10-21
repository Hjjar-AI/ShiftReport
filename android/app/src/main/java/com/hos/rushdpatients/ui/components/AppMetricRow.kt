package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun AppMetricRow(label: String, count: Int, compact: Boolean = false, kind: NoticeKind = NoticeKind.INFO,
    onClick: () -> Unit) {
    val colors = noticeColors(kind)
    val urgent = kind == NoticeKind.ERROR || kind == NoticeKind.WARNING
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
        .clickable(role = Role.Button, onClickLabel = "عرض $label", onClick = onClick),
        shape = MaterialTheme.shapes.medium, color = if (urgent) colors.first else MaterialTheme.colorScheme.surface,
        contentColor = if (urgent) colors.second else MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(UiPadding.content), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            Text(label, Modifier.weight(1f), style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium)
            Text(uiNumber(count), color = if (urgent) colors.second else MaterialTheme.colorScheme.primary, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
        }
    }
}
