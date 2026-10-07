package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    val fold = rememberWardFoldingFeature()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
        dismissOnClickOutside = false
    )) {
        Surface(Modifier.fillMaxSize()) {
            WardAdaptivePanes(
                enableSplit = false, foldingFeature = fold,
                listFraction = .42f, onListFractionChange = {},
                modifier = Modifier.fillMaxSize(), primaryTitle = "تحرير المريض",
                primary = {
                    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight()) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { title() }
                            Divider()
                            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { text() }
                            Divider()
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.weight(1f)) { confirmButton() }
                                dismissButton()
                            }
                        }
                    }
                }
            )
        }
    }
}
