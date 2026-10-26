package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun AppBusyIndicator(modifier: Modifier = Modifier, size: Dp = UiSize.iconSmall,
    color: Color = LocalContentColor.current) {
    CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = UiSize.progressStroke)
}

/** Reserve the progress slot in both states so busy transitions do not move the label. */
@Composable
fun AppActionContent(text: String, loading: Boolean, color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.labelLarge) {
    val uiText = rememberUiStrings()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
        modifier = Modifier.semantics { if (loading) stateDescription = uiText(R.string.ui_app_progress_001) }) {
        Box(Modifier.size(UiSize.iconSmall), contentAlignment = Alignment.Center) {
            if (loading) AppBusyIndicator(color = color)
        }
        Text(text, color = color, style = style)
    }
}
