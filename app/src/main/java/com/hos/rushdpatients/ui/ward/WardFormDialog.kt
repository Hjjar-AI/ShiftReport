package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Keeps form state in the caller while relocating the entire editor away from a hinge. */
@Composable
internal fun WardFormDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    properties: DialogProperties,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    val fold = rememberWardFoldingFeature()
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        WardAdaptivePanes(enableSplit = false, foldingFeature = fold,
            listFraction = .42f, onListFractionChange = {}, modifier = Modifier.fillMaxSize(), primaryTitle = "تحرير المريض",
            primary = {
                Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                    Surface(modifier = modifier, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            title()
                            Box(Modifier.weight(1f).fillMaxWidth()) { text() }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                confirmButton()
                                dismissButton()
                            }
                        }
                    }
                }
            })
    }
}
