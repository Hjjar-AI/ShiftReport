package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.ui.window.DialogProperties

/** Full-screen editor with fixed actions, keeping caller-owned drafts and hinge-safe content. */
@Composable
internal fun WardFormDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    val uiText = rememberUiStrings()
    WardFullscreenDialog(
        title = uiText(R.string.ui_ward_form_dialog_001), onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = UiLayout.patientFormMaxWidth).fillMaxWidth().fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screenGutter, vertical = UiSpacing.tiny)) { title() }
                AppDivider()
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = UiSpacing.screenGutter, vertical = UiSpacing.small)) { text() }
                AppDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screenGutter, vertical = UiSpacing.tiny),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(Modifier.weight(1f)) { confirmButton() }
                    dismissButton()
                }
            }
        }
    }
}
