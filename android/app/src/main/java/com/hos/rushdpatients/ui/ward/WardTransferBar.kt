package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
    onFetch: () -> Unit, onReport: () -> Unit) {
    val uiText = rememberUiStrings()
    Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.micro)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            TransferAction(uiText(R.string.ui_patient_fetch_reminder_003), fetchEnabled, onFetch, Modifier.weight(1f))
            TransferAction(uiText(R.string.ui_report_send_confirmation_001), reportEnabled, onReport, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransferAction(label: String, enabled: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    Surface(modifier = modifier.fillMaxHeight(), shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.fillMaxHeight().combinedClickable(enabled = enabled, role = Role.Button,
            onClickLabel = label, onClick = onOpen,
            onLongClickLabel = label, onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen()
            }).heightIn(min = UiSpacing.touchTarget).padding(UiSpacing.small),
            verticalArrangement = Arrangement.Center) {
            Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
