package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.ui.theme.ContainerColors
import com.hos.rushdpatients.ui.theme.Tone
import com.hos.rushdpatients.ui.theme.toneColors
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

enum class NoticeKind { INFO, SUCCESS, WARNING, ERROR }

/** A compact message with an optional, labelled recovery/review action. */
@Composable
fun AppNotice(
    message: String,
    modifier: Modifier = Modifier,
    kind: NoticeKind = NoticeKind.INFO,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    compact: Boolean = false
) {
    val colors = noticeColors(kind)
    val symbol = icon ?: when (kind) {
        NoticeKind.INFO -> Icons.Filled.Info
        NoticeKind.SUCCESS -> Icons.Filled.CheckCircle
        NoticeKind.WARNING -> Icons.Filled.Warning
        NoticeKind.ERROR -> Icons.Filled.ErrorOutline
    }
    Surface(modifier = modifier.fillMaxWidth().semantics { liveRegion = if (kind == NoticeKind.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.small, color = colors.container, contentColor = colors.content) {
        Row(Modifier.fillMaxWidth().padding(if (compact) UiPadding.compact else UiPadding.content),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            Icon(symbol, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.bodySmall)
                if (actionLabel != null && onAction != null) {
                    AppTextButton(onClick = onAction, enabled = actionEnabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.content)) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
fun noticeColors(kind: NoticeKind): ContainerColors = toneColors(when (kind) {
    NoticeKind.INFO -> Tone.NEUTRAL
    NoticeKind.SUCCESS -> Tone.SUCCESS
    NoticeKind.WARNING -> Tone.WARNING
    NoticeKind.ERROR -> Tone.URGENT
})
