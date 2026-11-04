package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.hos.rushdpatients.ui.components.AppIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.UiSpacing

/** A tap opens confirmation/review, never publishes directly. Long press remains an equivalent shortcut. */
@Composable
internal fun WardTransferBar(fetchEnabled: Boolean, reportEnabled: Boolean,
    onFetch: () -> Unit, onReport: () -> Unit,
    uploadEnabled: Boolean = false, onUpload: () -> Unit = {}) {
    val uiText = rememberUiStrings()
    Row(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        val actionModifier = Modifier.weight(1f).fillMaxHeight()
        TransferAction(uiText(R.string.ui_patient_fetch_reminder_003), AppIcons.CloudDownload, fetchEnabled, onFetch, actionModifier)
        TransferAction(uiText(R.string.sync_upload_data_only), AppIcons.CloudUpload, uploadEnabled, onUpload, actionModifier)
        TransferAction(uiText(R.string.ui_report_send_confirmation_001), AppIcons.PictureAsPdf, reportEnabled, onReport, actionModifier)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransferAction(label: String, icon: ImageVector, enabled: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant) {
        Box(Modifier.combinedClickable(enabled = enabled, role = Role.Button,
            onClickLabel = label, onClick = onOpen,
            onLongClickLabel = label, onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen()
            }).heightIn(min = UiSpacing.touchTarget).padding(UiSpacing.small),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.matchParentSize(),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 0.10f else 0.06f))
            Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
