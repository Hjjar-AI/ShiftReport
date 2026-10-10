package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressLoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val active = enabled && !loading
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .heightIn(min = UiSpacing.touchTarget)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .combinedClickable(
                enabled = active,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(UiPadding.content),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = contentColor,
                    modifier = Modifier.size(UiSize.iconSmall),
                    strokeWidth = UiSize.progressStroke
                )
            }
            Text(text, color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}
